package com.mozeeb.picoclaw.cmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform