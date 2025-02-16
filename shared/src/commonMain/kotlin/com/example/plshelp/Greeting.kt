package com.example.plshelp

class Greeting {
    private val platform: Platform = getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!\n This is Start Page"
    }
}