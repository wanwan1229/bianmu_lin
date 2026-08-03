package com.bianmu.lin

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * AI-Live-Overflow 桌宠身体层 · 悬浮窗服务
 * —— 电子边牧·粼 的身体骨架
 *
 * 思路对照仓库 ExampleOverlayService.kt：
 * - TYPE_APPLICATION_OVERLAY 悬浮窗 + 透明 WebView 渲染 pet.html
 * - 手势识别：拖拽（动作 MOVE）、单击返回"恢复原状"等
 * - 后续接 Supabase 读 pet_state 心情，写 gesture_log
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.bianmu.lin.action.START"
        const val ACTION_STOP = "com.bianmu.lin.action.STOP"
        private const val OVERLAY_WIDTH = 200   // dp
        private const val OVERLAY_HEIGHT = 260  // dp
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var webView: WebView

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> showOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        // 若还没悬浮窗权限，引导去系统设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        }

        // 用 layout 展开悬浮窗根视图（根视图里包含一个透明 WebView）
        val layoutInflater = LayoutInflater.from(this)
        overlayView = layoutInflater.inflate(R.layout.overlay_pet, null)
        overlayView.alpha = 1f

        // 透明 WebView 渲染 pet.html
        setupWebView()

        val wmParams = WindowManager.LayoutParams(
            dpToPx(OVERLAY_WIDTH),
            dpToPx(OVERLAY_HEIGHT),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.TOP or Gravity.START
        wmParams.x = dpToPx(160)  // 初始位置：屏幕偏右下一点
        wmParams.y = dpToPx(300)
        wmParams.width = dpToPx(OVERLAY_WIDTH)
        wmParams.height = dpToPx(OVERLAY_HEIGHT)

        // 手势：拖拽
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = wmParams.x
                    initialY = wmParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    // 位移超过 10px 视为拖拽（对照仓库：拖拽>10px）
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        dragging = true
                        wmParams.x = initialX + dx.toInt()
                        wmParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(overlayView, wmParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 单击（没拖拽）——可触发"蹭你"动画，通过 JS 告知 pet.html
                    if (!dragging) {
                        webView?.let { w ->
                            w.evaluateJavascript(
                                "window.roomieOnTap && window.roomieOnTap()",
                                null
                            )
                        }
                        // 上报手势到 Supabase（摸头）
                        logGestureInBackground("tap")
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(overlayView, wmParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupWebView() {
        webView = overlayView.findViewById(R.id.pet_webview)
        webView.setBackgroundColor(0x00000000) // 全透明背景
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportZoom(false)

        // 允许 WebView 加载外部 file:// 图片（真实像素边牧素材在手机存储里）
        settings.setAllowFileAccess(true)
        settings.setAllowContentAccess(true)
        @Suppress("DEPRECATION")
        settings.setAllowFileAccessFromFileURLs(true)
        @Suppress("DEPRECATION")
        settings.setAllowUniversalAccessFromFileURLs(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 用本地 assets 里的 pet.html 作为边牧身体
        webView.loadUrl("file:///android_asset/pet.html")

        // 后台连 Supabase：拉取粼的心情并注入气泡（失败时静默忽略，不影响桌宠显示）
        startSupabaseSync()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { windowManager.removeView(overlayView) }
    }

    /**
     * 后台连 Supabase：
     *  - 拉取粼当前心情（pet_state.state_key='lin_mood'）→ 延迟注入 pet.html 气泡
     * 无需联网/失败时静默忽略，不影响桌宠本身显示。
     */
    private fun startSupabaseSync() {
        Thread {
            try {
                // 等 WebView 加载完再注入（约 1.2s）
                Thread.sleep(1200)
                val mood = SupabaseManager.fetchMood()
                if (!mood.isNullOrBlank()) {
                    // 用 webView.post 回到主线程执行 JS 注入
                    webView.post {
                        try {
                            webView.evaluateJavascript(
                                "window.setBubble && window.setBubble(${jsonQuote(mood)})",
                                null
                            )
                        } catch (e: Exception) { /* 忽略 */ }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    /** 把一条手势（摸头/拖拽）上报到 gesture_log（后台线程，失败静默）。 */
    private fun logGestureInBackground(type: String) {
        Thread {
            try { SupabaseManager.logGesture(type) } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    /** 把一个字符串安全地包成 JS 字符串字面量。 */
    private fun jsonQuote(s: String): String =
        org.json.JSONObject().put("v", s).getString("v").replace("\"", "\\\"")

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
