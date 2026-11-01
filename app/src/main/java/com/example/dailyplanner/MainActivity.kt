package com.example.dailyplanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** 本体画面。assets/index.html を WebView で表示し、データ・キャラ・声を Kotlin 側でやり取りする。 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            // 設定画面の「ファイルを選ぶ」（キャラのHTML選び）で使う
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = params?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
                startActivityForResult(intent, FILE_REQUEST)
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        VoiceWorker.schedule(this)

        // 初回だけ、キャラの絵（ウィジェット用）を作る
        if (!Chara.snapshotFile(this).exists()) {
            Chara.snapshot(this) { refreshWidgetsAsync() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST) {
            filePathCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    private fun refreshWidgetsAsync() {
        CoroutineScope(Dispatchers.IO).launch { refreshWidgets(applicationContext) }
    }

    private fun rebuildCharaAndWidgets() {
        runOnUiThread {
            Chara.snapshot(this@MainActivity) { refreshWidgetsAsync() }
        }
    }

    /** JavaScript（index.html）から呼べる窓口 */
    inner class Bridge {
        @JavascriptInterface
        fun getState(): String = Store.load(this@MainActivity)

        @JavascriptInterface
        fun setState(json: String) {
            Store.save(this@MainActivity, json)
            refreshWidgetsAsync()
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

        @JavascriptInterface
        fun getCharacter(): String = Chara.readHtml(this@MainActivity)

        @JavascriptInterface
        fun saveCharacter(html: String) {
            Chara.writeHtml(this@MainActivity, html)
            rebuildCharaAndWidgets()
        }

        @JavascriptInterface
        fun resetCharacter() {
            Chara.resetHtml(this@MainActivity)
            rebuildCharaAndWidgets()
        }
    }

    companion object {
        private const val FILE_REQUEST = 1001
    }
}
