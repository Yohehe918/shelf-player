# CleanYourTV · 客厅定制版

面向小米 S Pro Mini LED（MiTV-MFTR0、Android 14 / HyperOS 3）的原生桌面。基于 Gathin23/cleanyourtv 的 294d3d59dc2514e69fed557f46c1ecdfb87a95ec，作为 Shelf 仓库中的独立 launcher 项目；原项目保留不动。

## 本版界面

- 主屏：全屏山景、精简状态入口、选中应用名、五列常用应用栏、放大焦点。
- 控制中心：右侧独立玻璃模块，提供应用、输入源、桌面设置、画面、网络、电视设置与屏幕保护入口。
- 所有应用：可滚动的五列横向图标卡片；常用应用优先；长按管理主屏快捷方式。
- 输入源：同一套面板和焦点样式，保留此机型已经验证的 HDMI 映射。
- 设置：左侧分组、右侧选项，背景、列数、行数、透明度、动态效果、开机应用、屏保与时钟格式可调整。
- 屏幕保护：同一套山景背景、较小的时钟、定期移动文字；任意键退出。
- 应用长按菜单、排序和返回焦点沿用并适配原有实现。

它是 Android 桌面，设计参考 tvOS；不是 Apple 官方软件，不包含 tvOS、Siri、AirPlay 或 Apple 账户服务。第三方应用及小米系统设置的内部页面仍由各自应用提供。山景是静态背景；没有将网络影视内容伪装成实时 Top Shelf 推荐，也没有导入 Apple 的宣传片或系统字体。

## 操作

方向键切换，确定打开。主屏按上进入右上角快捷入口；四格图标为所有应用，滑杆图标为控制中心。长按确定或菜单键打开应用菜单。返回逐层退出；主屏浏览下方应用时按返回回到常用栏。Home 关闭面板并回到第一项。

## 构建

需要 JDK 17、Android SDK platform 34 / build-tools 34.0.0。配置 ANDROID_HOME 或 local.properties 后运行：

```sh
./gradlew --no-daemon -PlauncherApplicationId=com.ts.launcher testReleaseUnitTest lintRelease assembleRelease
```

输出在 `app/build/outputs/apk/release/app-release.apk`。`com.ts.launcher` 是本机 HyperOS 的桌面兼容包名。不要把它当作所有电视通用的包名。release 使用本地 Android 调试签名；更新已安装版本时必须使用同一个签名，源码包不包含私钥。

本次设计版本是 `3.4-tv.3`，versionCode 16。保留了默认桌面、输入源和系统设置的既有跳转路径，没有新增联网权限、后台网络请求或分析收集。

## 参考与许可

- 上游：https://github.com/Gathin23/cleanyourtv （MIT；见 LICENSE）
- Apple tvOS 设计：https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos
- Apple 焦点交互：https://developer.apple.com/documentation/uikit/about-focus-interactions-for-apple-tv
- Apple 主屏参考：https://support.apple.com/guide/tv/navigate-apple-tv-atvb07c6b3ba/tvos
- 壁纸沿用上游 ambient-glass 的 `01-alps-switzerland.jpg`，来源和许可见 WALLPAPER-CREDITS.md。
- 字体使用 Android 自带 sans-serif；源码内上游字体许可见 FONT-LICENSE-OFL.txt。

实机检查结果见 VALIDATION.md。安装前保留原版安装包和可恢复的默认桌面设置；不要删除系统桌面或输入服务。

- 新增线条图标来自 Lucide，见 LICENSE-lucide；详细调研及设计取舍见 DESIGN.md。

## 3.4-tv.3 清理

移除静态主屏不再调用的 HeroArt 绘制、缓存及访问方法，以及依赖原仓库 tools 目录的旧安装脚本。使用上面的 Gradle 构建命令。R8 原先已能裁剪这条无用路径，因此不声称这项源码清理本身带来帧率提升。
