package com.reversetutor.feature.chat

import androidx.annotation.DrawableRes

sealed interface LearnerAvatarReference {
    val persistedValue: String

    data class PackagedDrawable(@DrawableRes val resourceId: Int) : LearnerAvatarReference {
        init {
            require(resourceId != 0) { "Drawable resource ID must be non-zero" }
        }

        override val persistedValue: String = "drawable:$resourceId"
    }

    data class ContentUri(val uri: String) : LearnerAvatarReference {
        init {
            require(uri.startsWith(ContentScheme)) { "Avatar URI must use the content scheme" }
        }

        override val persistedValue: String = uri
    }

    companion object {
        private const val DrawablePrefix = "drawable:"
        private const val ContentScheme = "content://"

        fun parse(value: String?): LearnerAvatarReference? {
            val reference = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            if (reference.startsWith(DrawablePrefix)) {
                val resourceId = reference.removePrefix(DrawablePrefix).toIntOrNull() ?: return null
                return resourceId.takeIf { it != 0 }?.let(::PackagedDrawable)
            }
            return reference.takeIf { it.startsWith(ContentScheme) }?.let(::ContentUri)
        }
    }
}
