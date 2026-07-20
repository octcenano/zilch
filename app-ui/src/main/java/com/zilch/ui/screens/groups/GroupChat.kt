package com.zilch.ui.screens.groups

/**
 * GroupInfo — Información de un grupo de chat.
 */
data class GroupInfo(
    val groupId: String,
    val name: String,
    val members: List<String>,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val lastMessageTimeMs: Long = 0L
)

/**
 * GroupMessage — Mensaje dentro de un grupo.
 */
data class GroupMessage(
    val id: String,
    val groupId: String,
    val senderNodeId: String,
    val senderName: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isFromLocal: Boolean = false
)
