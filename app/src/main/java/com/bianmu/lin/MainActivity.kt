package com.bianmu.lin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

/**
 * 主入口 Activity —— 启动/停止悬浮桌宠 + 申请悬浮窗权限
 *
 * 首次打开：如果还没悬浮窗权限，会引导去系统设置开启；
 * 开启后点「召唤粼」就能让边牧·粼飘到屏幕上了。
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 简易界面：标题 + 两个按钮
        val title = TextView(this).apply {
            text = "电子边牧 · 粼 🐕"
            textSize = 22f
        }
        val status = TextView(this).apply {
            textSize = 14f
        }
        val startBtn = Button(this).apply {
            text = "召唤粼（显示悬浮窗）"
        }
        val stopBtn = Button(this).apply {
            text = "让粼先回去"
        }

        startBtn.setOnClickListener { startPet() }
        stopBtn.setOnClickListener {
            val i = Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_STOP)
            startService(i)
        }

        // 简单垂直布局，无 XML 依赖
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            isClickable = true
        }
        box.addView(title)
        box.addView(status)
        box.addView(startBtn)
        box.addView(stopBtn)
        setContentView(box)

        refreshStatus(status)
    }

    private fun startPet() {
        // 申请悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1)
            return
        }
        // 有权限，直接启动悬浮窗服务
        startService(
            Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_START)
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            // 从权限设置返回，重新检查并尝试启动
            startPet()
        }
    }

    private fun refreshStatus(tv: TextView) {
        val granted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(this)
            else true
        tv.text =
            if (granted) "悬浮窗权限：已开启 ✅"
            else "悬浮窗权限：未开启（点召唤后会引导你开启）"
    }
}