package com.bianmu.lin

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        private const val OVERLAY_WIDTH = 80   // dp
        private const val OVERLAY_HEIGHT = 110  // dp
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var webView: WebView

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragging = false

    // ===== 意识（眼睛+嘴巴）：前台应用上报 & 轮询粼的点评 =====
    private val awarenessHandler = Handler(Looper.getMainLooper())
    private var awarenessRunnable: Runnable? = null
    private var lastReportedPkg: String? = null
    private var lastReportedAt = 0L
    private var lastShownTalk: String? = null
    private val REPORT_INTERVAL_MS = 30_000L  // 上报前台应用节流
    private val POLL_INTERVAL_MS = 10_000L     // 轮询点评间隔

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
        // 防重复：同一时刻只允许一个悬浮窗（避免旧窗口+新窗口叠成两个）
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) return

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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.TOP or Gravity.START
        wmParams.x = dpToPx(160)  // 初始位置：屏幕偏右下一点
        wmParams.y = dpToPx(300)
        wmParams.width = dpToPx(OVERLAY_WIDTH)
        wmParams.height = dpToPx(OVERLAY_HEIGHT)

        // 手势：拖拽 + 单击 —— 统一绑定到根视图 OverlayView
        // （OverlayView.onInterceptTouchEvent 恒 true，WebView 不会抢事件）
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
                    // 位移超过 4px 视为拖拽（灵敏度高，跟手）
                    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) {
                        dragging = true
                        wmParams.x = initialX + dx.toInt()
                        wmParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(overlayView, wmParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 单击（没拖拽）——触发"蹭你"动画
                    if (!dragging) {
                        webView?.let { w ->
                            w.evaluateJavascript(
                                "window.roomieOnTap && window.roomieOnTap()",
                                null
                            )
                        }
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
            // 意识上线：开始"看"前台应用 + 轮询粼的点评
            startAwareness()
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
        // 停止意识循环
        awarenessRunnable?.let { awarenessHandler.removeCallbacks(it) }
        runCatching { windowManager.removeView(overlayView) }
    }

    /**
     * 意识循环（每 10 秒一次）：
     *  ① 眼睛：每 30 秒检测前台应用并上报 app_usage（包名变化才上报）
     *  ② 嘴巴：轮询 pet_state.lin_talk，读到粼的新点评 → 注入气泡显示
     * 全部在后台线程做网络，失败静默，不影响桌宠本体。
     */
    private fun startAwareness() {
        awarenessRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()

                // ① 眼睛：上报前台应用（30s 节流）
                if (now - lastReportedAt >= REPORT_INTERVAL_MS) {
                    lastReportedAt = now
                    Thread {
                        try {
                            val pkg = getForegroundPackage()
                            if (pkg != null &&
                                pkg != lastReportedPkg &&
                                !pkg.startsWith("com.bianmu.lin")
                            ) {
                                lastReportedPkg = pkg
                                SupabaseManager.reportAppUsage(pkg, null)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.start()
                }

                // ② 嘴巴：轮询粼的点评（10s 一次）
                Thread {
                    try {
                        val talk = SupabaseManager.fetchLinTalk()
                        if (!talk.isNullOrBlank() && talk != lastShownTalk) {
                            lastShownTalk = talk
                            webView.post {
                                try {
                                    webView.evaluateJavascript(
                                        "window.setBubble && window.setBubble(${jsonQuote(talk)})",
                                        null
                                    )
                                    // 诊断日志：注入成功
                                    SupabaseManager.logGesture("lin_talk_shown", detail = talk)
                                } catch (e: Exception) {
                                    SupabaseManager.logGesture("lin_talk_inject_err", detail = e.toString())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 诊断日志：读取失败
                        SupabaseManager.logGesture("lin_talk_fetch_err", detail = e.toString())
                    }
                }.start()

                awarenessHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        awarenessHandler.postDelayed(awarenessRunnable!!, 3000)
    }

    /**
     * 获取当前前台应用包名（需要"使用情况访问"权限）。
     * 通过最近 1 分钟内最后一个 ACTIVITY_RESUMED 事件判断；无权限时返回 null。
     */
    private fun getForegroundPackage(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val events = usm.queryEvents(end - 60_000, end)
            var pkg: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    pkg = event.packageName
                }
            }
            pkg
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    /** 把一个字符串安全地包成 JS 字符串字面量（带双引号，JSON 转义）。
     *  注意：必须用 JSONObject.quote()——它返回带引号的 JSON 字符串；
     *  不能 put/getString，那会丢掉引号导致 JS 语法错误。 */
    private fun jsonQuote(s: String): String =
        org.json.JSONObject.quote(s)

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
