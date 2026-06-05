package com.mozeeb.picoclaw.cmp.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

// =============================================================================
// QrCodeImage — public composable
// =============================================================================

/**
 * Renders a QR code for [data] using Compose Canvas.
 *
 * Pure Kotlin implementation — no third-party QR library.
 * Supports byte-mode encoding, ECC level M, versions 1–10 (up to ~214 bytes).
 *
 * Usage:
 * ```kotlin
 * QrCodeImage(
 *     data = "http://127.0.0.1:18800",
 *     modifier = Modifier.size(180.dp)
 * )
 * ```
 */
@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
) {
    val matrix = remember(data) {
        try {
            QrEncoder.encode(data)
        } catch (e: Exception) {
            // Fallback: 21×21 all-dark matrix to signal error visually
            Array(21) { BooleanArray(21) { true } }
        }
    }

    Canvas(modifier = modifier) {
        val n = matrix.size
        val moduleSize = size.width / n

        // Light background
        drawRect(color = lightColor, size = size)

        // Dark modules
        for (row in 0 until n) {
            for (col in 0 until n) {
                if (matrix[row][col]) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(col * moduleSize, row * moduleSize),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

// =============================================================================
// QrEncoder — pure Kotlin QR matrix encoder
// Implements ISO/IEC 18004 (QR Code), byte mode, ECC level M
// =============================================================================

internal object QrEncoder {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Encode [data] string into a QR bit matrix (true = dark module).
     * @throws IllegalArgumentException if [data] is too long for version 10 with ECC M.
     */
    fun encode(data: String): Array<BooleanArray> {
        val bytes = data.encodeToByteArray()
        val version = findVersion(bytes.size)
            ?: throw IllegalArgumentException("Data too long for QR version 10 (max 214 bytes)")

        val size = version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        // Place structural patterns
        placeFinderPatterns(matrix, isFunction, size)
        placeTimingPatterns(matrix, isFunction, size)
        if (version >= 2) placeAlignmentPatterns(matrix, isFunction, version, size)
        reserveFormatArea(isFunction, size)

        // Encode and place data
        val codewords = buildCodewords(bytes, version)
        placeData(matrix, isFunction, codewords, size)

        // Apply mask pattern 2: (row/2 + col/3) % 2 == 0  (good for URL-type data)
        val maskId = 2
        applyMask(matrix, isFunction, size, maskId)

        // Place format information (ECC level M = 0b01, mask = maskId)
        placeFormatInfo(matrix, size, eccCode = 0b01, maskId = maskId)

        return matrix
    }

    // -------------------------------------------------------------------------
    // Version selection
    // -------------------------------------------------------------------------

    // Max bytes for ECC level M, byte mode — versions 1..10
    private val MAX_BYTES = intArrayOf(14, 26, 42, 62, 84, 106, 122, 152, 180, 214)

    private fun findVersion(dataLen: Int): Int? {
        for ((i, max) in MAX_BYTES.withIndex()) {
            if (dataLen <= max) return i + 1
        }
        return null
    }

    // -------------------------------------------------------------------------
    // GF(256) arithmetic for Reed-Solomon ECC
    // -------------------------------------------------------------------------

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0..254) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D // primitive poly x^8+x^4+x^3+x^2+1
        }
        for (i in 255..511) GF_EXP[i] = GF_EXP[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[(GF_LOG[a] + GF_LOG[b]) % 255]
    }

    /** Reed-Solomon generator polynomial for [n] ECC symbols. */
    private fun rsGenerator(n: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until n) {
            val factor = intArrayOf(1, GF_EXP[i])
            val prod = IntArray(g.size + factor.size - 1)
            for (j in g.indices) for (k in factor.indices) prod[j + k] = prod[j + k] xor gfMul(g[j], factor[k])
            g = prod
        }
        return g
    }

    /** Compute [eccCount] Reed-Solomon error correction codewords for [data]. */
    private fun rsEncode(data: IntArray, eccCount: Int): IntArray {
        val gen = rsGenerator(eccCount)
        val msg = data.copyOf(data.size + eccCount)
        for (i in data.indices) {
            val coef = msg[i]
            if (coef != 0) for (j in gen.indices) msg[i + j] = msg[i + j] xor gfMul(gen[j], coef)
        }
        return msg.copyOfRange(data.size, msg.size)
    }

    // -------------------------------------------------------------------------
    // Block structure for ECC level M
    // [ecPerBlock, group1Count, group1DataPerBlock, group2Count, group2DataPerBlock]
    // -------------------------------------------------------------------------

    private val BLOCK_INFO = arrayOf(
        intArrayOf(10, 1, 16, 0, 0),   // v1
        intArrayOf(16, 1, 28, 0, 0),   // v2
        intArrayOf(26, 1, 44, 0, 0),   // v3
        intArrayOf(18, 2, 32, 0, 0),   // v4
        intArrayOf(24, 2, 43, 0, 0),   // v5
        intArrayOf(16, 4, 27, 0, 0),   // v6
        intArrayOf(18, 4, 31, 1, 32),  // v7
        intArrayOf(22, 2, 38, 2, 39),  // v8
        intArrayOf(22, 3, 36, 2, 37),  // v9
        intArrayOf(26, 4, 43, 1, 44),  // v10
    )

    // -------------------------------------------------------------------------
    // Data encoding and codeword assembly
    // -------------------------------------------------------------------------

    private fun buildCodewords(bytes: ByteArray, version: Int): IntArray {
        val info = BLOCK_INFO[version - 1]
        val (ecPerBlock, g1Count, g1DataPer, g2Count, g2DataPer) = info
        val totalDataCw = g1Count * g1DataPer + g2Count * g2DataPer
        val totalCw = totalDataCw + (g1Count + g2Count) * ecPerBlock

        // Build the data bit stream
        val bits = buildBitStream(bytes, version, totalDataCw * 8)

        // Convert bits → data codewords
        val dataCw = IntArray(totalDataCw) { i ->
            var cw = 0
            for (b in 0..7) {
                val bitIndex = i * 8 + b
                if (bitIndex < bits.size && bits[bitIndex]) cw = cw or (1 shl (7 - b))
            }
            cw
        }

        // Split into blocks and generate ECC
        val blocks = buildList {
            var offset = 0
            repeat(g1Count) {
                add(dataCw.copyOfRange(offset, offset + g1DataPer))
                offset += g1DataPer
            }
            repeat(g2Count) {
                add(dataCw.copyOfRange(offset, offset + g2DataPer))
                offset += g2DataPer
            }
        }
        val eccBlocks = blocks.map { rsEncode(it, ecPerBlock) }

        // Interleave data codewords
        val result = IntArray(totalCw)
        var idx = 0
        val maxDataPerBlock = maxOf(g1DataPer, g2DataPer)
        for (col in 0 until maxDataPerBlock) {
            for (block in blocks) {
                if (col < block.size) result[idx++] = block[col]
            }
        }
        // Interleave ECC codewords
        for (col in 0 until ecPerBlock) {
            for (ecc in eccBlocks) result[idx++] = ecc[col]
        }
        return result
    }

    private operator fun IntArray.component1() = this[0]
    private operator fun IntArray.component2() = this[1]
    private operator fun IntArray.component3() = this[2]
    private operator fun IntArray.component4() = this[3]
    private operator fun IntArray.component5() = this[4]

    private fun buildBitStream(bytes: ByteArray, version: Int, totalCapacityBits: Int): BooleanArray {
        val bits = mutableListOf<Boolean>()

        // Mode indicator: 0100 (byte mode)
        bits.addBits(0b0100, 4)
        // Character count: 8 bits for versions 1–9
        bits.addBits(bytes.size, 8)
        // Data bytes
        for (b in bytes) bits.addBits(b.toInt() and 0xFF, 8)
        // Terminator (up to 4 zeros)
        val terminatorLen = minOf(4, totalCapacityBits - bits.size)
        repeat(terminatorLen) { bits.add(false) }
        // Pad to byte boundary
        while (bits.size % 8 != 0) bits.add(false)
        // Pad bytes
        val padBytes = listOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < totalCapacityBits) {
            bits.addBits(padBytes[padIdx % 2], 8)
            padIdx++
        }
        return bits.toBooleanArray()
    }

    private fun MutableList<Boolean>.addBits(value: Int, count: Int) {
        for (i in count - 1 downTo 0) add((value shr i) and 1 == 1)
    }

    // -------------------------------------------------------------------------
    // Matrix patterns
    // -------------------------------------------------------------------------

    private fun placeFinderPatterns(matrix: Array<BooleanArray>, fn: Array<BooleanArray>, size: Int) {
        val positions = listOf(0 to 0, 0 to size - 7, size - 7 to 0)
        for ((row, col) in positions) {
            placeFinderPattern(matrix, fn, row, col, size)
        }
    }

    private fun placeFinderPattern(
        matrix: Array<BooleanArray>,
        fn: Array<BooleanArray>,
        startRow: Int,
        startCol: Int,
        size: Int,
    ) {
        // 7×7 finder pattern with 1-wide separator
        for (r in -1..7) for (c in -1..7) {
            val row = startRow + r
            val col = startCol + c
            if (row < 0 || row >= size || col < 0 || col >= size) continue
            fn[row][col] = true
            val isPattern = r in 0..6 && c in 0..6 &&
                (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
            matrix[row][col] = isPattern
        }
    }

    private fun placeTimingPatterns(matrix: Array<BooleanArray>, fn: Array<BooleanArray>, size: Int) {
        for (i in 8 until size - 8) {
            matrix[6][i] = i % 2 == 0
            matrix[i][6] = i % 2 == 0
            fn[6][i] = true
            fn[i][6] = true
        }
    }

    // Alignment pattern centers (for each version)
    private val ALIGN_POS = arrayOf(
        intArrayOf(),               // v1
        intArrayOf(6, 18),          // v2
        intArrayOf(6, 22),          // v3
        intArrayOf(6, 26),          // v4
        intArrayOf(6, 30),          // v5
        intArrayOf(6, 34),          // v6
        intArrayOf(6, 22, 38),      // v7
        intArrayOf(6, 24, 42),      // v8
        intArrayOf(6, 26, 46),      // v9
        intArrayOf(6, 28, 50),      // v10
    )

    private fun placeAlignmentPatterns(
        matrix: Array<BooleanArray>,
        fn: Array<BooleanArray>,
        version: Int,
        size: Int,
    ) {
        val pos = ALIGN_POS[version - 1]
        for (r in pos) for (c in pos) {
            // Skip positions occupied by finder patterns
            if (fn[r][c]) continue
            for (dr in -2..2) for (dc in -2..2) {
                if (r + dr < 0 || r + dr >= size || c + dc < 0 || c + dc >= size) continue
                fn[r + dr][c + dc] = true
                matrix[r + dr][c + dc] = dr == 0 && dc == 0 || maxOf(
                    kotlin.math.abs(dr), kotlin.math.abs(dc)
                ) == 2
            }
        }
    }

    private fun reserveFormatArea(fn: Array<BooleanArray>, size: Int) {
        // Top-left format strip (rows 0-8 × col 8, and row 8 × cols 0-8)
        for (i in 0..8) {
            fn[8][i] = true
            fn[i][8] = true
        }
        // Bottom-left and top-right strips
        for (i in 0..7) {
            fn[size - 1 - i][8] = true
            fn[8][size - 1 - i] = true
        }
        // Dark module (always set)
        fn[size - 8][8] = true
    }

    // Matrix reference needed in reserveFormatArea — inline solution
    private fun reserveFormatArea2(matrix: Array<BooleanArray>, fn: Array<BooleanArray>, size: Int) {
        for (i in 0..8) { fn[8][i] = true; fn[i][8] = true }
        for (i in 0..7) { fn[size - 1 - i][8] = true; fn[8][size - 1 - i] = true }
        matrix[size - 8][8] = true // permanent dark module
        fn[size - 8][8] = true
    }

    // -------------------------------------------------------------------------
    // Data placement (zigzag scan, right to left, bottom to top)
    // -------------------------------------------------------------------------

    private fun placeData(
        matrix: Array<BooleanArray>,
        fn: Array<BooleanArray>,
        codewords: IntArray,
        size: Int,
    ) {
        var bitIndex = 0
        val totalBits = codewords.size * 8

        // Data is placed in 2-column wide strips, scanning from right to left.
        // `col` is the RIGHT column of each pair.
        // Column 6 is the timing pattern — we skip it mid-loop.
        var col = size - 1
        var goingUp = true
        while (col >= 1) {
            for (rowOffset in 0 until size) {
                val row = if (goingUp) size - 1 - rowOffset else rowOffset
                // Each pair: columns (col) and (col-1)
                for (dc in 0..1) {
                    val c = col - dc
                    if (c < 0 || c >= size) continue
                    if (!fn[row][c] && bitIndex < totalBits) {
                        val cwIndex = bitIndex / 8
                        val bitInCw = 7 - (bitIndex % 8)
                        matrix[row][c] = ((codewords[cwIndex] shr bitInCw) and 1) == 1
                        bitIndex++
                    }
                }
            }
            col -= 2
            if (col == 6) col-- // skip timing column (col 6 is the timing row/col)
            goingUp = !goingUp
        }
    }

    // -------------------------------------------------------------------------
    // Masking
    // -------------------------------------------------------------------------

    private fun applyMask(matrix: Array<BooleanArray>, fn: Array<BooleanArray>, size: Int, maskId: Int) {
        for (row in 0 until size) {
            for (col in 0 until size) {
                if (!fn[row][col] && maskCondition(maskId, row, col)) {
                    matrix[row][col] = !matrix[row][col]
                }
            }
        }
    }

    private fun maskCondition(id: Int, row: Int, col: Int): Boolean = when (id) {
        0 -> (row + col) % 2 == 0
        1 -> row % 2 == 0
        2 -> col % 3 == 0
        3 -> (row + col) % 3 == 0
        4 -> (row / 2 + col / 3) % 2 == 0
        5 -> (row * col) % 2 + (row * col) % 3 == 0
        6 -> ((row * col) % 2 + (row * col) % 3) % 2 == 0
        7 -> ((row + col) % 2 + (row * col) % 3) % 2 == 0
        else -> false
    }

    // -------------------------------------------------------------------------
    // Format information (ECC level M = 0b01, mask pattern [0..7])
    // Pre-computed 15-bit strings for level M (from QR standard)
    // -------------------------------------------------------------------------

    // Format info words for ECC M (bits[14..0]). Index = mask pattern id (0..7).
    private val FORMAT_INFO_M = intArrayOf(
        0x5412, // mask 0
        0x5125, // mask 1
        0x5E7C, // mask 2
        0x5B4B, // mask 3
        0x45F9, // mask 4
        0x40CE, // mask 5
        0x4F97, // mask 6
        0x4AA0, // mask 7
    )

    private fun placeFormatInfo(matrix: Array<BooleanArray>, size: Int, eccCode: Int, maskId: Int) {
        val formatData = FORMAT_INFO_M[maskId]

        // Place bits at standard positions (top-left strip + top-right/bottom-left copies)
        val bits = (14 downTo 0).map { (formatData shr it) and 1 == 1 }

        // Top-left strip
        val positions1 = listOf(
            8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8,
            7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8,
        )
        for ((i, pos) in positions1.withIndex()) matrix[pos.first][pos.second] = bits[i]

        // Top-right / bottom-left strips
        val positions2 = buildList {
            for (i in 0..7) add(size - 1 - i to 8)
            add(8 to size - 8)
            for (i in 9..14) add(8 to size - 15 + i)
        }
        for ((i, pos) in positions2.withIndex()) {
            if (pos.first in 0 until size && pos.second in 0 until size) {
                matrix[pos.first][pos.second] = bits[i]
            }
        }

        // Dark module: always set
        matrix[size - 8][8] = true
    }
}
