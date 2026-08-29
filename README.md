# 音频路由 · By JIAN

Android 平板音频路由应用。

## UI
- Apple 风格 Liquid Glass
- 自动适配平板屏幕比例
- 夜晚 / 白色模式
- NOW PLAYING UI
- 播放设备管理
- 自定义设备名称
- 0–1000 ms 延迟设置
- 同步锁定

## Android
- Android 8.0+ minimum
- Android 12+ Bluetooth permissions
- 本地 HTML UI，无需网络资源
- GitHub Actions 自动生成 Debug APK

## 构建
在 Android Studio 中打开仓库根目录，等待 Gradle Sync 后运行 app。

GitHub Actions 也会在 push 到 main 后自动构建：
`app/build/outputs/apk/debug/app-debug.apk`

## 后续原生能力
真实 Bluetooth 多路同步音频、MediaSession Apple Music 元数据读取、延迟补偿 Audio Engine 将在后续版本接入。
