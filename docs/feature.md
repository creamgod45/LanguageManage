# LanguageManager 功能總覽

> 給開發者的簡短精確功能索引。版本 **1.5.6**。詳細操作見 [`user_manual_book.zh.md`](user_manual_book.zh.md)，完整需求見 [`需求.md`](需求.md)，工程規範見 [`../AGENTS.md`](../AGENTS.md)。

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
- 從明確選取的檔案或一個以上資料夾建立隔離方案；資料夾模式先 parse、預覽辨識結果（格式/locale/namespace/筆數/錯誤），並由使用者在獨立欄位確認或修改方案名稱後才建立。
- 目前方案可從「方案設定」重新命名；改名不改變方案 ID、列管檔案、快取歸屬或方案隔離。
- 方案間完全隔離，mutation 只寫入該方案檔案。
- 可從 Tool Window dropdown 以 JSON 匯入/匯出可攜方案設定（路徑盡量轉相對，每檔含 parser 與安全預覽）。

### 翻譯表
- 以 `namespace + key` JOIN 成一列，每個 locale 一欄，每頁上限 100 列。
- 模糊/精確搜尋、locale 篩選、缺翻譯與零使用率篩選、分頁。
- 一張捲動表單編輯所有 locale 值並存為單一驗證批次；批量刪除、跨 locale 改 key、複製/貼上 cell、IDE 原生 Find in Files。
- **Rename Key** 可選同步已記錄的原始碼使用位置，透過可編輯 code Diff 後才寫檔。Diff 的檔案下拉選單以圖示＋文字標籤（可編輯／唯讀）逐檔標示，切換時即時反映當前檔案是否可編輯。
- 從既有 locale 建立完整新 locale（如 `en/*.php` → `es/*.php`），附 ISO/BCP 47 建議 popup 與選填語言備註。

### 編輯器選取範圍建立翻譯
- 編輯器右鍵群組只在有選取文字時顯示可用狀態，且必須有目前方案；Modal 同時處理 key、所有 locale 值與選填的類似文字替換條件。
- 替換條件為 `%key%` 字串樣板＋檔名 suffix，不是 Regex；完全保留新增順序，每個檔案以第一個符合條件為準。
- 掃描只限方案 base path，遵守排除清單並略過列管語言檔、二進位、無效 UTF-8 與超過 5 MB 的來源檔；最多讀取 50,000 個符合副檔名檔案、列出 2,000 候選。
- 掃描註冊為可取消 JetBrains 背景任務；Modal 與 IDE indicator 顯示規劃／走訪／讀取階段、目錄／檔案／符合副檔名／命中數及相對路徑。一般檔案走訪另限 100,000 筆，排除清單以名稱 set 與祖先相對路徑查找，避免逐目錄線性比對 1,000 筆設定。
- Backend log 以 `Selection replacement scan` 標記開始、節流進度、完成、取消與失敗；包含安全統計與耗時，不包含選取原文或完整樣板。
- 候選清單雙擊才 lazy 建立單檔 Diff。送出後另顯示最終多檔 Diff 與完整路徑／可編輯狀態；語言檔唯讀、程式碼結果可編輯，套用前重新驗證 SHA-256 與完整檔案集合。

### AI 批量翻譯
- 對最多 100 列，透過 OpenAI-compatible 或 Anthropic Claude endpoint 翻譯。
- 來源文字預設 `en`（否則用 key）、可編輯；多目標 locale 各自獨立可編輯欄，最後合併成一份檔案 Diff。
- 只有 **Apply** 才寫檔；**Give AI More Feedback** 帶著編輯後來源、審核建議與意見進入下一輪。
- API token 存於 JetBrains PasswordSafe；Temperature 預設省略。

