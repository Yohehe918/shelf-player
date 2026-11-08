# 片库 0.2

面向小米电视 S Pro 85 Mini LED 的 Android TV 播放器。以 Infuse 8 的 tvOS 版作为界面和功能参考。

## 在电视上使用

1. 从 CleanYourTV 底部 Dock 打开「片库」。
2. 选「添加 SMB 共享」，填写 NAS 的地址、共享文件夹名和账号。默认端口为 445。地址也可以写成 `192.168.1.50:1445`。
3. 打开共享后，应用会递归扫描影片，并建立电影和剧集视图。搜索、刷新、设置在首页顶部。密码用这台设备的 Android Keystore 密钥加密。
4. 封面可用 `片名-poster.jpg`、`poster.jpg` 或 `folder.jpg`，背景图可用 `片名-fanart.jpg` 或 `fanart.jpg`。支持 JPG、PNG、WebP。给视频旁放同名的 SRT、ASS、SSA 或 VTT，例如 `Movie (2024).zh.srt`。
5. 播放时按下方向键调出进度条及信息、视频、音频、字幕菜单。播放退出后可续播。

## 在线匹配

在「设置 → 在线匹配」中可以填写 TMDB API Read Access Token 和 OpenSubtitles 应用 API Key。凭据仅需在电视上输入。

- TMDB 按片名、年份和影片类别匹配。片名相同且无法区分的结果会保留为本地文件名。已有本地封面和背景时优先使用本地图片。
- OpenSubtitles 在没有同名外置字幕时优先查询文件指纹，自动下载需要精确匹配。也可以在详情或播放的字幕菜单手动查找。未登录时下载额度由服务方决定。
- 本地 SMB 浏览、播放、封面和字幕不需要联网账号。在线接口需要各自的密钥及电视能访问对应服务。
- [TMDB 应用鉴权](https://developer.themoviedb.org/docs/authentication-application)
- [OpenSubtitles API 说明](https://blog.opensubtitles.com/opensubtitles/how-to-build-a-movie-subtitle-finder-cli-with-python-and-the-opensubtitles-api)

## 构建与范围

使用 JDK 17 和 Android SDK 36。运行 `./gradlew assembleDebug`，APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

当前版本提供 SMB 2/3、海报与详情、按电影/剧集分类、收藏、搜索、继续观看、外置字幕、Media3 播放控制、音轨和字幕选择，以及设备音视频能力诊断。TMDB、OpenSubtitles 接口需要有效密钥才能完成端到端验证。Plex、Emby、Jellyfin、其他网络协议、云端同步、剧集按季聚合、片头识别和完整的 Infuse 格式兼容仍需实现。

所用 Material Icons 来自 Google，授权见 `MATERIAL-ICONS-LICENSE.txt`。Infuse 的品牌、官方界面截图和测试海报不包含在 APK 或源码中。
