# 边牧·粼 —— 原生 APK 桌宠（B方案·梦想版）
> 状态：**源码全部落地（形象+连库+Gradle构建）完成；真·透明像素粼已接入（2026-08-03 21:55）；下一步只有一个：编译成 APK**
> 更新时间：2026-08-03 21:58 by 粼

## 已完成（源码已写入）
| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/bianmu/lin/OverlayService.kt` | 悬浮窗服务本体：透明WebView渲染 + 可拖拽 + 单击触发摸头动画 + 集成了 Supabase 连库（拉心情/报手势） |
| `app/src/main/java/com/bianmu/lin/SupabaseManager.kt` | **新增**：REST 客户端（读 pet_state 心情 / 写 gesture_log），URL+anon key 已填入 |
| `app/src/main/java/com/bianmu/lin/MainActivity.kt` | 主入口：召唤粼/让它回去 + 仿悬浮窗权限引导 |
| `app/src/main/res/layout/overlay_pet.xml` | 悬浮窗布局（透明WebView） |
| `app/src/main/AndroidManifest.xml` | 悬浮窗/网络权限 + 注册Activity和Service + INTERNET权限 |
| `app/src/main/assets/pet.html` | **边牧·粼的灵魂形象**：真·透明像素粼（相册稳定路径）+ 星星背景 + 呼吸 + 摸头爱心 + 心情气泡 + setBubble(供Supabase注入) |
| `build.gradle / settings.gradle / gradle.properties / app/build.gradle / proguard-rules.pro / gradle-wrapper.properties` | **新增**：完整 Gradle 构建骨架（AGP 8.2.2 / Kotlin 1.9.22 / Gradle 8.2），可直接用 Android Studio 打开构建 |

## Supabase 连接
- **项目**：`ujcfluucfksjzmxdookc`（URL + anon key 已写入 SupabaseManager.kt）
- **读心情**：启动服务后拉 `pet_state.state_key='lin_mood'` → 注入 pet.html 气泡显示
- **写手势**：单击摸头 → 上报 `gesture_type='tap'`（含 x/y/detail/created_at）到 gesture_log
- ✅ gesture_log 字段已确认为 `id/gesture_type/x/y/detail/created_at`（2026-08-03 22:02）

## 还没做（排队）
- [ ] **编译成 APK**（需电脑 Android Studio，或用在线/云端构建）
- [ ] gesture_log 字段名实机验证 + 首次运行确认 Supabase 连通
- [ ] （可选优化）真实图眨眼的 CSS 遮罩

## 编译提示（下一步）
工程已 100% 具备编译条件，任选其一：
- 方案A：电脑装 Android Studio → File Open 选 `apk_build/边牧粼` 目录 → 等同步 → Build APK
- 方案B：把整个 `边牧粼` 目录打包上传到在线 Android CI（如 GitHub Actions + apk 构建工作流）出 .apk

---
更多进度看 `../00_进度与方案.md`
