# Privacy Policy / 隱私政策

**Effective Date / 生效日期：** 2026-09-12

[English](#privacy-policy-for-xposedsmscode-capricornus007-fork) | [中文](#xposedsmscode-capricornus007-fork-隱私政策)

---

## Privacy Policy for XposedSmsCode (Capricornus007 fork)

### 1. Introduction
This fork of XposedSmsCode ("the app") is maintained by [Capricornus007](https://github.com/Capricornus007/xposedsmscode). The app's core functionality runs entirely **on your device**: it reads incoming SMS messages in Xposed environments, extracts verification codes locally, and processes them according to your own settings (record, intercept, copy, auto-input, etc.).

### 2. What the App Does NOT Do
- It does **not** collect, upload, or share your SMS contents, verification codes, contacts, or any personal data.
- It does **not** include analytics, tracking, advertising, or telemetry.
- It does **not** contain any licensing, activation, or entitlement checks; after installation (and a reboot with the module enabled in LSPosed), it works out of the box.
- It does **not** connect to any third-party server. The only network requests it may make are the ones you explicitly configure or trigger: the GitHub update check against this repository, and downloads of files you choose to fetch.

### 3. Permissions
- **POST_NOTIFICATIONS:** show verification-code notifications.
- **QUERY_ALL_PACKAGES / PACKAGE_USAGE_STATS:** app list display and usage-based sorting in settings.
- **RECEIVE_SMS / READ_SMS (hooked in target apps):** the hook runs inside the SMS-handling components; the app itself does not request these.
- **Accessibility service (optional):** auto-input of verification codes, only when you enable it.

### 4. Data Storage
All data (preferences, code records, rules) stays in the app's private storage on your device. Nothing is transmitted anywhere except at your explicit request. Uninstalling the app removes everything.

### 5. Contact
Open an issue at <https://github.com/Capricornus007/xposedsmscode/issues> for any privacy question.

---

## XposedSmsCode (Capricornus007 fork) 隱私政策

### 1. 簡介
本 XposedSmsCode 分支由 [Capricornus007](https://github.com/Capricornus007/xposedsmscode) 維護。核心功能完全在**你的裝置上**執行：讀取收到的簡訊、在本機提取驗證碼，並依你自己的設定處理（記錄、攔截、複製、自動輸入等）。

### 2. 本應用不做的事
- 不收集、不上傳、不分享你的簡訊內容、驗證碼、聯絡人或任何個人資料。
- 不包含任何統計、追蹤、廣告或遙測。
- 不包含任何授權、啟用或權益驗證機制；安裝後（於 LSPosed 啟用模組並重啟）即可直接使用。
- 不連接任何第三方伺服器。唯一的網路請求是你明確配置或觸發的：針對本倉庫的 GitHub 更新檢查，以及你主動選擇下載的檔案。

### 3. 權限
- **POST_NOTIFICATIONS：** 顯示驗證碼通知。
- **QUERY_ALL_PACKAGES / PACKAGE_USAGE_STATS：** 設定中的應用清單顯示與使用狀態排序。
- **RECEIVE_SMS / READ_SMS（在目標應用內 hook）：** hook 運行於簡訊處理組件內，本應用自身不請求這些權限。
- **無障礙服務（可選）：** 僅在你啟用時用於自動填入驗證碼。

### 4. 資料儲存
所有資料（偏好、記錄、規則）僅儲存在你裝置上應用的私有儲存空間，除你明確請求外不會傳輸到任何地方。解除安裝即全部移除。

### 5. 聯絡方式
任何隱私問題請到 <https://github.com/Capricornus007/xposedsmscode/issues> 提出。

### 6. 原始專案致謝
本分支基於 tianma8023/XposedSmsCode 與 magisk317 的重構成果，感謝原作與社群貢獻。
