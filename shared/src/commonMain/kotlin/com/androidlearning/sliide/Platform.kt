package com.androidlearning.sliide

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform