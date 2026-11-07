package dev.nekuz.gamemath

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

object Actions {
    private const val CLIP_LABEL = "GameMath"

    fun copy(context: Context, value: String) {
        val done = try {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, value))
            true
        } catch (e: RuntimeException) {
            false
        }
        toast(context, if (done) R.string.copied else R.string.copy_failed)
    }

    fun shareText(activity: Activity, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
        try {
            activity.startActivity(Intent.createChooser(send, null))
        } catch (e: RuntimeException) {
            toast(activity, R.string.share_failed)
        }
    }

    fun toast(context: Context, text: Int) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
