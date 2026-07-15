# 插件相容性驗證

LanguageManager `1.4.1` 的插件描述設定如下：

- 最低版本：JetBrains Platform build `253.5`（IntelliJ IDEA 2025.3.5）
- 最高版本：未限制
- 編譯與最低版本測試平台：IntelliJ IDEA 2025.3.5
- JVM bytecode target：Java 21

## Marketplace 驗證結果

以下結果由 JetBrains Marketplace Compatibility Verification 於 2026-07-14 提供：

| 狀態 | 產品 | 版本 | 驗證時間 | Verifier | 結果 |
|---|---|---|---|---|---|
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 17:15 | IDE | 安裝插件後執行 IDE，未發生問題 |
| Success | IntelliJ IDEA | 2026.2 RC (`262.8665.176`) | 17:05 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.4 | 17:05 | Plugin Verifier `1.408` | Compatible |
| 未完成 | IntelliJ IDEA | 2026.1.4 | 17:47 | IDE | Marketplace 顯示 `—`，不列為失敗 |
| Success | IntelliJ IDEA | 2026.1.3 | 17:07 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.2 | 17:08 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2026.1.1 | 17:07 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2025.3.6 | 17:48 | Plugin Verifier `1.408` | Compatible |
| Success | IntelliJ IDEA | 2025.3.5 | 17:49 | Plugin Verifier `1.408` | Compatible |

「Compatible」代表 Plugin Verifier 未發現該版本的二進位或 API 相容性問題。IDE 實際執行驗證與 Plugin Verifier 是不同檢查；表格中的 `—` 代表沒有可用判定，並不代表驗證失敗。

未設定 `until-build` 可讓較新 IDE 嘗試安裝插件，但仍應在每次發布前對最新正式版與 EAP／RC 重新執行 Plugin Verifier 及 IDE 安裝測試。
