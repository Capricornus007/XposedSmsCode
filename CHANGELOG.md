# 更新日志 (CHANGELOG)

本日志记录了项目重构后的主要变更。

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

---

## [v3.0.2] - 2026-01-28
### 修复 (Fixed)
- 修复了自动填写权限逻辑。
- 修复了剪贴板静默失败的问题。

---

## [v3.0.1] - 2026-01-28
### 发布 (Released)
- v3.0.1 版本发布，包含多处稳定性改进。

---

## [v3.0.0] - 2026-01-28
### 发布 (Released)
- **UI 大版本更新**：由 Android View 彻底迁移至 Jetpack Compose。
- **架构重构**：采用 Single Activity 架构，引入 Compose Navigation。
- **自动化**：集成 GitHub Actions CI 自动化构建流程。

---

## [2.5.1_53] - 2026-01-28
### 变更 (Changed)
- 提升版本代码至 53。

---

> [!NOTE]
> 之前的历史日志请参考原始项目：[Original LOG-CN.md](https://github.com/tianma8023/XposedSmsCode/blob/master/LOG-CN.md)
