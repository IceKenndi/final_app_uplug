package com.phinmaed.uplug

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment

class MapFragment : Fragment() {

    private lateinit var mapWebView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        mapWebView = view.findViewById(R.id.mapWebView)

        mapWebView.setBackgroundColor(Color.TRANSPARENT)
        mapWebView.webViewClient = WebViewClient()
        mapWebView.webChromeClient = WebChromeClient()

        with(mapWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        mapWebView.loadUrl("file:///android_asset/campus_map_mobile.html")

        return view
    }

    override fun onDestroyView() {
        mapWebView.stopLoading()
        mapWebView.loadUrl("about:blank")
        mapWebView.destroy()
        super.onDestroyView()
    }
}