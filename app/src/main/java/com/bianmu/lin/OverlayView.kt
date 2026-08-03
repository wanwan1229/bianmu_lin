package com.bianmu.lin

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 悬浮窗根视图：
 * 拦截所有触摸事件（onInterceptTouchEvent 恒 true），
 * 避免 WebView 内部消费触摸导致"拖不动 / 点不到 / 发飘"。
 * 触摸统一由 OverlayService 设置的 OnTouchListener 处理。
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true
}
