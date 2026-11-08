# tv.2 设计依据与实施

本地 Android TV 原生实现，参考 Apple tvOS 的交互与视觉规范。不是 Apple 系统移植，也不提供 Apple 系统服务。

## 调研来源

- Apple HIG — Designing for tvOS: https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos
- Apple HIG — Materials: https://developer.apple.com/design/human-interface-guidelines/materials
- Apple TV 26 用户手册 — Control Center: https://support.apple.com/en-asia/guide/tv/atvb0a416871/26/tvos/26
- 社区技能索引 apple-hig: https://skills.sh/nexu-io/open-design/apple-hig （检索显示约 3.4K 安装；实际 SKILL.md 是指向 raintree-technology 的目录条目，不是 Apple 官方技能，未全局安装或执行外部脚本）。

2026-11-08 阅读官方文档与控制中心实图后，采用下列原则：大屏内容优先，柔和放大焦点，控制层与内容层分离，玻璃效果仅用于导航及浮层，保持清晰文字与稳定的方向键导航。

## 对上一版的修正

- 首页移除占据场景的大标题和宣传说明，应用名在选中的卡片下方显示。
- 右上时钟、应用入口、控制中心合为紧凑胶囊；图标采用 Lucide 一致的线条语言。
- 焦点保持约 12% 放大，增加上移，边缘由 2dp 粗白框改为 0.65dp 细高光。
- 常用栏增加静态背景采样模糊；外部主题壁纸仍使用原有材质路径。
- 控制中心从七行整块列表改为一个大输入模块及六个小模块。无虚构的电源控制、AirPlay 或音频设备状态。
- 控制中心和输入浮层采样实际首页背景并模糊；底色透出场景颜色。每次打开只计算一次，多个控件共享位图，不做持续全屏模糊。
- 应用总览优先读取应用自己的横幅；缺失时把真实图标置于统一比例卡片。普通浏览不显示重复的“已在主屏幕”或绿色勾选标记，选择模式仍保留状态。
- 设置使用同一场景的深色模糊背景，左侧仅当前焦点/选择项有底色，右侧按分组列表展示。

## 尺度、字体、动画

主屏和全屏页采用电视安全边距。控件以原生 dp/sp 排版；主要操作文字 13–19sp，页面标题 32–42sp。系统 sans-serif/medium，不分发 Apple 字体或 SF Symbols。Lucide 图标保留其许可证。其他第三方应用横幅、图标从已安装应用读取。

焦点反馈约 160–220ms；减少动态效果时取消新增控件的放大；关闭动画保留静态亮色焦点。显示设置、音频与网络页面仍由小米系统接管，其内部外观不在桌面权限范围内。

## 遥控器导航

首页左右浏览，上进入快捷入口，下浏览更多应用。控制中心七个功能均可通过方向键到达。返回关闭浮层；Home 收起浮层回常用栏。设置右键或确定进入详情，左键返回分组，返回逐层退出。长按应用保留整理、添加/移除、应用信息等已有能力。

## 素材与实现边界

背景为原项目的阿尔卑斯山静态图，非 Apple 航拍视频。玻璃材质是 Android 原生近似实现，不等同于 Apple 专有的 Liquid Glass 渲染。未伪造未连接的片库、节目推荐或硬件能力。
