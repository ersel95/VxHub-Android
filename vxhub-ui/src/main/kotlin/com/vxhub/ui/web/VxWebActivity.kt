package com.vxhub.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.vxhub.providers.VxWebPresenter
import com.vxhub.ui.R
import com.vxhub.ui.internal.VxIcons

/** Shows the panel's legal pages and any URL inside the app — iOS's `VxWebViewer`. */
internal class VxWebActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val showClose = intent.getBooleanExtra(EXTRA_SHOW_CLOSE, true)
        setContent {
            var progress by remember { mutableStateOf(0f) }
            var webView by remember { mutableStateOf<WebView?>(null) }
            BackHandler {
                val view = webView
                if (view != null && view.canGoBack()) view.goBack() else finish()
            }
            Column(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (showClose) {
                    Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.CenterEnd) {
                        IconButton(onClick = ::finish) {
                            Icon(VxIcons.Close, contentDescription = stringResource(R.string.vxhub_close), tint = Color.Black)
                        }
                    }
                }
                if (progress in 0.01f..0.99f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    !(request.url.scheme == "https" || request.url.scheme == "http")

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    progress = 0.05f
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    progress = 1f
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_URL = "com.vxhub.ui.web.url"
        private const val EXTRA_SHOW_CLOSE = "com.vxhub.ui.web.showClose"

        fun intent(context: Context, url: String, showCloseButton: Boolean): Intent =
            Intent(context, VxWebActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SHOW_CLOSE, showCloseButton)
                .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
}

/**
 * Registered as [com.vxhub.providers.VxProviderRegistry.webPresenter]. Android shows every page full
 * screen, so `fullScreen` only matters on iOS; a close button is always offered unless the caller
 * relies on the back gesture alone (`showCloseButton = false` and `fullScreen = true`).
 */
internal object VxWebPresenterImpl : VxWebPresenter {
    override fun present(context: Context, url: String, fullScreen: Boolean, showCloseButton: Boolean) {
        context.startActivity(VxWebActivity.intent(context, url, showCloseButton || !fullScreen))
    }
}
