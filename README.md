# 📺 LiveTV

Android TV 电视直播应用，基于 VLC 播放 RTP 多播流，支持 4K HDR。

## 功能

- 🎬 VLC 播放器，支持 RTP/UDP 多播
- 📡 4K HDR 硬件解码直通
- 🎮 遥控器操作，零学习成本
- 📋 频道列表 + 分类筛选
- ⚡ 快速调台，按上下键预览后确认切换

## 遥控器操作

| 按键 | 功能 |
|------|------|
| ↑ / CH+ | 上一频道 |
| ↓ / CH- | 下一频道 |
| OK / 确认 | 切换到目标频道 |
| ← / 菜单 | 打开频道列表 |
| ← → (列表中) | 切换分类 |
| 返回 | 关闭 / 取消 |

## 技术栈

- Kotlin + Jetpack Compose
- libVLC 3.6 (RTP multicast + MediaCodec HW decode)
- Android 14+ (API 34)
- arm64-v8a

## 构建

```bash
./gradlew assembleRelease
```

APK 输出在 `app/build/outputs/apk/release/`。

## 频道配置

编辑 `app/src/main/java/dev/faraway/livetv/Channel.kt` 修改频道列表。

默认内嵌北京联通 IPTV 多播地址（104 个频道）。

## License

MIT
