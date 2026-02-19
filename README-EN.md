# XposedSmsCode
<div align="center">

[![CI](https://img.shields.io/github/actions/workflow/status/magisk317/XposedSmsCode/ci.yml?style=flat-square&label=Build&logo=github-actions&logoColor=white)](https://github.com/magisk317/XposedSmsCode/actions/workflows/ci.yml) [![Latest Release](https://img.shields.io/github/v/release/magisk317/XposedSmsCode?include_prereleases&style=flat-square&logo=github)](https://github.com/magisk317/XposedSmsCode/releases) [![Release Date](https://img.shields.io/github/release-date/magisk317/XposedSmsCode?style=flat-square)](https://github.com/magisk317/XposedSmsCode/releases) [![Downloads](https://img.shields.io/github/downloads/magisk317/XposedSmsCode/total?style=flat-square&color=blue)](https://github.com/magisk317/XposedSmsCode/releases) [![License](https://img.shields.io/github/license/magisk317/XposedSmsCode?style=flat-square)](LICENSE)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20--Beta2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org) [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.02.00-4285F4?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose) [![Gradle](https://img.shields.io/badge/Gradle-9.5.0--nightly-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org) [![AGP](https://img.shields.io/badge/AGP-9.1.0--alpha09-3DDC84?style=flat-square&logo=gradle&logoColor=white)](https://developer.android.com/studio/releases/gradle-plugin) [![Min SDK](https://img.shields.io/badge/Min_SDK-24_(Android_7)-brightgreen?style=flat-square&logo=android)](https://developer.android.com/about/versions/nougat) [![Target SDK](https://img.shields.io/badge/Target_SDK-36_(Android_16)-blue?style=flat-square&logo=android)](https://developer.android.com/about/versions) [![Xposed API](https://img.shields.io/badge/Xposed_API-82-orange?style=flat-square)](https://github.com/rovo89/XposedBridge) [![Telegram](https://img.shields.io/badge/Telegram-Group-2CA5E0?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+NR2QaQ4dlEgxYmNl)

</div>

An Xposed module which can recognize, parse SMS code and copy it to clipboard when a new message arrives. It can also input SMS code automatically.

[中文版说明](./README.md)

## Refactoring Highlights
- Settings entry migrated to Jetpack Compose; legacy Preference screens removed
- Configuration storage migrated to DataStore; SharedPreferences removed
- Added core/storage modules to split network and backup/serialization logic
- Backup writes schemaVersion/appVersion with import warnings on version gaps
- Rules/records/apps lists upgraded to ListAdapter + DiffUtil

[Refactoring Report](docs/REFACTORING.md)

# Screenshots
<img src="./art/en/01.png" width="180"/><img src="./art/en/02.png" width="180"/><img src="./art/en/03.png" width="180"/>

<div align="center">
    <a href="https://play.google.com/store/apps/details?id=com.github.tianma8023.xposed.smscode">
        <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>
    </a>
    <a href="https://github.com/magisk317/XposedSmsCode/releases">
        <img src="https://raw.githubusercontent.com/machiav3lli/oandbackupx/master/badge_github.png" alt="Get it on GitHub" height="80"/>
    </a>
</div>

# Communication & Feedback
- [Telegram Group](https://t.me/+NR2QaQ4dlEgxYmNl)

# Usage
1. Root your device and install Xposed Framework.
2. Install and activite this xposed module and then reboot.
3. Enjoy it!

Welcome any feedbacks.

# Attention
- **This module is designed for AOSP-like systems; it may not function correctly on heavily customized ROMs.**
- **Compatibility: Requires Android 15+ (API level ≥ 35).**
- **Supports LSPosed (Android 15+)**
- **Tech Stack: 100% Kotlin + Jetpack Compose + Room + Coroutines**
- **Please read the FAQ in the app first if you encounter any problems.**

# Features
- Copy verification code to clipboard when a new message arrives.
- Show toast when the verification code is copied.
- Show notification when verification SMS parsed.
- Mark verification SMS as read (experimental).
- Delete verification SMS when it's extracted successfully (experimental).
- Block verification SMS if it's extracted successfully.
- Custom keywords about verification code message (regular expressions allowed).
- Support the SMS code match rules customization, importation and exportation.
- Auto-input SMS code.
- **Deep integration for Android 15 (API 35)**
- **Material Design 3 (MD3) + Material You Dynamic Color**
- **100% Kotlin + Coroutines + Room Database**
- **Modern UI built with Jetpack Compose**
- **Settings page fully migrated to Jetpack Compose**

# Documentation
- [Release Logs](docs/CHANGELOG.md)
- [Privacy Policy](docs/PRIVACY.md)

# Thanks To
- [Xposed](https://github.com/rovo89/Xposed)
- [NekoSMS](https://github.com/apsun/NekoSMS)
- [Material Dialogs](https://github.com/afollestad/material-dialogs)
- [EventBus](https://github.com/greenrobot/EventBus)
- [Room](https://developer.android.com/training/data-storage/room)
- [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Material Design 3](https://m3.material.io/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

# License
All code is licensed under [GPLv3](https://www.gnu.org/licenses/gpl-3.0.txt) 

# Donation
If you find this project helpful, please consider rewarding the developer with a cup of coffee. Your support is the greatest motivation for my persistent maintenance!

| Alipay Red Packet | Alipay Receipt | WeChat Appreciation |
| :---: | :---: | :---: |
| ![Alipay Red Packet](./art/sponsorship/alipay_pocket.png) | ![Alipay](./art/sponsorship/alipay.png) | ![WeChat](./art/sponsorship/wx.png) |

![Star History Chart](https://api.star-history.com/svg?repos=magisk317/XposedSmsCode&type=Date)
