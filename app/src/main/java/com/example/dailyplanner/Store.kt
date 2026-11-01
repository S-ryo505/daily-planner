package com.example.dailyplanner

import android.content.Context
import android.content.SharedPreferences

/**
 * アプリの全データを1つのJSON文字列として端末内に保存する。
 * 画面（WebView）もウィジェットも、ここを読み書きする。
 */
object Store {
    private const val PREFS = "planner"
    private const val KEY = "state"
    private const val LAST_VOICE = "lastVoice"

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): String = prefs(c).getString(KEY, null) ?: DEFAULT

    fun save(c: Context, json: String) {
        prefs(c).edit().putString(KEY, json).apply()
    }

    fun lastVoice(c: Context): String = prefs(c).getString(LAST_VOICE, "") ?: ""

    fun setLastVoice(c: Context, date: String) {
        prefs(c).edit().putString(LAST_VOICE, date).apply()
    }

    val DEFAULT = """
        {
          "challenge": {
            "name": "チャレンジ",
            "unitsPerMonth": 6,
            "subjects": ["国語", "算数", "理科", "社会"],
            "months": {}
          },
          "items": [
            {"id": "piano", "name": "ピアノ", "target": 5, "comment": "", "days": [1,2,3,4,5,6,7], "from": "", "to": ""},
            {"id": "hw", "name": "宿題", "target": 1, "comment": "", "days": [1,2,3,4,5,6,7], "from": "", "to": ""}
          ],
          "tests": [],
          "voice": {
            "1": {"time": "16:00", "msg": ""},
            "2": {"time": "16:00", "msg": ""},
            "3": {"time": "16:00", "msg": ""},
            "4": {"time": "16:00", "msg": ""},
            "5": {"time": "16:00", "msg": ""},
            "6": {"time": "10:00", "msg": ""},
            "7": {"time": "10:00", "msg": ""}
          },
          "log": {}
        }
    """.trimIndent()
}
