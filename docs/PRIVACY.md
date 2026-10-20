# Privacy Policy / 隐私政策

**Effective Date / 生效日期:** 2026-10-20

[English Version](#privacy-policy-for-xposedsmscode) | [中文版本](#xposedsmscode-隐私政策)

---

## Privacy Policy for XposedSmsCode

### 1. Introduction
Welcome to XposedSmsCode ("we," "our," or "us"). We are committed to protecting your privacy and ensuring you understand how we handle your data. This Privacy Policy explains what information we collect, why we collect it, and how we safeguard it.

### 2. Information We Collect and Use

#### 2.1 Verification Code Processing
Our application's core functionality is to extract verification codes from SMS messages in Xposed environments, then process them locally according to your settings (for example, record, intercept, copy, and auto-input behaviors). Depending on distribution/channel and enabled features, we may request the following permissions:

*   **POST_NOTIFICATIONS:** Used to display verification-code notifications.
*   **QUERY_ALL_PACKAGES / PACKAGE_USAGE_STATS:** Used for app list display and app usage based sorting in settings.
*   **REQUEST_INSTALL_PACKAGES:** Used for the in-app update installation flow.

**Usage:**
*   We STRICTLY use these permissions only for the stated features.
*   We DO NOT collect, store, or transmit your personal conversations or contacts to our servers.
*   SMS content parsing and matching are performed locally on your device.

#### 2.2 Local Processing and Network Access
*   All SMS content parsing and matching is performed locally. No SMS content is uploaded, and there is no service that stores your message content.
*   Network access is used for update checks/downloads and related release metadata retrieval.
*   Where module activation is enabled, the app also communicates with an authorization service operated by this project in order to issue, confirm, and renew a device-bound license. These requests carry technical metadata only:
    *   a device identifier that is produced by applying a one-way hash to a hardware-level identifier on your device, plus the license identifier previously issued to this device;
    *   the device model or marketing name, the app version, and the operating-system version and API level;
    *   the release channel of the build you are using;
    *   public-key material generated inside the app and a signature derived from it, which is what binds a license to this device;
    *   the time at which the request is made.
*   Those requests never contain SMS content, extracted verification codes, message logs, contacts, or notification data.

### 3. Technical Diagnostics
Diagnostic and log data produced by the application is generated and kept locally. The authorization service records licensing events only — issuance, renewal, re-binding, revocation, and failures of these steps — and uses the device and app metadata described in section 2.2 in aggregate form to keep that service within its capacity limits and to understand which device and system versions it has to support. It is not used to trace what an individual device or user does, and the application contains no analytics or advertising development kit.

### 4. Data Retention
*   **Local Storage:** Extracted records are stored locally in a database on your device (`/data/data/com.github.tianma8023.xposed.smscode/databases/`). License state and the device keys described above are kept in the app's private storage and are excluded from backup and restore.
*   **Authorization Service:** For each activated device the service keeps the license identifier, the hashed device identifier, the device and app metadata listed in section 2.2, and the timestamps of issuance, renewal and revocation checks. A record for a licensed device is kept while that license is in use. Once a device is unbound or its license is revoked, an automatic sweep removes the record no later than 90 days after the revocation. Verification requests that were never completed are removed within 1 day after they expire, and server-side event logs are removed on a rolling 90-day basis.
*   **Deletion:** You can clear local data at any time by uninstalling the application or using the "Clear Data" function in the app settings. Unbinding the device from the activation service starts the deletion path described above, which completes within 90 days; to have a record removed sooner, contact us using the address in section 9.

### 5. Data Sharing and Disclosure
We **DO NOT** sell, trade, or otherwise transfer your personally identifiable information to outside parties.
*   We do not integrate analytics or advertising development kits, and we do not have a service that collects your SMS data.
*   Verification code content is processed locally on device and is not forwarded by this app to third-party services.
*   If you choose to obtain a license through a third-party messaging service, that service receives the verification reference the app displays, within its own scope and under its own privacy terms. This step is optional and started by you.

### 6. Security
We implement a variety of security measures to maintain the safety of your personal information.
*   The app operates ensuring that sensitive permissions are used solely for the stated core purpose.
*   Network communication uses standard HTTPS encryption, and licenses are issued as signed tokens that are validated against the device keys generated in the app.

### 7. Children's Privacy
Our Service does not address anyone under the age of 13. We do not knowingly collect personally identifiable information from children under 13.

### 8. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Thus, you are advised to review this page periodically for any changes. We will notify you of any changes by posting the new Privacy Policy on this page.

### 9. Contact Us
If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us at:

**Email:** play@usdt.edu.kg

---

## XposedSmsCode 隐私政策

### 1. 简介
欢迎使用 XposedSmsCode（以下简称“我们”）。我们致力于保护您的隐私，并确保您了解我们如何处理您的数据。本隐私政策解释了我们收集哪些信息、为什么收集以及如何保护这些信息。

### 2. 我们收集和使用的信息

#### 2.1 验证码处理
我们应用的核心功能是在 Xposed 环境中提取短信验证码，并按您的本地设置进行处理（例如记录、拦截、复制、自动输入）。根据发行渠道与功能启用情况，应用可能申请以下权限：

*   **通知权限 (POST_NOTIFICATIONS):** 用于展示验证码通知。
*   **应用查询/使用情况权限 (QUERY_ALL_PACKAGES / PACKAGE_USAGE_STATS):** 用于设置页应用列表展示和按使用频率排序。
*   **安装未知应用权限 (REQUEST_INSTALL_PACKAGES):** 用于应用内更新安装流程。

**使用说明：**
*   我们**仅**将这些权限用于声明功能。
*   我们**不会**收集、存储或传输您的个人对话或联系人到我们的服务器。
*   所有针对短信内容的解析与匹配均在您的设备本地进行。

#### 2.2 本地处理与网络访问
*   短信内容的解析与匹配全部在本地完成，不会上传短信内容，也不存在存储您消息内容的服务。
*   网络访问用于版本检查、更新下载及相关发布信息获取。
*   在启用模块激活的情况下，应用还会与本项目运营的授权服务通信，以签发、确认和续期与设备绑定的授权凭证。此类请求只包含技术性元数据：
    *   由您设备上的硬件级标识经单向哈希得到的设备标识，以及此前签发给该设备的授权凭证标识；
    *   设备型号或商品名、应用版本、操作系统版本与 API 级别；
    *   您所使用构建版本的发布渠道；
    *   在应用内生成的公钥材料，以及由该密钥派生的签名——授权凭证正是靠它与本设备绑定；
    *   请求发生的时间。
*   上述请求不包含短信内容、提取到的验证码、消息记录、通讯录或通知数据。

### 3. 技术诊断数据
应用产生的诊断与日志数据仅在本地生成和保存。授权服务只记录授权环节的事件——签发、续期、重新绑定、撤销，以及这些环节的失败——并以汇总形式使用第 2.2 节所述设备与应用元数据，用于让该服务维持在容量预算内、并了解需要支持哪些设备与系统版本。这些数据不用于追踪单个设备或用户的行为，应用内也不含统计或广告开发组件。

### 4. 数据保留
*   **本地存储：** 提取的记录存储在您设备本地的数据库中 (`/data/data/com.github.tianma8023.xposed.smscode/databases/`)。授权状态与上文提到的设备密钥保存在应用私有目录中，并且不参与备份与恢复。
*   **授权服务：** 对每台已激活的设备，服务端保留授权凭证标识、哈希后的设备标识、第 2.2 节所列的设备与应用元数据，以及签发、续期和校验撤销的时间点。仍在使用中的授权，其记录会随该授权一并保留。设备一旦解绑或授权被撤销，自动清理任务会在撤销起算的不迟 90 天内删除该记录。未完成的验证请求在其失效后 1 天内删除；服务端事件日志按 90 天滚动删除。
*   **删除：** 您可以通过卸载应用程序或使用应用设置中的“清除数据”功能随时清除本地数据。在激活服务中解绑设备即启动上文的删除流程，最长 90 天内完成；如需更早删除，请通过第 9 节的联系方式与我们联络。

### 5. 数据共享与披露
我们**不会**向外界出售、交易或以其他方式转移您的个人身份信息。
*   我们没有集成统计或广告类开发组件，也不存在收集您短信数据的服务。
*   验证码内容在本地处理，本应用不会将其转发到第三方服务。
*   如果您选择通过第三方即时通讯服务获取授权凭证，该服务会在其自身的服务范围和隐私条款下收到应用展示的验证凭据。这一步完全可选，并由您主动发起。

### 6. 安全性
我们实施了多种安全措施来维护您个人信息的安全。
*   应用程序确保敏感权限仅用于声明的核心目的。
*   网络通信使用标准 HTTPS 加密；授权凭证以签名令牌形式签发，并通过应用内生成的设备密钥进行校验。

### 7. 儿童隐私
我们的服务不针对 13 岁以下的任何人。我们不会有意收集 13 岁以下儿童的个人身份信息。

### 8. 本隐私政策的更改
我们可能会不时更新我们的隐私政策。因此，建议您定期查看本页面以了解任何更改。我们将通过在此页面上发布新的隐私政策来通知您任何更改。

### 9. 联系我们
如果您对我们的隐私政策有任何疑问或建议，请随时通过以下方式联系我们：

**邮箱：** play@usdt.edu.kg
