package com.example.dailyplanner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** 端末の標準音声（日本語）で読み上げる。通知も出す。 */
object Voice {
    private const val CHANNEL = "voice"

    /** 読み上げが終わるまで待つ（最大60秒）。終わったら TTS を片付ける。 */
    suspend fun speakAndWait(ctx: Context, text: String) {
        if (text.isBlank()) return
        val done = CompletableDeferred<Unit>()
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.JAPANESE
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { done.complete(Unit) }
                    override fun onError(utteranceId: String?) { done.complete(Unit) }
                })
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice")
            } else {
                done.complete(Unit)
            }
        }
        withTimeoutOrNull(60_000) { done.await() }
        tts.shutdown()
    }

    fun notify(ctx: Context, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "キャラの声", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("今日のお知らせ")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1001, n)
        } catch (e: SecurityException) {
            // 通知が許可されていない場合は、声だけ出す
        }
    }
}
