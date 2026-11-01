package com.example.dailyplanner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 本体画面。assets/index.html を WebView で表示し、データと声を Kotlin 側でやり取りする。 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        VoiceWorker.schedule(this)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    /** JavaScript（index.html）から呼べる窓口 */
    inner class Bridge {
        @JavascriptInterface
        fun getState(): String = Store.load(this@MainActivity)

        @JavascriptInterface
        fun setState(json: String) {
            Store.save(this@MainActivity, json)
            CoroutineScope(Dispatchers.IO).launch {
                refreshWidgets(this@MainActivity)
            }
        }

        @JavascriptInterface
        fun todayLine(): String =
            Plan.line(JSONObject(Store.load(this@MainActivity)), Plan.weekday(), Plan.todayStr())

        @JavascriptInterface
        fun speak(text: String) {
            CoroutineScope(Dispatchers.Main).launch {
                Voice.speakAndWait(this@MainActivity, text)
            }
        }
    }
}
