package com.dearby.nativeapp.features.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dearby.nativeapp.R
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 새 모집 푸시 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25) through Firebase Cloud Messaging. Firebase is configured only when the build
 * had `app/google-services.json`; without it [available] is false and nothing here touches Firebase (its `getInstance` would throw).
 * Nothing is stored here: the app sends the token whenever it is signed in and something changed ([refreshed], time zone, permission).
 */
object Push {
    const val CHANNEL = "new_recruitment"
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** A new FCM token while the app runs (a token that changes with the app closed is sent on the next start). */
    val refreshed = changes.asSharedFlow()
    internal fun tokenChanged() { changes.tryEmit(Unit) }

    fun available(context: Context) = FirebaseApp.getApps(context).isNotEmpty()

    /** The first program heart (2026-10-25 user decision): only from now may FCM make and refresh a token (auto-init is off in the manifest). */
    fun enable(context: Context) { if (available(context)) FirebaseMessaging.getInstance().isAutoInitEnabled = true }

    /** This install's FCM token, or null when Firebase is not configured or the token cannot be had right now. The token is never logged. */
    suspend fun token(context: Context): String? {
        if (!available(context)) return null
        return suspendCancellableCoroutine { done ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task -> done.resume(if (task.isSuccessful) task.result else null) }
        }
    }

    /** The `새 모집` channel; idempotent, so it is created at every start. */
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(NotificationChannel(CHANNEL, "새 모집", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Android 13+ asks for POST_NOTIFICATIONS; earlier versions only have the app-level switch. */
    fun needsPermission(context: Context) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    fun enabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()
    /** Requested only on Android 13+ ([needsPermission] is false below it). */
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"
}

/**
 * FCM's entry point. In the background the system draws `notification` messages itself on [Push.CHANNEL] (manifest default) and a tap starts the app
 * with the `data` keys (`url`, `activityId`) as extras; in the foreground this posts the same notification, whose tap opens `url` in this app.
 */
class DearbyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Push.tokenChanged()

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val open = message.data["url"]?.let { Intent(Intent.ACTION_VIEW, it.toUri()).setPackage(packageName) }
            ?: packageManager.getLaunchIntentForPackage(packageName) ?: return
        val tap = PendingIntent.getActivity(this, message.messageId.hashCode(), open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val shown = NotificationCompat.Builder(this, Push.CHANNEL).setSmallIcon(R.drawable.dearby_symbol).setContentTitle(title).setContentText(message.notification?.body)
            .setAutoCancel(true).setContentIntent(tap).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), shown)
    }
}
