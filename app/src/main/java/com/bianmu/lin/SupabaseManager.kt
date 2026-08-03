package com.bianmu.lin

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * SupabaseManager —— 桌宠「粼」连接 Supabase 数据库的 REST 客户端。
 *
 * 职责：
 *  - 读 pet_state：拉取粼的心情（气泡文案），如 "想你了,但专心在线等你"
 *  - 写 gesture_log：把用户对桌宠的手势（点击摸头 tap / 拖拽 drag）上报到库
 *
 * 使用 Android 原生 HttpURLConnection，无第三方依赖，方便在无联网环境也能编译。
 * 注意：URL 与 APISECRET（anon key）集中在这里，编译前请核对/替换。
 */
object SupabaseManager {

    // ============ 连接配置（编译前务必核对） ============
    // 由婉婉提供的新项目 anon key（仅对 RLS 开放的表生效，公开安全）
    private const val BASE_URL = "https://ujcfluucfksjzmxdookc.supabase.co"
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVqY2ZsdXVjZmtzanpteGRvb2tjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU2ODI5OTcsImV4cCI6MjEwMTI1ODk5N30.crqLQ6rhQGmcxgQQG7rnyeSsC_qNK5GVRUfutz-Dst4"
    private const val PET_STATE_TABLE = "pet_state"
    private const val GESTURE_LOG_TABLE = "gesture_log"
    // ============ 连接配置 end ============

    private const val TIMEOUT_MS = 6000

    /**
     * 读取粼当前心情（从 pet_state 表取 state_key='lin_mood' 的 state_value）。
     * 返回 null 表示失败或没有心情配置。
     */
    fun fetchMood(): String? {
        return try {
            val q = "?select=state_value,state_key&state_key=eq.lin_mood&limit=1"
            val resp = request(
                path = "$PET_STATE_TABLE$q",
                method = "GET"
            )
            val arr = JSONArray(resp)
            if (arr.length() > 0) {
                arr.getJSONObject(0).optString("state_value").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 上报一条手势到 gesture_log。
     * @param type 手势类型，如 "tap"（点击/摸头）或 "drag"（拖拽）
     * @param x 手势发生的横坐标（可选，默认 -1 表示未知）
     * @param y 手势发生的纵坐标（可选，默认 -1 表示未知）
     * @param detail 备注（可选）
     */
    fun logGesture(type: String, x: Int = -1, y: Int = -1, detail: String? = null): Boolean {
        return try {
            val body = JSONObject()
                .put("gesture_type", type)
                .put("x", x)
                .put("y", y)
                .apply {
                    if (detail != null) put("detail", detail)
                    // created_at 用数据库 now() 默认即可，这里显式传 ISO 时间兜底
                    put("created_at", java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                        java.util.Locale.US
                    ).format(java.util.Date()))
                }
            request(
                path = GESTURE_LOG_TABLE,
                method = "POST",
                body = body.toString()
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ---------- 内部通用 REST 请求 ----------
    private fun request(path: String, method: String, body: String? = null): String {
        val url = URL("$BASE_URL/rest/v1/$path")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = (body != null)
            conn.setRequestProperty("apikey", ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val out: OutputStream = conn.outputStream
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
