package com.example.dailyplanner

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * キャラクター（HTML）の読み書きと、ウィジェット用の画像作成。
 * - 選んだHTMLは filesDir/character.html に保存（無ければ assets/chara/default.html）
 * - 画面を1枚の絵にした PNG を filesDir/chara.png に保存し、ウィジェットはそれを表示する
 */
object Chara {
    private const val SIZE = 400
    private const val WAIT_MS = 3000L

    private fun htmlFile(c: Context) = File(c.filesDir, "character.html")

    fun snapshotFile(c: Context) = File(c.filesDir, "chara.png")

    fun readHtml(c: Context): String {
        val f = htmlFile(c)
        return if (f.exists()) f.readText()
        else c.assets.open("chara/default.html").bufferedReader().use { it.readText() }
    }

    fun writeHtml(c: Context, html: String) {
        htmlFile(c).writeText(html)
    }

    fun resetHtml(c: Context) {
        htmlFile(c).delete()
    }

    /**
     * キャラを WebView で描いて PNG に保存する。UIスレッドで呼ぶこと。
     * 完了したら onDone を呼ぶ（ウィジェットの描き直しに使う）。
     */
    fun snapshot(activity: Activity, onDone: () -> Unit) {
        val wv = WebView(activity)
        wv.setBackgroundColor(Color.TRANSPARENT)
        wv.settings.javaScriptEnabled = true
        wv.layerType = View.LAYER_TYPE_SOFTWARE
        wv.layout(0, 0, SIZE, SIZE)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.postDelayed({
                    val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                    wv.draw(Canvas(bmp))
                    FileOutputStream(snapshotFile(activity)).use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bmp.recycle()
                    wv.destroy()
                    onDone()
                }, WAIT_MS)
            }
        }
        wv.loadDataWithBaseURL(
            "file:///android_asset/", readHtml(activity), "text/html", "utf-8", null
        )
    }
}
