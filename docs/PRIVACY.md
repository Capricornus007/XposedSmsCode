# Privacy Policy / 隐私政策

**Effective Date / 生效日期:** 2026-01-29

[English Version](#privacy-policy-for-xposedsmscode) | [中文版本](#xposedsmscode-隐私政策)

---

## Privacy Policy for XposedSmsCode

### 1. Introduction
Welcome to XposedSmsCode ("we," "our," or "us"). We are committed to protecting your privacy and ensuring you understand how we handle your data. This Privacy Policy explains what information we collect, why we collect it, and how we safeguard it.

### 2. Information We Collect and Use

#### 2.1 SMS and Call Log Data
Our application's core functionality is to extract verification codes from SMS messages in Xposed environments and forward them to destinations you configure (e.g., WeChat, Telegram, Mail, etc.). Depending on distribution/channel and feature usage, we may request the following sensitive permissions:

*   **SEND_SMS (GitHub distribution only, optional):** Used only when you enable the SMS forwarding channel to send messages to phone numbers you configured.
*   **READ_CALL_LOG:** Used to support verification code extraction from incoming phone calls (if applicable/enabled by user).

**Usage:**
*   We STRICTLY only use these permissions for stated functionality (e.g., extracting verification codes or sending SMS to targets explicitly configured by you).
*   We DO NOT collect, store, or transmit your personal conversations, contacts, or other non-verification-related content to our servers.
*   All processing of SMS content happens locally on your device.

#### 2.2 Data Forwarding (User Configured)
You may choose to forward extracted verification codes to third-party services (such as Bark, DingTalk, Telegram, Email, etc.) via Webhooks.
*   **Control:** YOU define the destination URLs and configurations.
*   **Transmission:** When you configure a forwarder, the extracted verification code (and sender information) is sent directly from your device to the service endpoint you specified. We do not intercept or route this data through our own servers.

### 3. Data Retention
*   **Local Storage:** Extracted records are stored locally in a database on your device (`/data/data/com.github.tianma8023.xposed.smscode/databases/`).
*   **Deletion:** You can clear this data at any time by uninstalling the application or using the "Clear Data" function in the app settings.

### 4. Data Sharing and Disclosure
We **DO NOT** sell, trade, or otherwise transfer your personally identifiable information to outside parties.
*   We do not have a backend server that collects your SMS data.
*   Data is only shared with the destination services **you explicitly configure** in the app settings.

### 5. Security
We implement a variety of security measures to maintain the safety of your personal information.
*   The app operates ensuring that sensitive permissions are used solely for the stated core purpose.
*   Network communication for forwarding is performed using standard HTTPS encryption (depending on your configured endpoint).

### 6. Children's Privacy
Our Service does not address anyone under the age of 13. We do not knowingly collect personally identifiable information from children under 13.

### 7. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Thus, you are advised to review this page periodically for any changes. We will notify you of any changes by posting the new Privacy Policy on this page.

### 8. Contact Us
If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us at:

**Email:** play@usdt.edu.kg

---

## XposedSmsCode 隐私政策

### 1. 简介
欢迎使用 XposedSmsCode（以下简称“我们”）。我们致力于保护您的隐私，并确保您了解我们如何处理您的数据。本隐私政策解释了我们收集哪些信息、为什么收集以及如何保护这些信息。

### 2. 我们收集和使用的信息

#### 2.1 短信和通话记录数据
我们应用的核心功能是在 Xposed 环境中提取短信验证码，并将其转发到您配置的目标（例如微信、Telegram、邮件等）。根据发行渠道与功能启用情况，应用可能申请以下敏感权限：

*   **发送短信 (SEND_SMS，仅 GitHub 发行版、可选):** 仅在您启用“短信通道转发到手机号”时使用。
*   **读取通话记录 (READ_CALL_LOG):** 用于支持从来电中提取验证码（如果用户启用适用功能）。

**使用说明：**
*   我们**仅**将这些权限用于声明功能（例如提取验证码，或向您明确配置的目标号码发送短信）。
*   我们**不会**收集、存储或传输您的个人对话、联系人或其他非验证码相关内容到我们的服务器。
*   所有针对短信内容的处理均在您的设备本地进行。

#### 2.2 数据转发（由用户配置）
您可以选择通过 Webhook 将提取的验证码转发到第三方服务（如 Bark、钉钉、Telegram、电子邮件等）。
*   **控制权：** 目标 URL 和配置完全由**您**定义。
*   **传输：** 当您配置转发器时，提取的验证码（及发送者信息）将直接从您的设备发送到您指定的服务端点。我们不会拦截或通过我们自己的服务器路由此数据。

### 3. 数据保留
*   **本地存储：** 提取的记录存储在您设备本地的数据库中 (`/data/data/com.github.tianma8023.xposed.smscode/databases/`)。
*   **删除：** 您可以通过卸载应用程序或使用应用设置中的“清除数据”功能随时清除此数据。

### 4. 数据共享与披露
我们**不会**向外界出售、交易或以其他方式转移您的个人身份信息。
*   我们没有用于收集您短信数据的后端服务器。
*   数据仅与您在应用设置中**明确配置**的目标服务共享。

### 5. 安全性
我们实施了多种安全措施来维护您个人信息的安全。
*   应用程序确保敏感权限仅用于声明的核心目的。
*   转发的网络通信使用标准的 HTTPS 加密（取决于您配置的端点）。

### 6. 儿童隐私
我们的服务不针对 13 岁以下的任何人。我们不会有意收集 13 岁以下儿童的个人身份信息。

### 7. 本隐私政策的更改
我们可能会不时更新我们的隐私政策。因此，建议您定期查看本页面以了解任何更改。我们将通过在此页面上发布新的隐私政策来通知您任何更改。

### 8. 联系我们
如果您对我们的隐私政策有任何疑问或建议，请随时通过以下方式联系我们：

**邮箱：** play@usdt.edu.kg
