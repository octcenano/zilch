package com.zilch.ui.screens.contacts

data class TrustedContact(
    val nodeId: String,
    val fingerprint: String,
    val nickname: String,       // User-assigned name
    val isTrusted: Boolean,     // Trusted person flag
    val addedTimestampMs: Long
)
