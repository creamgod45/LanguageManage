# LanguageManager 功能總覽

> 給開發者的簡短精確功能索引。版本 **1.5.5**。詳細操作見 [`user_manual_book.zh.md`](user_manual_book.zh.md)，完整需求見 [`需求.md`](需求.md)，工程規範見 [`../AGENTS.md`](../AGENTS.md)。

## 一句話定位

JetBrains IDE 的多語系檔案管理插件（split-mode），只管理使用者在「方案（scheme）」中明確選取的語言檔，永不自動納管或改寫其他專案檔。UI 與診斷支援 7 種語言：英文、`zh_TW`、`zh_CN`、`ja`、`ko`、`es`、`th`。

## 模組邊界

| 模組 | 執行位置 | 職責 |
| --- | --- | --- |
| `shared` | frontend + backend | 可序列化 DTO、`@Rpc` 契約、純搜尋/JOIN/分頁邏輯 |
| `frontend` | IDE 前端 | Tool Window、Swing 表格、對話框、Diff、RPC repository（不直接碰檔案） |
| `backend` | IDE 後端 | 方案狀態、檔案 IO、parser、cache、品質分析、使用率掃描、RPC 實作 |

- IO 走 `Dispatchers.IO`；同方案 mutation 用 mutex 序列化。
- UI 狀態透過 RPC `Flow`／`StateFlow` 推送，無輪詢。

## 支援格式

JSON、YAML/YML、Laravel PHP 靜態 `return [...]`／`return array(...)`、JetBrains/Java ResourceBundle Properties。
PHP **只 parse 不執行**：只接受選填的 `declare(strict_types=1);` + 靜態陣列，支援類別子目錄（`en/components/pagination.php`）、字串串接、heredoc/nowdoc；拒絕變數、函式呼叫、include、eval 與可執行運算式。

## 核心功能

### 方案（Scheme）
- 從明確選取的檔案或一個以上資料夾建立隔離方案；資料夾模式先 parse、預覽辨識結果（格式/locale/namespace/筆數/錯誤）再確認。
- 方案間完全隔離，mutation 只寫入該方案檔案。
- 可從 Tool Window dropdown 以 JSON 匯入/匯出可攜方案設定（路徑盡量轉相對，每檔含 parser 與安全預覽）。

### 翻譯表
- 以 `namespace + key` JOIN 成一列，每個 locale 一欄，每頁上限 100 列。
- 模糊/精確搜尋、locale 篩選、缺翻譯與零使用率篩選、分頁。
- 一張捲動表單編輯所有 locale 值並存為單一驗證批次；批量刪除、跨 locale 改 key、複製/貼上 cell、IDE 原生 Find in Files。
- **Rename Key** 可選同步已記錄的原始碼使用位置，透過可編輯 code Diff 後才寫檔。
- 從既有 locale 建立完整新 locale（如 `en/*.php` → `es/*.php`），附 ISO/BCP 47 建議 popup 與選填語言備註。

### AI 批量翻譯
- 對最多 100 列，透過 OpenAI-compatible 或 Anthropic Claude endpoint 翻譯。
- 來源文字預設 `en`（否則用 key）、可編輯；多目標 locale 各自獨立可編輯欄，最後合併成一份檔案 Diff。
- 只有 **Apply** 才寫檔；**Give AI More Feedback** 帶著編輯後來源、審核建議與意見進入下一輪。
- API token 存於 JetBrains PasswordSafe；Temperature 預設省略。

### 使用率掃描 / Usage
- 多組 Regex 累計出現次數（同行重複計數，同位置重疊去重）。
- 內建各主流框架 Regex 建議（PHP 框架、Spring/Java/Kotlin、ResourceBundle、IntelliJ Platform），另有 opt-in Laravel「key only」預設。
- 掃描設定以方案隔離：安全 base path、可編輯排除清單（每方案上限 1,000，支援逗號/換行批量、Project 樹右鍵加入資料夾）。
- 雙擊某列 Usage cell 才啟用 **Usage Locations** 分頁；位置記錄僅存 backend，前端每次只取該 key 當頁（≤100），開啟時才 lazy 解析行/列並快取。

### 問題偵測
- 偵測 parser 錯誤、空值、重複 key、重複值、缺 locale、疑似未使用 key。
- 重複值與疑似未使用建議可於設定隱藏。
- 單列與批量操作；可自動修改的操作先出 Diff 預覽。

### Diff / 安全寫入
- 修復或刪除前顯示 IDE 雙欄 Diff。
- 套用前以 **SHA-256** 比對，避免預覽後外部變更被靜默覆蓋。
- 寫檔用 temporary file + atomic move；輸出依各格式正確跳脫；錯誤訊息移除危險控制字元並限長。
- 檔案來源驗證：只接受一般本機檔案，拒絕 URI、`file:`、Windows device path、`GLOBALROOT`、控制字元。

### 效能 / 快取
- 記憶體 + `.idea/language-manager/` 快取解析結果。
- 每方案 parser 記憶體有上限：每檔大小、總內容、每檔筆數、總筆數（大小先於讀取檢查，筆數/巢狀於解析時強制）。
- 方案切換與手動重載為可取消的 JetBrains 背景任務；較新請求會取代舊載入，generation guard 阻擋 stale 結果。
- 背景載入顯示精確動態步數：preparation + 各語言檔 + 建表 + 各原始碼檔 + 分析 + 寫快取。

## 開發須遵守（摘自 AGENTS.md）
- 所有使用者可見文字必進 resource bundle，7 套字典同步新增相同 key，並更新 bundle parity tests。
- 新功能加入與風險相稱的測試（parser、安全、RPC DTO、搜尋、字典不可只靠手動驗證）。
- 交付前至少跑 `test` 與 `buildPlugin`，確認 ZIP 含三個 module 與所有語言 bundle。
- 功能更新同步 `CHANGELOG.md`；架構/API/操作改變同步 README 與 `docs/`。
