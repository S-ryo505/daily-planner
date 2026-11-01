package com.example.dailyplanner

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 画面に出す1項目分。done は今日の実施回数。 */
data class Row(
    val id: String,
    val name: String,
    val target: Int,
    val done: Int,
    val comment: String
)

/**
 * 計算ロジック。ウィジェットと定時の声の両方で使う。
 * 曜日は 1=月 … 7=日（java.time の DayOfWeek.getValue() と同じ）。
 */
object Plan {

    fun todayStr(): String = LocalDate.now().toString()

    fun weekday(): Int = LocalDate.now().dayOfWeek.value

    private fun hasDay(arr: JSONArray?, wd: Int): Boolean {
        if (arr == null) return false
        for (i in 0 until arr.length()) {
            if (arr.getInt(i) == wd) return true
        }
        return false
    }

    /** 今日やる項目の一覧（曜日・期間で絞り込み） */
    fun activeItems(s: JSONObject, date: String, wd: Int): List<Row> {
        val arr = s.optJSONArray("items") ?: return emptyList()
        val log = s.optJSONObject("log")?.optJSONObject(date)
        val out = mutableListOf<Row>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!hasDay(o.optJSONArray("days"), wd)) continue
            val from = o.optString("from")
            val to = o.optString("to")
            if (from.isNotEmpty() && date < from) continue
            if (to.isNotEmpty() && date > to) continue
            val id = o.getString("id")
            out += Row(
                id = id,
                name = o.getString("name"),
                target = o.optInt("target", 1),
                done = log?.optInt(id, 0) ?: 0,
                comment = o.optString("comment")
            )
        }
        return out
    }

    /** タップで 0 → 1 → … → target → 0 と回る */
    fun toggle(stateStr: String, id: String, date: String): String {
        val s = JSONObject(stateStr)
        var target = 1
        val arr = s.optJSONArray("items")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) target = o.optInt("target", 1)
            }
        }
        if (s.optJSONObject("log") == null) s.put("log", JSONObject())
        val logs = s.getJSONObject("log")
        if (logs.optJSONObject(date) == null) logs.put(date, JSONObject())
        val day = logs.getJSONObject(date)
        val cur = day.optInt(id, 0)
        day.put(id, if (cur >= target) 0 else cur + 1)
        return s.toString()
    }

    private fun challengeName(s: JSONObject): String =
        s.optJSONObject("challenge")?.optString("name", "チャレンジ") ?: "チャレンジ"

    /** 教科ごとの残り単元。ym は "2026-11" 形式 */
    fun challengeLeft(s: JSONObject, ym: String): List<Pair<String, Int>> {
        val c = s.optJSONObject("challenge") ?: return emptyList()
        val units = c.optInt("unitsPerMonth", 6)
        val subs = c.optJSONArray("subjects") ?: return emptyList()
        val done = c.optJSONObject("months")?.optJSONObject(ym)
        return (0 until subs.length()).map { i ->
            val name = subs.getString(i)
            name to maxOf(0, units - (done?.optInt(name, 0) ?: 0))
        }
    }

    /** 今日以降で一番近いテスト（名前, 何日後） */
    fun nextTest(s: JSONObject, date: String): Pair<String, Long>? {
        val arr = s.optJSONArray("tests") ?: return null
        val today = LocalDate.parse(date)
        var best: Pair<String, Long>? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val d = o.optString("date")
            if (d.isEmpty()) continue
            val days = ChronoUnit.DAYS.between(today, LocalDate.parse(d))
            if (days < 0) continue
            val b = best
            if (b == null || days < b.second) best = o.optString("name") to days
        }
        return best
    }

    /** ウィジェット用の短い文 */
    fun challengeShort(s: JSONObject, date: String): String {
        val lefts = challengeLeft(s, date.substring(0, 7))
        if (lefts.isEmpty()) return "チャレンジ未設定"
        val total = lefts.sumOf { it.second }
        val mm = date.substring(5, 7).toInt()
        return "${challengeName(s)} ${mm}月号 残り${total}単元"
    }

    fun testShort(s: JSONObject, date: String): String {
        val t = nextTest(s, date) ?: return "テストの予定なし"
        return if (t.second == 0L) "今日はテスト「${t.first}」！"
        else "テスト「${t.first}」まであと${t.second}日"
    }

    private fun sent(x: String): String =
        if (x.last() in "。！？!?") x else "$x。"

    /** キャラが話す一言。曜日の一言 → 今日のこり → チャレンジ → テスト の順 */
    fun line(s: JSONObject, wd: Int, date: String): String {
        val parts = mutableListOf<String>()

        val msg = s.optJSONObject("voice")?.optJSONObject(wd.toString())?.optString("msg").orEmpty()
        if (msg.isNotBlank()) parts += sent(msg)

        val items = activeItems(s, date, wd)
        if (items.isNotEmpty()) {
            val todo = items.filter { it.done < it.target }
            if (todo.isEmpty()) {
                parts += "今日の分は全部できたよ！すごい！"
            } else {
                val detail = todo.joinToString("、") { r ->
                    val rest = "${r.name}あと${r.target - r.done}回"
                    if (r.comment.isNotBlank()) "$rest（${r.comment}）" else rest
                }
                parts += sent("今日のこりは、$detail")
            }
        }

        val lefts = challengeLeft(s, date.substring(0, 7))
        if (lefts.isNotEmpty()) {
            val total = lefts.sumOf { it.second }
            val mm = date.substring(5, 7).toInt()
            if (total == 0) {
                parts += "${challengeName(s)}${mm}月号は全部終わってるよ！"
            } else {
                val today = LocalDate.parse(date)
                val daysLeft = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth
                val detail = lefts.filter { it.second > 0 }
                    .joinToString("、") { "${it.first}あと${it.second}" }
                parts += "${challengeName(s)}${mm}月号、残り${total}単元。" +
                    sent(detail) + sent("月末まであと${daysLeft}日")
            }
        }

        nextTest(s, date)?.let { (name, d) ->
            parts += if (d == 0L) "今日はテスト「$name」の日だよ！がんばってね！"
            else sent("テスト「$name」まで、あと${d}日")
        }

        return if (parts.isEmpty()) "今日もがんばろうね！" else parts.joinToString("")
    }
}
