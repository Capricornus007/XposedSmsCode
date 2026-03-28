# XposedSmsCode 架构收口说明

本文档描述当前 `XposedSmsCode` 在向 `xinyi-relay` 结构对齐后的推荐分层与开发约束。

## 当前分层

### `runtime`
- 运行时与数据主层
- 承载：
  - `common/constant`、runtime-only `common/utils`
  - `data/db`、`data/prefs`、`data/update`
  - `feature/backup`、`feature/store`
  - `forwarder/*`
- 允许继续保留现有 Kotlin package，不强制做包名重命名
- 对外优先暴露 facade，减少上层直接依赖实现细节

### `core`
- Compose UI、导航、ViewModel、应用内 facade
- 只保留界面层、展示层和应用内交互协调
- 不直接依赖 `AppDatabase`、`DBManager`、`DBProvider`
- 不直接依赖 update 实现类（下载/安装/校验器）
- update 检查结果、策略判断与 Play 更新动作统一经 `RuntimeUpdateFacade`

### `app`
- Android 应用壳
- Receiver / Service
- Xposed entry 与 hook
- migration / transition
- shared `smscode-core` 的装配桥
- 不重新定义本地验证码主链基础设施

### `smscode-core`
- 继续作为验证码主链唯一共享实现来源
- `verification-core` 放共享编排与 helper
- `xposed-core` 放共享 hook infra / system input / fallback policy

## 依赖方向

- `app -> core, runtime, smscode-core:*`
- `core -> runtime, magisk-ui-kit, smscode-core:domain`
- `runtime -> smscode-core:domain, smscode-core:xposed-core`

约束：
- `core` 不得直接依赖 runtime 的 DB/update 实现类
- `runtime` 不得引入 Compose/UI API
- `app` 不得重新放回本地验证码引擎基础设施

## 构建治理

- 统一通过 `build-logic` convention plugin 提供 flavor 和打包规则
- flavor 语义保持不变：
  - `play -> api101`
  - `github -> legacy/api101`
  - `fdroid -> disabled`
- Play 渠道只保留 AAB，GitHub 保留 APK

## 继续优化的方向

1. 增加更多 runtime facade，继续减少 `core/app` 对 runtime 具体实现的感知
2. 视风险决定是否把 runtime 内的旧 `common/data/...` package 进一步语义重命名
3. 继续把测试按模块语义归位，避免 `app` 承载 runtime/core 的测试
