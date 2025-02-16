package com.example.plshelp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform