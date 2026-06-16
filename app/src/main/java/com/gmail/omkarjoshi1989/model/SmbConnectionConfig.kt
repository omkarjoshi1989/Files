package com.gmail.omkarjoshi1989.model

enum class SmbAuthMode {
    GUEST,
    USERNAME_PASSWORD
}

data class SmbConnectionConfig(
    val id: String,
    val displayName: String,
    val host: String,
    val port: Int = 445,
    val defaultShareName: String = "",
    val authMode: SmbAuthMode = SmbAuthMode.GUEST,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val lastConnectedAt: Long = 0L
)

data class LanHostCandidate(
    val ipAddress: String,
    val hostName: String
)

data class SmbRemoteItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

