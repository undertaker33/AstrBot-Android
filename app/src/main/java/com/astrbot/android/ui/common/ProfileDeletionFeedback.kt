package com.astrbot.android.ui.common

import android.content.Context
import android.widget.Toast
import com.astrbot.android.R
import com.astrbot.android.data.LastProfileDeletionBlockedException
import com.astrbot.android.data.ProfileCatalogKind

internal fun showProfileDeletionFailureToast(
    context: Context,
    throwable: Throwable,
) {
    val messageResId = when ((throwable as? LastProfileDeletionBlockedException)?.kind) {
        ProfileCatalogKind.BOT -> R.string.bot_delete_last_blocked
        ProfileCatalogKind.PERSONA -> R.string.persona_delete_last_blocked
        null -> return
    }
    Toast.makeText(context, context.getString(messageResId), Toast.LENGTH_SHORT).show()
}
