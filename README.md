# Language Manager

Language Manager 是支援 JetBrains IDE split mode 的在地化檔案管理插件。它只會讀取使用者在方案中明確選擇的檔案，不會自動掃描或列管語言檔。

## 功能

- 讀取與安全寫回 JSON、YAML/YML、Laravel `return [...]` PHP 語言檔。
- 建立彼此獨立的管理方案；方案與解析結果快取於 `.idea/language-manager/`。
- 表格新增、編輯、單筆或批量刪除，以及跨語言 key 改名。
- 模糊搜尋、精準搜尋與語言篩選。
- parser 錯誤隔離與顯示，不執行 PHP，也不解析 URI、LDAP 或 Windows 裝置路徑。
- 顯示空值、重複鍵、重複值、缺少語言及可能未使用的翻譯。
- 將可解析檔案正規化，並以 key 修復空白 value。
- 插件介面與診斷支援英文、繁體中文、簡體中文、日文及韓文，並跟隨 IDE 顯示語言。

## 使用方式

1. 安裝插件後，從右側工具視窗開啟 **Language Manager**。
2. 點選「新增方案」，明確選擇要列管的 JSON、YAML 或 PHP 語言檔。
3. 輸入方案名稱。每個方案只操作自己的檔案清單。
4. 使用上方搜尋與語言條件篩選表格，或執行新增、編輯、批量刪除與 key 改名。
5. 在「問題與建議」頁籤檢查格式與翻譯品質。

## Laravel 慣例

- `lang/en.json` 會識別為語言 `en`，namespace 留空。
- `lang/en/messages.php` 會識別為語言 `en`、namespace `messages`。
- 巢狀 JSON/YAML/PHP array 在表格中以點號 key 顯示，寫回時會恢復巢狀結構。

## 開發與測試

本專案沿用 JetBrains 官方 modular plugin 範例的 `shared`、`frontend`、`backend` 分層與 RPC 寫法。

```powershell
$env:JAVA_HOME='C:\Program Files\JetBrains\PhpStorm 2026.1\jbr'
.\gradlew.bat test
.\gradlew.bat verifyPlugin
```
