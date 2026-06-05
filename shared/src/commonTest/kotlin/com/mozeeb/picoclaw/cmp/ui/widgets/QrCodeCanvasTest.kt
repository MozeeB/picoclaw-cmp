package com.mozeeb.picoclaw.cmp.ui.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [QrEncoder] — the in-house QR code encoder.
 * Verifies matrix dimensions, expected module count, and error cases.
 */
class QrCodeCanvasTest {

    // -------------------------------------------------------------------------
    // Matrix size tests — size = (version - 1) * 4 + 21
    // -------------------------------------------------------------------------

    @Test
    fun given_shortUrl_then_matrixIsVersion1Size() {
        // "hello" = 5 bytes → version 1 (max 14 bytes with ECC M), size = 21
        val matrix = QrEncoder.encode("hello")
        assertEquals(21, matrix.size, "Version 1 QR should be 21×21")
        matrix.forEach { row -> assertEquals(21, row.size, "Every row must have 21 modules") }
    }

    @Test
    fun given_typicalUrl_then_matrixIsAtLeastVersion2() {
        // "http://127.0.0.1:18800" = 22 bytes → version 2+ (max 14 for v1), size ≥ 25
        val matrix = QrEncoder.encode("http://127.0.0.1:18800")
        assertTrue(matrix.size >= 25, "URL of 22 bytes should produce at least version 2 (size 25+)")
    }

    @Test
    fun given_22ByteUrl_then_matrixIsVersion2() {
        // version 2 max for ECC M = 26 bytes, 22 bytes fits
        val data = "http://127.0.0.1:18800"  // exactly 22 bytes ASCII
        assertEquals(22, data.encodeToByteArray().size)
        val matrix = QrEncoder.encode(data)
        assertEquals(25, matrix.size, "22 bytes → version 2 → 25×25 matrix")
    }

    @Test
    fun given_42ByteData_then_matrixIsVersion3() {
        val data = "A".repeat(42) // 42 bytes → version 3 (max 42), size = 29
        val matrix = QrEncoder.encode(data)
        assertEquals(29, matrix.size, "42 bytes → version 3 → 29×29 matrix")
    }

    @Test
    fun given_62ByteData_then_matrixIsVersion4() {
        val data = "X".repeat(62) // version 4 max = 62 bytes, size = 33
        val matrix = QrEncoder.encode(data)
        assertEquals(33, matrix.size, "62 bytes → version 4 → 33×33 matrix")
    }

    // -------------------------------------------------------------------------
    // Matrix structure tests
    // -------------------------------------------------------------------------

    @Test
    fun given_anyInput_then_matrixIsSquare() {
        listOf("a", "hello world", "http://example.com/path").forEach { input ->
            val matrix = QrEncoder.encode(input)
            val size = matrix.size
            assertTrue(size > 0, "Matrix must be non-empty for input: $input")
            matrix.forEach { row ->
                assertEquals(size, row.size, "Matrix must be square for input: $input")
            }
        }
    }

    @Test
    fun given_anyInput_then_matrixSizeIsValid() {
        // Valid QR sizes: 21, 25, 29, 33, 37, 41, 45, 49, 53, 57 (versions 1–10)
        val validSizes = (1..10).map { it * 4 + 17 }.toSet()
        listOf("hi", "test data here", "http://192.168.1.1:8080/api/v1").forEach { input ->
            val matrix = QrEncoder.encode(input)
            assertTrue(matrix.size in validSizes, "Matrix size ${matrix.size} is not a valid QR size for: $input")
        }
    }

    @Test
    fun given_anyInput_then_finderPatternTopLeftIsDark() {
        // The top-left corner module (0,0) is always dark in a QR code
        val matrix = QrEncoder.encode("test")
        assertTrue(matrix[0][0], "Top-left module (0,0) must be dark (finder pattern)")
    }

    @Test
    fun given_anyInput_then_finderPatternTopLeftBorderIsExpected() {
        // The finder pattern outer ring: row 0 and row 6 from cols 0..6 are all dark
        val matrix = QrEncoder.encode("test")
        for (col in 0..6) {
            assertTrue(matrix[0][col], "Finder pattern top row (0, $col) must be dark")
            assertTrue(matrix[6][col], "Finder pattern bottom row (6, $col) must be dark")
        }
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    fun given_emptyString_then_encodesSuccessfully() {
        // Empty string should encode as version 1 (0 bytes)
        val matrix = QrEncoder.encode("")
        assertNotNull(matrix)
        assertTrue(matrix.size >= 21)
    }

    @Test
    fun given_dataTooLong_then_throwsIllegalArgumentException() {
        // 215 bytes > version 10 max (214 bytes with ECC M)
        val tooLong = "A".repeat(215)
        assertFailsWith<IllegalArgumentException> {
            QrEncoder.encode(tooLong)
        }
    }

    // -------------------------------------------------------------------------
    // Consistency test
    // -------------------------------------------------------------------------

    @Test
    fun given_sameInput_then_producesIdenticalMatrix() {
        val input = "http://127.0.0.1:18800"
        val matrix1 = QrEncoder.encode(input)
        val matrix2 = QrEncoder.encode(input)
        assertEquals(matrix1.size, matrix2.size)
        for (row in matrix1.indices) {
            for (col in matrix1[row].indices) {
                assertEquals(
                    matrix1[row][col],
                    matrix2[row][col],
                    "Matrix should be deterministic: mismatch at ($row, $col)"
                )
            }
        }
    }
}
