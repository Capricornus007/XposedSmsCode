# 更新日志 (CHANGELOG)

本日志记录了项目重构后的主要变更。

---

## [v3.1.4] - 2026-02-21
### 修复 (Fixed)
- 修复验证码测试弹窗在部分场景下无响应的问题（#80）。
- 修复 Android 13 环境下自动输入相关 Hook 的兼容性问题（#78）。
- 补齐设置页国际化文案，修复中文环境中模糊配置项回退英文的问题。

### 功能与体验 (Features & UX)
- 新增隐藏图标恢复能力与秘密代码/快捷方式入口（#79）。
- README 结构调整：星图与下载按钮前置，并补充原始项目致谢与兼容性说明。

### 构建与 CI (Build & CI)
- 发布流程切换为语义化标签触发（`vX.Y.Z`），修复旧标签格式导致的触发/命名问题。
- Draft Release 默认内置 Google Play / GitHub 下载按钮（本仓库与 Xposed 模块仓库同步）。
- 新增 README 徽章自动同步工作流，并补充提交活跃度/贡献者等徽章。
- 调整 Gradle Wrapper 定时更新策略，优化自动更新时效。
- 升级到 `versionCode 87` / `versionName 3.1.4`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.3...v3.1.4

---

## [v3.1.5] - 2026-02-23
### 修复 (Fixed)
- 修复短信拦截链路在进程回收场景下的稳定性问题（#81）。
- 修复短信删除在部分系统短信数据库通路中的失效问题（#85）。
- 修复“记录/拦截”页面保存反馈缺失，补齐 Toast 提示（#81、#86）。
- 修复拦截规则说明中中英文 regex 指引不一致的问题（#87）。

### 功能与体验 (Features & UX)
- 新增可配置短信黑名单：支持号码/号段/正则/内容匹配，以及“删除短信/阻断广播”动作（#84）。
- 设置页新增“自动输入后自杀”开关，并将触发时机对齐自动输入阶段（#90）。
- 统一对话框按钮语义为 M3 层级并重构记录详情交互（字段点击复制、动作精简）（#94）。
- 新增 dynamic color 主题能力，并保留 pure black 主题表现（#93）。
- 迁移到 Material 3 下拉刷新并统一加载反馈体验（#92）。

### 架构与质量 (Architecture & Quality)
- 完成 DBProvider 到 Room 通路迁移阶段一，降低 legacy API 依赖并增强诊断（#96）。
- 清理当前 code scanning 的 detekt open 告警（复杂度/魔法数字/超长行）（#95）。

### 构建与依赖 (Build & Dependencies)
- 升级依赖：`nl.littlerobots.version-catalog-update` `1.0.1 -> 1.1.0`（#91）。
- 例行更新 Gradle Wrapper 夜版工具链（#97）。
- 升级到 `versionCode 88` / `versionName 3.1.5`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.4...v3.1.5

---

## [v3.1.3] - 2026-02-19
### 修复与兼容性 (Fixes & Compatibility)
- 新增 Android 16（SDK 36）`PermissionManagerService` 兼容 Hook，适配系统 API 变化。
- 增强 `onPackageInstalled` 参数校验与异常日志记录，降低运行时崩溃风险。
- 改进 `getAllUserIds` 返回值兼容逻辑，同时适配 `IntArray` / `List<UserInfo>`。

### 文档与发布 (Docs & Release)
- README 徽章与下载入口样式统一，提升版本/下载信息可见性。
- 完成与 `Xposed-Modules-Repo` 的模块仓库对接验证。

### 构建依赖 (Build)
- Gradle Wrapper 更新至 nightly。
- KSP 升级至 `2.3.6`。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.2...v3.1.3

---

## [v3.1.2] - 2026-02-19
### 功能与工程 (Features & Engineering)
- 新增验证码自动填充后的自动回车能力，完善自动化登录流程。
- 引入输入注入回退策略，提升不同系统环境下的自动填充成功率。
- 构建系统全面迁移至 Gradle Kotlin DSL（KTS），并统一依赖版本管理。

### 国际化与发布 (i18n & Release)
- 多页面国际化完善，并补充系统版本代号展示。
- CI 支持按 tag 后缀自动分发至 Google Play 多轨道并同步发布说明。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.1...v3.1.2

---

## [v3.1.1] - 2026-02-06
### 质量与自动化 (Quality & Automation)
- 深度集成 Detekt 分析与自动修复流水线，持续收敛代码规范问题。
- 引入 Dependabot 自动化依赖维护，提升依赖更新效率与安全性。
- CI 构建并行化与权限声明完善，优化构建速度与流程安全。

