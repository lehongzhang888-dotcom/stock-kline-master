package com.cryptopulse.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(false)
                builtInZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            // 🔑 JS Bridge: 原生层代理所有 API 请求
            addJavascriptInterface(ApiBridge(), "NativeAPI")

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    /** 原生层 API 代理桥 —— 绕过 WebView CORS 限制 */
    inner class ApiBridge {
        @JavascriptInterface
        fun fetchKlines(symbol: String, interval: String, limit: Int) {
            Thread {
                try {
                    // 多数据源 fallback
                    var result = tryBinance(symbol, interval, limit)
                    if (result == null) result = tryBinance2(symbol, interval, limit)
                    if (result == null) result = tryOKX(symbol, interval, limit)

                    val jsonResult = result ?: "[]"
                    handler.post {
                        val wv = findViewById<WebView>(android.R.id.content)
                        wv?.evaluateJavascript("onKlinesData($jsonResult)", null)
                    }
                } catch (e: Exception) {
                    Log.e("CryptoPulse", "fetchKlines error", e)
                    handler.post {
                        val wv = findViewById<WebView>(android.R.id.content)
                        wv?.evaluateJavascript("onKlinesData([])", null)
                    }
                }
            }.start()
        }

        @JavascriptInterface
        fun fetchTicker(symbol: String) {
            Thread {
                try {
                    var result = tryBinanceTicker(symbol)
                    if (result == null) result = tryOKXTicker(symbol)
                    val jsonResult = result ?: "{}"
                    handler.post {
                        val wv = findViewById<WebView>(android.R.id.content)
                        wv?.evaluateJavascript("onTickerData($jsonResult)", null)
                    }
                } catch (e: Exception) {
                    Log.e("CryptoPulse", "fetchTicker error", e)
                }
            }.start()
        }

        @JavascriptInterface
        fun aiChat(model: String, apiKey: String, prompt: String) {
            Thread {
                try {
                    val json = """{"model":"$model","messages":[{"role":"user","content":${gson.toJson(prompt)}}],"max_tokens":800}"""
                    val body = json.toRequestBody("application/json".toMediaType())
                    val req = Request.Builder()
                        .url("https://openrouter.ai/api/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()
                    val resp = client.newCall(req).execute()
                    val text = resp.body?.string() ?: "{}"
                    handler.post {
                        val wv = findViewById<WebView>(android.R.id.content)
                        val escaped = gson.toJson(text)
                        wv?.evaluateJavascript("onAIData($escaped)", null)
                    }
                } catch (e: Exception) {
                    handler.post {
                        val wv = findViewById<WebView>(android.R.id.content)
                        wv?.evaluateJavascript("onAIData('{\"error\":\"${e.message}\"}')", null)
                    }
                }
            }.start()
        }

        // ---- Binance API ----
        private fun tryBinance(symbol: String, interval: String, limit: Int): String? {
            return try {
                val url = "https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) resp.body?.string() else null
            } catch (e: Exception) { null }
        }

        private fun tryBinance2(symbol: String, interval: String, limit: Int): String? {
            return try {
                val url = "https://api1.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) resp.body?.string() else null
            } catch (e: Exception) { null }
        }

        private fun tryOKX(symbol: String, interval: String, limit: Int): String? {
            return try {
                val instId = symbol.replace("USDT", "-USDT")
                val bar = interval.replace("m", "").replace("h", "H").replace("d", "D")
                val url = "https://www.okx.com/api/v5/market/candles?instId=$instId&bar=$bar&limit=$limit"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val raw = resp.body?.string() ?: return null
                    // 转换 OKX 格式为 Binance 格式
                    val okxData: List<List<String>> = try {
                        val map = gson.fromJson(raw, Map::class.java)
                        val data = map["data"] as? List<List<String>> ?: return null
                        data.reversed() // OKX 是倒序
                    } catch (e: Exception) { return null }
                    okxData.map { k ->
                        listOf(k[0], k[1], k[2], k[3], k[4], k[5], "0", k[5], "0", "0", "0", "0")
                    }.let { gson.toJson(it) }
                } else null
            } catch (e: Exception) { null }
        }

        private fun tryBinanceTicker(symbol: String): String? {
            return try {
                val url = "https://api.binance.com/api/v3/ticker/24hr?symbol=$symbol"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) resp.body?.string() else null
            } catch (e: Exception) {
                tryBinance2Ticker(symbol)
            }
        }

        private fun tryBinance2Ticker(symbol: String): String? {
            return try {
                val url = "https://api1.binance.com/api/v3/ticker/24hr?symbol=$symbol"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) resp.body?.string() else null
            } catch (e: Exception) { null }
        }

        private fun tryOKXTicker(symbol: String): String? {
            return try {
                val instId = symbol.replace("USDT", "-USDT")
                val url = "https://www.okx.com/api/v5/market/ticker?instId=$instId"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val raw = resp.body?.string() ?: return null
                    val map = gson.fromJson(raw, Map::class.java)
                    val data = (map["data"] as? List<Map<String, Any>>)?.firstOrNull() ?: return null
                    val last = data["last"]?.toString()?.toDoubleOrNull() ?: 0.0
                    val open24h = data["open24h"]?.toString()?.toDoubleOrNull() ?: last
                    val changePct = if (open24h > 0) ((last - open24h) / open24h * 100) else 0.0
                    """{"lastPrice":"$last","priceChangePercent":"$changePct"}"""
                } else null
            } catch (e: Exception) { null }
        }
    }

    override fun onBackPressed() {
        val webView = findViewById<WebView>(android.R.id.content)
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
