// data/ChatMessage.kt
package com.example.plshelp.android.data

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "", // Document ID of the message
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now()
)