package com.astrbot.android.core.common.profile

enum class ProfileCatalogKind {
    BOT,
    PERSONA,
}

class LastProfileDeletionBlockedException(
    val kind: ProfileCatalogKind,
) : IllegalStateException(
        "At least one ${kind.name.lowercase()} profile must remain.",
    )

object ProfileDeletionGuard {
    fun canDelete(remainingCount: Int): Boolean = remainingCount > 1

    fun requireCanDelete(
        remainingCount: Int,
        kind: ProfileCatalogKind,
    ) {
        if (!canDelete(remainingCount)) {
            throw LastProfileDeletionBlockedException(kind)
        }
    }
}