### 使用率掃描 / Usage
- 多組 Regex 累計出現次數（同行重複計數，同位置重疊去重）。
- 支援侵入式備註標記 `@languageManager(method: dynamic, <群組>: <key...>)`；只有實際位於程式碼備註中的標記會納入，群組與 key 均可重複新增。
- 支援非侵入式 **動態來源** Tool Window 頁籤：每條規則保存來源檔、line、column 與多層 key 群組，key 欄位可由目前翻譯表建議值選取。規則隨方案匯入／匯出並保持方案隔離。
- 侵入式與非侵入式表單皆為可捲動面板，並支援以換行、逗號、分號或自訂純文字分隔符批次加入 Key；維持順序、去重且套用每群組 500 個 Key 上限。
- `@languageManager` 支援 IDE 懸停文件；游標位於既有或複製的有效標記時，可由編輯器右鍵選單或 `Floating.CodeToolbar` 開啟表單編輯，且只替換標記本體。
- 兩種動態來源可由各自表單雙向轉換；backend 會檢查 stale marker，在同一方案 mutex 內以可回復交易同步來源檔與規則、重新載入 VFS／Document、清除 cache 並重算。
- 所有寫檔後的快取 Document reload 都在 EDT 的 write-safe `ModalityState.nonModal()` context 執行，避免 `ModalityState.any()` 觸發 TransactionGuard 的 `Write-unsafe context`。
- 開啟的編輯器以輕量 RangeHighlighter 顯示兩種來源的裝訂區圖示，不另行掃描專案。侵入式圖示導航至 marker；非侵入式圖示開啟動態來源頁籤並定位規則卡片。
- 有效的非侵入式規則會在目標程式碼上一行額外顯示輕量 block inlay，與登錄程式碼位置對齊並呈現去重 key 數量；懸停顯示方法、位置與群組內容，點擊定位規則。座標失效時略過 inlay，避免錯誤標註程式碼。
- 非侵入式規則卡片提供「前往標記位置」，直接依目前表單中的檔案、line、column 導航，允許在儲存前驗證調整後的座標。
- 編輯器只要有游標即可由右鍵「建立在地化管理動態使用來源」建立備註標記，或開啟「在地化動態使用來源」頁籤並預填檔案、行、欄；選取文字改為選填並只用於預填 key。翻譯表精準／模糊搜尋仍要求選取文字。
- 內建各主流框架 Regex 建議（PHP 框架、Spring/Java/Kotlin、ResourceBundle、IntelliJ Platform），另有 opt-in Laravel「key only」預設。
- 掃描設定以方案隔離：安全 base path、可編輯排除清單（每方案上限 1,000，支援逗號/換行批量、Project 樹右鍵加入資料夾）。
- Project 樹右鍵批量加入排除：逐一分類——掃描根本身/根外/無效/超長，以及超過 1,000 上限的多餘項目個別略過並回報，能加入的先加入，不因單一無法排除者中斷整批（其餘合法資料夾照常加入）。
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
- Regex、侵入式標記與非侵入式規則會在首次載入、強制重載及方案切換時合併計算；儲存動態規則會清除該方案 cache 並重新載入。位置仍只快取 offset，使用者開啟時才解析 line/column。

## 設計 / 規格約束（摘自 需求.md）

### RPC 與資料模型
- `LocalizationStateDto`：`schemes` / `activeSchemeId` / `entries` / `issues` / `busy` / `errorMessage`；前端持續 collect backend `state()` flow，不輪詢。
- `EntryMutationDto`：entry ID（新增可空）、file path、locale、namespace、key、value；backend 不信任前端路徑，每次 mutation 重新驗證歸屬並強制 reload。
- Preview：`ChangePreviewRequestDto`（全檔正規化／空值修復／entry 刪除）、`FileChangePreviewDto`（path、before、after、before SHA-256）；`applyPreviewedChanges` 帶入 preview 時的 expected hashes。
- 所有 DTO 用 `kotlinx.serialization`；backend RPC provider 不依賴非必要的 PHP/YAML/Properties IDE 插件。

### 快取佈局
- 方案清單：`.idea/language-manager/schemes.json`；每方案 `cache-{schemeId}.json`。
- Cache 含 format version、file fingerprints、entries、issues；version 或任一 fingerprint 不符即淘汰重解析；刪方案刪對應 cache。
- 記憶體以 `StateFlow` 保存當前權威狀態。

### 安全上限（硬約束）
- 路徑非空、≤4,096 字元、無危險控制字元；僅本機一般檔案，拒 `://`、`ldap:`、`file:`、device path、`GLOBALROOT`。
- 副檔名限 `.json`/`.yaml`/`.yml`/`.php`/`.properties`，單檔 ≤10 MB；value ≤100,000 字元、key ≤256 字元；locale/namespace 走白名單。
- 匯出方案設定**不含** cache/entries/issues；AI token 只存 PasswordSafe，非 localhost endpoint 只允許 HTTPS、禁 redirect、限回應大小。

### UI 行為細節
- 圖示須提供 16×16／20×20 的 Light/Dark 四種 JetBrains 命名變體；Tool Window 有固定 GitHub URL 的「回報問題」外連。
- `Ctrl+C` 複製所選 cell（單 cell 不含整列，多 cell 用 TSV）；`Ctrl+V` 只准貼入語言 value cell。
- 新增/編輯表單同頁列出全部語言，每個 textarea 保留 3 行／72px 高度。
- 全文搜尋只搜實際 key（不含 PHP 檔名或 bundle namespace）；亦可把方案第一條含 `(?<key>…)` 的 Regex 帶入 IDE Regex 搜尋（替換 key 群組、去除最外層 `^`/`$`、保留其他群組與反向參照）。
- 問題類型：`PARSE_ERROR`/`READ_ERROR`、`MISSING_VALUE`、`DUPLICATE_KEY`、`DUPLICATE_VALUE`、`MISSING_TRANSLATION`、`UNUSED_KEY`。
- 舊方案缺掃描設定須套安全預設、不得反序列化失敗；舊排除清單自動升級到最新預設但不覆蓋使用者自訂。

### 明確不在自動處理範圍
- 不自動選取/納管語言檔、不執行 PHP 取動態內容、不未經 Diff 確認就刪不確定未使用的 key、不對解析失敗檔猜測修復後寫回。

## 開發須遵守（摘自 AGENTS.md）
- 所有使用者可見文字必進 resource bundle，7 套字典同步新增相同 key，並更新 bundle parity tests。
- 新功能加入與風險相稱的測試（parser、安全、RPC DTO、搜尋、字典不可只靠手動驗證）。
- 交付前至少跑 `test` 與 `buildPlugin`，確認 ZIP 含三個 module 與所有語言 bundle。
- 功能更新同步 `CHANGELOG.md`；架構/API/操作改變同步 README 與 `docs/`。
