package com.example.dailyplanner

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 15分ごとに起きて、「今の時刻が曜日の声の時刻から20分以内か」を見る。
 * その日まだ話していなければ、声と通知を出す。
 */
class VoiceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        PlannerWidget().updateAll(ctx)

        val s = JSONObject(Store.load(ctx))
        val wd = Plan.weekday()
        val date = Plan.todayStr()
        val time = s.optJSONObject("voice")?.optJSONObject(wd.toString())?.optString("time").orEmpty()

        if (inWindow(time) && Store.lastVoice(ctx) != date) {
            Store.setLastVoice(ctx, date)
            val text = Plan.line(s, wd, date)
            Voice.notify(ctx, text)
            Voice.speakAndWait(ctx, text)
        }
        return Result.success()
    }

    private fun inWindow(time: String): Boolean {
        val p = time.split(":")
        if (p.size != 2) return false
        val h = p[0].toIntOrNull() ?: return false
        val m = p[1].toIntOrNull() ?: return false
        val start = h * 60 + m
        val now = LocalTime.now()
        val cur = now.hour * 60 + now.minute
        return cur >= start && cur < start + 20
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<VoiceWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("voice", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
