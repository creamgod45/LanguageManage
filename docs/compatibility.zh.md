[English](compatibility.md) | [繁體中文](compatibility.zh.md)

# 插件相容性驗證

## 支援產品

`plugin.xml` 只宣告共用的 `com.intellij.modules.platform` 相依性，因此 JetBrains Marketplace 會依各產品是否提供這個共用模組，自動判斷 IntelliJ Platform 系列產品的相容性；descriptor 不會硬編碼產品名稱清單。Split mode 的 frontend 與 backend 模組相依性仍分別保留在各自的 content descriptor。

AppCode 並未宣告為目標產品。Marketplace 未將它列入支援產品時，不能藉由在 `plugin.xml` 加入產品名稱來強制覆寫。

已核准的 LanguageManager `1.5.3` 與 `1.5.4` 使用以下 descriptor 設定：

- 最低版本：JetBrains Platform build `253.5`（IntelliJ IDEA 2025.3.5）
- 最高版本：不限制
- 編譯與最低版本測試平台：IntelliJ IDEA 2025.3.5
- JVM bytecode target：Java 21

## 1.5.4 進度 API 驗證

2026 年 7 月 23 日 Marketplace 對先前 `1.5.4` 安裝包的報告指出兩個 internal API 使用（`RawProgressReporterHandle` 與 `getReporter()`），以及 11 個來自 `RawProgressReporter` 的 experimental API 使用。該安裝包仍為 binary compatible 且通過 IDE 安裝執行測試，但這些進度 API 不適合作為 Marketplace 發布實作。

目前已改用穩定的 `Task.Backgroundable` 與 `ProgressIndicator` API，保留取消、階段／詳細文字、精確進度比例，以及新載入取消舊工作的行為。修正版 ZIP 已不存在 `RawProgressReporterHandle`、`RawProgressReporter` 或 `reportRawProgress` bytecode 引用。

修正版安裝包的本機 Plugin Verifier `1.408` 結果：

| 狀態 | 產品 | Build | 結果 |
|---|---|---|---|
| Success | IntelliJ IDEA 2025.3.5 | `253.33514.17` | Compatible，沒有 API 使用警告 |
| Success | IntelliJ IDEA 2026.2 | `262.8665.258` | Compatible，沒有 API 使用警告 |

以上涵蓋宣告的最低 build 與目前穩定 build。

## 1.5.4 Marketplace 發布狀態

| 欄位 | 結果 |
|---|---|
| 狀態 | 已核准（Approved） |
| 核准日期 | 2026 年 7 月 23 日 |
| 相容範圍 | `253.5+` |
| Marketplace 安裝包大小 | 938.48 KB |
| 上傳者 | Laifu來福（來福Laifu） |
| 報告當時版本下載次數 | 0 |

## 1.5.4 Marketplace 相容性驗證結果

| 狀態 | 產品 | 版本 | 日期與時間 | 驗證器 | 結果 |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 | 2026-07-23 11:29 | IDE | 安裝插件後執行 IDE，未發生問題 |
| Success | IntelliJ IDEA | 2026.2 | 2026-07-23 11:26 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-23 11:33 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 RC (`262.8665.184`) | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 2026-07-23 11:25 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.4 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.3 | 2026-07-23 11:31 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.3 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 2026-07-23 11:31 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.2 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 2026-07-23 11:31 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.1 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |
| Warning | IntelliJ IDEA | 2026.1 | 2026-07-23 11:32 | Plugin Verifier `1.408` | Compatible，使用 101 個 experimental API |
| Warning | PhpStorm | 2026.1 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible，使用 101 個 experimental API |
| Success | IntelliJ IDEA | 2025.3.6 | 2026-07-23 11:25 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2025.3.6 | 2026-07-23 11:36 | Plugin Verifier `1.408` | Compatible |

已發布安裝包沒有 internal API 警告。只有未套用修補版的 2026.1 產品回報 101 個 experimental API 使用；其餘列出的 patch、RC 與 stable 產品版本皆為 Compatible，且沒有 API 警告。

## 1.5.3 Marketplace 發布狀態

| 欄位 | 結果 |
|---|---|
| 狀態 | 已核准（Approved） |
| 核准日期 | 2026 年 7 月 21 日 |
| 相容範圍 | `253.5+` |
| Marketplace 安裝包大小 | 791.4 KB |
| 上傳者 | Laifu來福（來福Laifu） |
| 報告當時版本下載次數 | 0 |

## 1.5.3 Marketplace 相容性驗證結果

JetBrains Marketplace Compatibility Verification 於 2026 年 7 月 21 日回報以下 LanguageManager `1.5.3` 結果：

| 狀態 | 產品 | 版本 | 日期與時間 | 驗證器 | 結果 |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 | 2026-07-21 12:40 | IDE | 安裝插件後執行 IDE，未發生問題 |
| Success | IntelliJ IDEA | 2026.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 RC (`262.8665.184`) | 2026-07-21 12:37 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 2026-07-21 12:35 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.4 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.3 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.3 | 2026-07-21 12:37 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.2 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.1 | 2026-07-21 12:36 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2025.3.6 | 2026-07-21 12:35 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2025.3.6 | 2026-07-21 12:37 | Plugin Verifier `1.408` | Compatible |

`Compatible` 表示 Plugin Verifier 未發現該 IDE 版本的 binary compatibility failure。IDE 那一列是實際安裝與執行檢查，其餘列則是 Plugin Verifier 的靜態驗證，兩者屬於不同檢查。

未設定 `until-build` 可讓較新的 IDE 版本嘗試安裝，但每次發布仍應以 Plugin Verifier 與實際安裝執行，驗證最新穩定版及相關 EAP／RC 版本。
