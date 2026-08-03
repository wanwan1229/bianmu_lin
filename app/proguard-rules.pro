# 边牧·粼 —— ProGuard 规则（当前 release 未开启 minify，规则保留备用）
# 保留 org.json（Android 内置，默认无需规则）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}