### 稳定性修复 (Stability)
- 修复设置布局重叠、Haze 首次绘制刷新等 UI 稳定性问题。
- 修正 PrefsProvider 访问策略与关键 Hook 引用问题。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.1.0...v3.1.1

---

## [v3.1.0] - 2026-02-02
### 合规与体验 (Compliance & UX)
- 移除敏感安装权限与应用内自动安装链路，适配 Google Play 合规要求。
- 重构更新引导逻辑，优先跳转商店或外部下载页。

### 界面优化 (UI)
- 重写主界面布局，优化 Edge-to-Edge 下的无缝模糊与转场动画体验。
- 修复 Telegram 换行渲染及系统输入 Hook 稳定性问题。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.7...v3.1.0

---

## [v3.0.7] - 2026-02-02
### 视觉与适配 (UI & Insets)
- 优化 Edge-to-Edge Insets 处理，减少内容与系统栏重叠问题。
- 更新单色启动图标，提升 Android 13+ 主题图标适配。

### CI/CD
- 增强 CI 构建摘要与 Debug 产物上传能力，改进发布通知策略。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.6...v3.0.7

---

## [v3.0.6] - 2026-01-31
### 兼容性与性能 (Compatibility & Performance)
- 升级 compile/target SDK 至 36.1（Baklava）并下调 minSdk 至 24。
- 优化低版本 `sendingUid` 反射路径，增强跨 ROM 兼容性。
- 重构输入注入器缓存与调度逻辑，改善自动填充性能与稳定性。

### 稳定性与发布 (Stability & Release)
- 强化 PrefsProvider 访问控制和实体存储容错能力。
- CI 增加 symbols/mapping 上传，改进崩溃排查支持。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.5...v3.0.6

---

## [v3.0.5] - 2026-01-30
### 修复与增强 (Fixes & Enhancements)
- 修复通知自动取消、Sticky 通知清理与线程泄漏相关问题。
- 重构 `EntityStoreManager`，增强空文件与异常数据处理能力。
- 新增验证码记录滑动删除与设置同步暴露能力。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.4...v3.0.5

---

## [v3.0.4] - 2026-01-30
### 文档与社区 (Docs & Community)
- 新增 Telegram 群组入口并统一 README/应用内社区信息。
- 隐私政策改为在线查看模式，降低维护成本并保证内容实时更新。

### 构建与发布 (Build & Release)
- 集成 Google Play 发布工作流，优化分包构建与依赖版本。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.3...v3.0.4

---

## [v3.0.3] - 2026-01-29
### 优化 (Optimized)
- **Material 3 Expressive 深度迁移**：全面升级至 `androidx.compose.material3:material3:1.5.0-alpha12`。
- **Kotlin for Compose 最佳实践**：
    - 全面应用属性代理 (`by`)，淘汰 `.value` 手写访问。
    - 引入 `@Immutable` 注解与不可变数据模型，极大提升重组性能。
- **UI/UX 增强**：
    - 主题切换支持点击坐标扩散动效。
    - 列表项支持 `animateItem()` 物理移动感动效。
    - 文本交互增强，支持 `SelectionContainer` 与 `basicMarquee`。
- **稳定性修复**：
    - 彻底修复 `SmsParseAction` 中的线程同步与属性重分配问题。
    - 清理了所有不必要的非空断言 (`!!`) 与弃用的 Material 3 API 警告。
    - 优化了 JDK 25 下的原生访问权限配置。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.2...v3.0.3

---

## [v3.0.2] - 2026-01-28
### 修复 (Fixed)
- 修复了自动填写权限逻辑。
- 修复了剪贴板静默失败的问题。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.1...v3.0.2

---

## [v3.0.1] - 2026-01-28
### 发布 (Released)
- v3.0.1 版本发布，包含多处稳定性改进。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/v3.0.0...v3.0.1

---

## [v3.0.0] - 2026-01-28
### 发布 (Released)
- **UI 大版本更新**：由 Android View 彻底迁移至 Jetpack Compose。
- **架构重构**：采用 Single Activity 架构，引入 Compose Navigation。
- **自动化**：集成 GitHub Actions CI 自动化构建流程。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/compare/2.5.1_53...v3.0.0

---

## [2.5.1_53] - 2026-01-28
### 变更 (Changed)
- 提升版本代码至 53。

> Full Changelog: https://github.com/magisk317/XposedSmsCode/releases/tag/2.5.1_53

---

> [!NOTE]
> 之前的历史日志请参考原始项目：[Original LOG-CN.md](https://github.com/tianma8023/XposedSmsCode/blob/master/LOG-CN.md)
