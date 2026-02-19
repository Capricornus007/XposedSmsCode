# XposedSmsCode
<div align="center">

[![CI](https://img.shields.io/github/actions/workflow/status/magisk317/XposedSmsCode/ci.yml?style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/magisk317/XposedSmsCode/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/XposedSmsCode?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/XposedSmsCode/releases) [![Release Date](https://img.shields.io/github/release-date/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/releases) [![Downloads](https://img.shields.io/github/downloads/magisk317/XposedSmsCode/total?style=flat-square&color=blue)](https://github.com/magisk317/XposedSmsCode/releases) [![License](https://img.shields.io/github/license/magisk317/XposedSmsCode?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20--Beta2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.02.00-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.5.0--nightly-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.1.0--alpha09-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-24_(Android_7)-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions/nougat) [![Target SDK](https://img.shields.io/badge/Target_SDK-36_(Android_16)-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-82-orange?style=flat-square)](https://github.com/rovo89/XposedBridge) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

</div>

识别短信验证码的Xposed模块，并将验证码拷贝到剪切板，亦可以自动输入验证码。

[English Version](./README-EN.md)

## 重构要点
- 设置入口已迁移至 Jetpack Compose，旧 Preference 页面移除
- 配置存储迁移到 DataStore，移除 SharedPreferences
- 新增 core/storage 模块，拆分网络与备份/序列化逻辑
- 备份写入 schemaVersion/appVersion，导入提供版本跨度提示
- 规则/记录/应用列表升级为 ListAdapter + DiffUtil

[重构与现代化报告](docs/REFACTORING.md)

# 应用截图
<img src="./art/cn/01.png" width="180"/><img src="./art/cn/02.png" width="180"/><img src="./art/cn/03.png" width="180"/>

# 下载
<div align="center">
    <a href="https://play.google.com/store/apps/details?id=com.github.tianma8023.xposed.smscode">
        <img src="https://play.google.com/intl/zh-CN/badges/static/images/badges/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/XposedSmsCode/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

# 交流与反馈
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)


# 使用
1. Root你的设备，安装Xposed框架；
2. 安装本模块，激活并重启；
3. Enjoy it！

欢迎反馈，欢迎提出意见或建议。

# 注意
- **此模块适用于偏原生的系统，其他第三方定制Rom可能不适用。**
- **兼容性：兼容 Android 15 及以上（API 等级 ≥ 35）设备。**
- **支持 LSPosed (Android 15+)**
- **代码库：100% Kotlin + Jetpack Compose + Room + Coroutines**
- **遇到问题请先阅读模块中的"常见问题"**

# 功能
- 收到验证码短信后将验证码复制到系统剪贴板
- 收到验证码时显示Toast
- 收到验证码时显示通知
- 将验证码短信标记为已读（实验性）
- 验证码提取成功后，删除验证码短信（实验性）
- 拦截验证码短信
- 自定义验证码短信关键字（正则表达式）
- 自定义验证码匹配规则，并支持规则导入导出
- 自动输入验证码
- **全系统 Android 15 (API 35) 深度适配**
- **Material Design 3 (MD3) + Material You 动态配色**
- **100% Kotlin + 协程 (Coroutines) + Room 数据库**
- **Jetpack Compose 现代化 UI (FaqFragment 已迁移)**
- **设置页已升级为 Jetpack Compose**

# 文档
- [更新日志 (Changelog)](docs/CHANGELOG.md)
- [重构汇总 (Refactoring Summary)](docs/REFACTORING.md)
- [隐私政策 (Privacy Policy)](docs/PRIVACY.md)

# 感谢
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

# 赞助与捐赠
如果您觉得本项目对您有所帮助，欢迎给开发者投喂一杯咖啡。您的支持是我坚持维护的最大动力！

| 支付宝红包口令 | 支付宝收款码 | 微信赞赏码 |
| :---: | :---: | :---: |
| ![Alipay Red Packet](./art/sponsorship/alipay_pocket.png) | ![Alipay](./art/sponsorship/alipay.png) | ![WeChat](./art/sponsorship/wx.png) |

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)
