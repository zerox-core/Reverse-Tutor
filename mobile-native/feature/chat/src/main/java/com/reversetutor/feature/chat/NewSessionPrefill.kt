package com.reversetutor.feature.chat

data class NewSessionPrefillRequest(
    val requestId: String,
    val configuration: NewSessionConfiguration
)
