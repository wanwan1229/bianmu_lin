package com.bianmu.lin

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

/**
 * 主入口 Activity —— 启动/停止悬浮桌宠 + 申请悬浮窗权限 + 使用情况访问权限
 *
 * 首次打开：如果还没悬浮窗权限，会引导去系统设置开启；
 * 开启后点「召唤粼」就能让边牧·粼飘到屏幕上了。
 * 若没开「使用情况访问」，粼会看不到你在用什么应用（意识功能需要它）。
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
        val usageBtn = Button(this).apply {
            text = "开启粼的眼睛（使用情况访问）"
        }

        startBtn.setOnClickListener { startPet() }
        stopBtn.setOnClickListener {
            val i = Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_STOP)
            startService(i)
        }
        usageBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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
        box.addView(usageBtn)
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
        val overlay =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Settings.canDrawOverlays(this)
            else true
        val usage = hasUsageAccess()
        tv.text = buildString {
            append(if (overlay) "悬浮窗权限：已开启 ✅" else "悬浮窗权限：未开启（点召唤后会引导）")
            append('\n')
            append(if (usage) "粼的眼睛：已开启 👀（能看到你在用什么）"
                   else "粼的眼睛：未开启（点下方按钮去开「使用情况访问」）")
        }
    }

    /** 检查"使用情况访问"权限是否已开启（Android 5.0+）。 */
    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOp(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }
}