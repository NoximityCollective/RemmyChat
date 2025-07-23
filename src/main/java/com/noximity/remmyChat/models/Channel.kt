package com.noximity.remmyChat.models

class Channel(
    val name: String?,
    val permission: String?,
    val radius: Double,
    val prefix: String?,
    val hover: String?,
    val displayName: String?
) {
    fun hasDisplayName(): Boolean {
        return displayName != null && !displayName.isEmpty()
    }


}
