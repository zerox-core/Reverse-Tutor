package com.reversetutor.core.protocol

object ProtocolFixtureLoader {
    fun read(path: String): String {
        val resourcePath = "protocol-fixtures/$path"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
            "Missing protocol fixture: $resourcePath"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
