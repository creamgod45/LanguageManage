[English](compatibility.md) | [繁體中文](compatibility.zh.md)

# 插件相容性驗證

## 支援產品

`plugin.xml` 只宣告所有產品共用的 `com.intellij.modules.platform` 依賴，因此 JetBrains Marketplace 會依具備此共用模組的 IntelliJ Platform 產品自動推導相容清單；descriptor 不需要也不應維護寫死的產品名稱。Split mode 的 frontend／backend 模組依賴仍分別保留於各自 content descriptor。

AppCode 不列為目標產品；Marketplace 未列出 AppCode 時，不能透過在 `plugin.xml` 加入產品名稱強制啟用。

LanguageManager `1.5.0` 的插件描述設定如下：

- 最低版本：JetBrains Platform build `253.5`（IntelliJ IDEA 2025.3.5）
- 最高版本：未限制
- 編譯與最低版本測試平台：IntelliJ IDEA 2025.3.5
- JVM bytecode target：Java 21

## 1.4.1 Marketplace 驗證結果

以下 LanguageManager `1.4.1` 結果由 JetBrains Marketplace Compatibility Verification 於 2026-07-15 至 2026-07-16 提供：

| 狀態 | 產品 | 版本 | 日期與時間 | Verifier | 結果 |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-15 09:01 | IDE | 安裝插件後執行 IDE，未發生問題 |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 2026-07-15 08:58 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.2 RC (`262.8665.184`) | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 2026-07-15 08:58 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.4 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.3 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.3 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.2 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2026.1.1 | 2026-07-16 10:26 | Plugin Verifier `1.408` | Compatible |
| Warning | IntelliJ IDEA | 2026.1 | 2026-07-15 09:00 | Plugin Verifier `1.408` | Compatible；使用 77 處 experimental API |
| Warning | PhpStorm | 2026.1 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible；使用 77 處 experimental API |
| Success | IntelliJ IDEA | 2025.3.6 | 2026-07-15 08:58 | Plugin Verifier `1.408` | Compatible |
| Success | PhpStorm | 2025.3.6 | 2026-07-16 10:25 | Plugin Verifier `1.408` | Compatible |

「Compatible」代表 Plugin Verifier 未發現該 IDE 版本的二進位相容性失敗。2026.1 的警告列仍判定相容，但使用了 77 處 experimental API，這些 API 的契約可能在後續 IDE 版本變更。IDE 實際執行驗證與 Plugin Verifier 是不同檢查。

未設定 `until-build` 可讓較新 IDE 嘗試安裝插件，但仍應在每次發布前對最新正式版與 EAP／RC 重新執行 Plugin Verifier 及 IDE 安裝測試。
