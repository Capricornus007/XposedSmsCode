# FCM 跨设备同步配置指南

> **适用版本**: v1 API (推荐) | **加密方式**: AES-256-CBC 端到端加密

## 概述

FCM Sync 允许您在多台设备之间**实时同步验证码短信**。当一台设备收到验证码时，其他设备也会同步接收。

### 核心特性

- 🔐 **端到端加密**: 使用 AES-256-CBC，仅同步组内设备可解密
- 🌐 **零数据库架构**: 无需中央服务器，完全去中心化
- 🔑 **用户完全控制**: 同步密钥（Sync Group ID）由用户自行管理
- 📱 **双向同步**: 每台设备既可发送也可接收验证码
- ⚡ **低延迟**: 基于 Google FCM，毫秒级送达

---

## 配置步骤

### 第一步：创建 Firebase 项目

1. 访问 [Firebase Console](https://console.firebase.google.com/)
2. 点击 **"添加项目"**
3. 输入项目名称（例如：`MyPhoneSync`）
4. 完成项目创建向导（Google Analytics 可选）

### 第二步：启用 Cloud Messaging

1. 在左侧菜单选择 **构建** → **Cloud Messaging**
2. 如果是新项目，点击 **"开始使用"**
3. 确认 FCM 服务已启用

### 第三步：下载服务账号 JSON

> ⚠️ **重要**: 本应用使用 **FCM v1 API**，需要服务账号 JSON 文件（**不是** Legacy Server Key）

**获取步骤**:
1. 点击左上角 ⚙️ 图标 → **"项目设置"**
2. 切换到 **"服务账号"** 标签页
3. 点击 **"生成新的私钥"** 按钮
4. 在弹窗中确认下载
5. 保存 JSON 文件（文件名类似 `your-project-xxxxx-firebase-adminsdk-xxxxx.json`）

⚠️ **安全提示**: 此 JSON 包含敏感私钥，请妥善保管，勿分享给他人或上传到公共云存储。

---

### 第四步：应用内配置

#### 配置发送端设备（主设备）

发送端可以发送验证码到同步组，推荐在主手机上配置。

**步骤**:
1. 打开 **XposedSmsCode** 应用
2. 切换到 **"Sync"** 标签页
3. 启用 **"Enable FCM Sync"** 开关
4. 点击 **"Service Account (v1 API)"**
5. **选择刚才下载的 JSON 文件**
6. 应用会显示提示 **"服务账号已保存"**
7. 记下或复制 **"Sync Group ID"**（这是加密密钥）

#### 配置接收端设备（次设备）

接收端仅接收同步的验证码，推荐在平板/备用手机上配置。

**步骤**:
1. 打开 **XposedSmsCode** 应用
2. 切换到 **"Sync"** 标签页
3. 启用 **"Enable FCM Sync"** 开关
4. **跳过** Service Account 配置（无需上传 JSON 文件）
5. 点击 **"Sync Group ID"**
6. **粘贴主设备的 Group ID**
7. 点击保存

> 💡 **模式说明**:
> - **上传 JSON** → 可发送模式（适合主设备）
> - **不上传 JSON** → 仅接收模式（适合次设备，显示 "Receiver Only" 提示）

---

### 第五步：验证配置

1. 在 **主设备** 发送测试短信或接收真实验证码
2. 检查 **次设备** 通知栏，应显示同步的验证码
3. 如果未收到，检查:
   - 两台设备的 Group ID 是否一致
   - 网络连接是否正常
   - FCM 同步开关是否启用

---

## 安全与隐私

### 加密机制

- **密钥**: Sync Group ID（用户自行管理）
- **算法**: AES-256-CBC
- **加密范围**: 验证码内容（`code` 和 `body` 字段）
- **传输**: 加密后通过 FCM Topic 传输，Google 无法解密

### 安全建议

1. **Sync Group ID 是唯一密钥**: 泄露后他人可解密您的验证码，请勿分享
2. **定期更换 Group ID**: 在 "Sync Group ID" 中点击 **"生成"** 可刷新密钥
3. **服务账号 JSON 保密**: 包含私钥，仅存储在本地，勿上传云端
4. **权限最小化**: 接收端无需服务账号，仅主设备需要

---

## 常见问题

### Q: 为什么不用 Legacy Server Key？

**A**: Legacy HTTP API 已被 Google 官方弃用，v1 API 是推荐方案，安全性更高（OAuth 2.0 认证）。

### Q: 可以多台设备同时发送吗？

**A**: 可以！每台设备只要上传了服务账号 JSON，都可以作为发送端。

### Q: 流量消耗如何？

**A**: 每条验证码约 1-2 KB，加密后略有增加，正常使用下流量可忽略不计。

### Q: 支持哪些 Android 版本？

**A**: Android 15+ (API 35)，与主应用要求一致。

### Q: 多个 Group ID 可以共存吗？

**A**: 不可以。每台设备只能加入一个同步组，需切换组时请先更改 Group ID。

### Q: Group ID 丢失怎么办？

**A**: 在任一已配置设备的 "Sync Group ID" 中可查看当前 ID，建议手动备份。

### Q: Firebase 项目删除了会怎样？

**A**: 服务账号 JSON 会失效，发送端无法发送。但接收端仍可正常接收（旧 Token 仍有效）。

---

## 故障排查

### 问题：次设备未收到同步验证码

**解决步骤**:
1. 确认两台设备 Group ID **完全一致**（区分大小写）
2. 检查次设备的 FCM 同步开关是否启用
3. 测试网络连接（访问 `https://fcm.googleapis.com` 确认可达）
4. 重启应用或重新订阅（关闭后重新启用同步）

### 问题：上传 JSON 后提示 "Failed to read file"

**解决步骤**:
1. 确认选择的是 **JSON 文件**（不是图片或其他格式）
2. 检查文件权限（Android 11+ 需存储权限）
3. 尝试将 JSON 文件复制到 Downloads 目录后重新选择

### 问题：主设备显示 "Failed to get access token"

**解决步骤**:
1. 确认服务账号 JSON 格式正确（可用文本编辑器打开验证）
2. 检查手机时间是否准确（OAuth 依赖时间戳）
3. 确认网络可访问 Google 服务

---

## 技术细节

### 架构概览

```
[主设备] 收到短信 
    ↓ 
提取验证码 → AES 加密(Group ID) 
    ↓ 
FCM v1 API (OAuth Token) → Topic 广播 
    ↓ 
Google FCM 服务器 
    ↓ 
订阅该 Topic 的设备 
    ↓ 
AES 解密(Group ID) → 显示验证码
```

### 消息格式

**加密前**:
```json
{
  "type": "sms_sync_encrypted",
  "code": "123456",
  "sender": "+86123456789",
  "body": "Your verification code is 123456"
}
```

**加密后（传输格式）**:
```json
{
  "message": {
    "topic": "group_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "data": {
      "type": "sms_sync_encrypted",
      "code": "Encrypted_Base64_String",
      "sender": "+86123456789",
      "body": "Encrypted_Base64_String"
    }
  }
}
```

---

## 相关链接

- [Firebase Console](https://console.firebase.google.com/)
- [FCM v1 API 文档](https://firebase.google.com/docs/cloud-messaging/migrate-v1)
- [项目 GitHub](https://github.com/magisk317/XposedSmsCode)
- [问题反馈](https://github.com/magisk317/XposedSmsCode/issues)

---

**最后更新**: 2026-02-05  
**文档版本**: v1.0.0
