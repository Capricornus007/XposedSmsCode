# FCM Cross-Device Sync Configuration Guide

> **Supported Version**: v1 API (Recommended) | **Encryption**: AES-256-CBC End-to-End

## Overview

FCM Sync enables **real-time SMS verification code synchronization** across multiple devices. When one device receives a code, all other devices in the sync group receive it simultaneously.

### Core Features

- 🔐 **End-to-End Encryption**: AES-256-CBC, only devices in the sync group can decrypt
- 🌐 **Zero-Database Architecture**: Completely decentralized, no central server required
- 🔑 **User Full Control**: Sync keys (Sync Group ID) are user-managed
- 📱 **Bidirectional Sync**: Each device can both send and receive codes
- ⚡ **Low Latency**: Powered by Google FCM with millisecond-level delivery

---

## Configuration Steps

### Step 1: Create Firebase Project

1. Visit [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add project"**
3. Enter project name (e.g., `MyPhoneSync`)
4. Complete project creation wizard (Google Analytics optional)

### Step 2: Enable Cloud Messaging

1. Select **Build** → **Cloud Messaging** from left menu
2. For new projects, click **"Get Started"**
3. Confirm FCM service is enabled

### Step 3: Download Service Account JSON

> ⚠️ **Important**: This app uses **FCM v1 API**, which requires a service account JSON file (**NOT** Legacy Server Key)

**Steps**:
1. Click ⚙️ icon (top-left) → **"Project settings"**
2. Switch to **"Service accounts"** tab
3. Click **"Generate new private key"** button
4. Confirm download in the popup
5. Save the JSON file (filename like `your-project-xxxxx-firebase-adminsdk-xxxxx.json`)

⚠️ **Security Alert**: This JSON contains sensitive private keys. Keep it secure and never share or upload to public cloud storage.

---

### Step 4: In-App Configuration

#### Configure Sender Device (Primary Device)

Senders can push codes to the sync group. Recommended for primary phones.

**Steps**:
1. Open **XposedSmsCode** app
2. Switch to **"Sync"** tab
3. Enable **"Enable FCM Sync"** toggle
4. Tap **"Service Account (v1 API)"**
5. **Select the downloaded JSON file**
6. App will show **"Service account saved"** toast
7. Note or copy the **"Sync Group ID"** (this is your encryption key)

#### Configure Receiver Device (Secondary Device)

Receivers only receive synced codes. Recommended for tablets/backup phones.

**Steps**:
1. Open **XposedSmsCode** app
2. Switch to **"Sync"** tab
3. Enable **"Enable FCM Sync"** toggle
4. **Skip** Service Account configuration (no JSON upload needed)
5. Tap **"Sync Group ID"**
6. **Paste the Group ID from primary device**
7. Save

> 💡 **Mode Explanation**:
> - **Upload JSON** → Sender mode (for primary devices)
> - **No JSON** → Receiver-only mode (for secondary devices, shows "Receiver Only" hint)

---

### Step 5: Verify Configuration

1. On **primary device**, send a test SMS or receive a real verification code
2. Check **secondary device** notification tray for synced code
3. If not received, verify:
   - Both devices have identical Group IDs
   - Network connectivity is available
   - FCM Sync toggle is enabled

---

## Security & Privacy

### Encryption Mechanism

- **Key**: Sync Group ID (user-managed)
- **Algorithm**: AES-256-CBC
- **Encrypted Fields**: Code content (`code` and `body` fields)
- **Transport**: Encrypted data transmitted via FCM Topic, Google cannot decrypt

### Security Recommendations

1. **Sync Group ID is the sole encryption key**: Do not share; leakage allows decryption
2. **Rotate Group ID regularly**: Tap **"Generate"** in "Sync Group ID" to refresh
3. **Keep Service Account JSON confidential**: Contains private key, store locally only
4. **Minimize permissions**: Receivers don't need service account, only senders do

---

## FAQ

### Q: Why not use Legacy Server Key?

**A**: Legacy HTTP API is deprecated by Google. v1 API is recommended for higher security (OAuth 2.0 authentication).

### Q: Can multiple devices send simultaneously?

**A**: Yes! Any device with uploaded service account JSON can act as a sender.

### Q: How much data does it consume?

**A**: ~1-2 KB per code, slightly more after encryption. Negligible under normal usage.

### Q: Which Android versions are supported?

**A**: Android 15+ (API 35), same as main app requirement.

### Q: Can I join multiple sync groups?

**A**: No. Each device can only join one sync group at a time. Change Group ID to switch groups.

### Q: What if I lose my Group ID?

**A**: View current ID in "Sync Group ID" on any configured device. Manual backup is recommended.

### Q: What happens if Firebase project is deleted?

**A**: Service account JSON becomes invalid; senders cannot send. Receivers can still receive (old tokens remain valid).

---

## Troubleshooting

### Issue: Secondary device not receiving synced codes

**Solutions**:
1. Confirm both devices have **identical** Group IDs (case-sensitive)
2. Verify secondary device's FCM Sync toggle is enabled
3. Test network connectivity (access `https://fcm.googleapis.com`)
4. Restart app or re-subscribe (disable then re-enable sync)

### Issue: "Failed to read file" after JSON upload

**Solutions**:
1. Confirm selected file is **JSON format** (not image or other types)
2. Check file permissions (Android 11+ requires storage permission)
3. Copy JSON file to Downloads folder and try again

### Issue: "Failed to get access token" on sender device

**Solutions**:
1. Verify service account JSON format (open with text editor)
2. Check phone time accuracy (OAuth relies on timestamps)
3. Confirm network can access Google services

---

## Technical Details

### Architecture Overview

```
[Primary Device] Receives SMS 
    ↓ 
Extract code → AES Encrypt (Group ID) 
    ↓ 
FCM v1 API (OAuth Token) → Topic Broadcast 
    ↓ 
Google FCM Server 
    ↓ 
Devices subscribed to Topic 
    ↓ 
AES Decrypt (Group ID) → Display Code
```

### Message Format

**Before Encryption**:
```json
{
  "type": "sms_sync_encrypted",
  "code": "123456",
  "sender": "+1234567890",
  "body": "Your verification code is 123456"
}
```

**After Encryption (Transport Format)**:
```json
{
  "message": {
    "topic": "group_xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "data": {
      "type": "sms_sync_encrypted",
      "code": "Encrypted_Base64_String",
      "sender": "+1234567890",
      "body": "Encrypted_Base64_String"
    }
  }
}
```

---

## Related Links

- [Firebase Console](https://console.firebase.google.com/)
- [FCM v1 API Documentation](https://firebase.google.com/docs/cloud-messaging/migrate-v1)
- [Project GitHub](https://github.com/magisk317/XposedSmsCode)
- [Issue Tracker](https://github.com/magisk317/XposedSmsCode/issues)

---

**Last Updated**: 2026-02-05  
**Documentation Version**: v1.0.0
