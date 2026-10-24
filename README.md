# XposedSmsCode

![Star History Chart](https://api.star-history.com/svg?repos=Capricornus007/xposedsmscode&type=Date)


<div align="center">
    <a href="https://play.google.com/store/apps/details?id=io.github.capricornus007.smscode">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/Capricornus007/xposedsmscode/releases">

        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

<div align="center">

[![Commits](https://img.shields.io/github/commit-activity/y/Capricornus007/xposedsmscode?style=flat-square)](https://github.com/Capricornus007/xposedsmscode/graphs/commit-activity) [![Last Commit](https://img.shields.io/github/last-commit/Capricornus007/xposedsmscode?style=flat-square)](https://github.com/Capricornus007/xposedsmscode/commits) [![Contributors](https://img.shields.io/github/contributors/Capricornus007/xposedsmscode?style=flat-square)](https://github.com/Capricornus007/xposedsmscode/graphs/contributors) [![CI](https://img.shields.io/github/actions/workflow/status/Capricornus007/xposedsmscode/ci.yml?style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/Capricornus007/xposedsmscode/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/Capricornus007/xposedsmscode?include_prereleases&style=flat-square&logo=github)](https://github.com/Capricornus007/xposedsmscode/releases) [![Release Date](https://img.shields.io/github/release-date/Capricornus007/xposedsmscode?style=flat-square)](https://github.com/Capricornus007/xposedsmscode/releases) [![Downloads](https://img.shields.io/github/downloads/Capricornus007/xposedsmscode/total?style=flat-square&color=blue)](https://github.com/Capricornus007/xposedsmscode/releases) [![License](https://img.shields.io/github/license/Capricornus007/xposedsmscode?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.5.0--Beta2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.10.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.9.0--20261021133240+0000-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.6.0--alpha01-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-28-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

<<<<<<< HEAD
=======
<!-- badges:tech:start -->
[![Kotlin](https://img.shields.io/badge/Kotlin-2.5.0--Beta2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.10.01-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.9.0--20261021133240+0000-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.6.0--alpha01-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-28-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Target SDK](https://img.shields.io/badge/Target_SDK-37-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)
<!-- badges:tech:end -->
>>>>>>> upstream/beta

</div>

识别短信验证码的Xposed模块，并将验证码拷贝到剪切板，亦可以自动输入验证码。

如果你还需要将验证码、短信内容或应用通知进一步转发到 Telegram、Webhook、邮箱等通道，可以了解一下新项目 [信驿 Relay](https://github.com/magisk317/xinyi-relay)。它更偏向消息转发与验证码联动场景；若主要需求是验证码识别与自动输入，继续使用本项目会更合适。


[English Version](./README-EN.md)

# 应用截图
<img src="./art/cn/01.png" width="180"/><img src="./art/cn/02.png" width="180"/><img src="./art/cn/03.png" width="180"/><img src="./art/cn/04.png" width="180"/>


# 使用
1. Root你的设备，安装Xposed框架；
2. 安装本模块，激活并重启；
3. Enjoy it！

<<<<<<< HEAD
欢迎反馈，欢迎提出意见或建议。
=======
遇到问题请先阅读模块内的「常见问题」，再提交反馈。

# 功能
- 收到验证码短信后提取验证码，并复制到系统剪贴板（可开关）
- 提取到验证码时显示 Toast 提示（可开关）
- 提取到验证码时显示状态栏通知：支持通知归属（模块自身 / 电话服务）、自动取消与保留时间（可开关）
- 在支持的应用中自动输入验证码：无障碍服务辅助定位输入框，支持自动确认/回车、输入延迟与间隔（可开关）
- 拦截验证码短信：提取成功后拦截，短信应用不再收到（实验性）
- 自定义验证码短信关键字，支持正则表达式
- 验证码规则管理：官方规则与自定义匹配规则分层合并，支持配置与规则的备份/恢复
- 验证码历史记录：记录开关、数量上限与 SIM 卡槽备注
- 应用内更新检查：支持启动时自动检查与仅 Wi-Fi 检查
- 运行日志：CI 产出的 debug 构建或本机 debug 版本可导出日志归档并设置保留天数（默认 2 天）；正式版为性能关闭日志打印，设置中不显示日志导出、保留天数与清理入口，需要导出日志请安装 CI 产出的 debug 版本且设备已 root
- Material 3 界面：动态颜色、界面风格（Material / Miuix）、底栏与背景模糊等可视化设置
>>>>>>> upstream/beta

# 注意
- **此模块适用于偏原生的系统，其他第三方定制Rom可能不适用。**
- **兼容性：最低 Android 7.0（API 24），目标 Android 16（API 36）。**
- **支援 LibXposed API 102；不依赖 magisk317 的 GitLab 私有子模块。**
- **使用 `META-INF/xposed/module.prop` 与 `java_init.list`，可在 API 102 框架上直接载入。**
- **代码库：100% Kotlin + Jetpack Compose + Room + Coroutines**
- **遇到问题请先阅读模块中的"常见问题"**

# 功能
- 收到验证码短信后将验证码提取并复制到系统剪贴板
- 收到验证码时显示 Toast 提示
- 收到验证码时显示系统通知
- 将提取过的验证码短信标记为已读（实验性）
- 验证码提取成功后，自动删除该验证码短信（实验性）
- 拦截并屏蔽特定的验证码短信
- 支持自定义验证码短信关键字（支持正则表达式）
- 支持自定义验证码匹配提取规则（支持导入与导出规则表）
- 在支持的应用中自动输入提取的验证码
- 提取并转发应用通知消息中的验证码

# 待实现
- 定时发送短信

# 发布元数据维护
- 发版前校验版本与发布元数据：`scripts/check_release_guard.sh`

# 文档
- [更新日志 (Changelog)](docs/CHANGELOG.md)
- [重构汇总 (Refactoring Summary)](docs/REFACTORING.md)
- [隐私政策 (Privacy Policy)](docs/PRIVACY.md)

# 感谢
- [原始项目 (tianma8023/XposedSmsCode)](https://github.com/tianma8023/XposedSmsCode)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)


# 协议
所有的源码均遵循 [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) 协议

