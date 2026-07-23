[English](user_manual_book.md) | [繁體中文](user_manual_book.zh.md)

# 在地化管理器使用者操作手冊

本手冊說明如何在 JetBrains IDE 中使用「在地化管理器 (LanguageManager)」建立語言方案、管理翻譯、檢查問題及安全套用修復。

> 插件不會自動選取、納管或修改任何語言檔。只有加入目前方案的檔案才會被讀取或寫入。

### 支援的 IDE 版本

插件最低支援 JetBrains Platform build `253.5`（IntelliJ IDEA 2025.3.5），且不設定最高版本。目前已由 Marketplace Plugin Verifier 驗證 IntelliJ IDEA 2025.3.5、2025.3.6、2026.1.1～2026.1.4 與 2026.2 RC 相容；完整紀錄請參閱 [相容性驗證](compatibility.zh.md)。

## 1. 支援內容

### 支援格式

- JSON：`.json`
- YAML：`.yaml`、`.yml`
- Laravel PHP：`.php`，內容可有 `declare(strict_types=1);`，之後必須是靜態 `return [...]` 或 `return array(...)`
- JetBrains／Java ResourceBundle Properties：`.properties`

### 介面語言

插件會跟隨 IDE 顯示語言，目前提供：

- English
- 繁體中文
- 简体中文
- 日本語
- 한국어
- Español
- ไทย

## 2. 安裝插件

### 從 ZIP 安裝

1. 開啟 IDE 設定。
2. 前往 **Plugins**。
3. 點擊齒輪圖示，選擇 **Install Plugin from Disk…**。
4. 選擇 `LanguageManage-{version}.zip`，不要先解壓縮。
5. 依 IDE 提示重新啟動。

安裝後，IDE 右側工具視窗列會出現「在地化管理器」圖示。IDE 會依目前 Light／Dark 主題與 UI 縮放，自動選擇 16×16 或 20×20 圖示，不需要另外設定。

## 3. 建立第一個方案

1. 開啟「在地化管理器」工具視窗。
2. 點擊「新增方案」下拉選單。
3. 選擇建立方式：
   - 「依檔案選取」：在 file chooser 中選擇一個或多個 JSON、YAML/YML、PHP、Properties 語言檔，再輸入方案名稱。
   - 「依資料夾選取」：一次選擇一個或多個資料夾，例如 `en`、`zh_CN`、`zh_TW`，等待 backend 合併掃描並嘗試解析支援格式檔案。
4. 資料夾識別視窗會顯示完整路徑、格式、locale、namespace、entry 筆數與識別結果。解析失敗的檔案會保留錯誤原因，但無法勾選；如有遺漏，可點擊「增加資料夾」重新合併掃描。
5. 輸入容易辨識的方案名稱，例如「網站前台」或「Admin API」，確認要列管的可識別檔案後點擊「建立方案」。
6. 等待狀態列完成讀取；翻譯表會顯示解析結果。

資料夾模式整批最多檢查 500 個支援格式檔案、每個根目錄遞迴深度最多 16 層，套用目前的新方案載入預算，並略過 `.git`、`.idea`、`vendor`、`node_modules`、`build`、`storage`、`cache` 等常見非來源目錄。若同時選到父目錄和子目錄，會依正規化完整路徑去重。掃描只提供候選清單，不會自動納管未經確認的檔案。

方案的特性：

- 每個方案擁有獨立檔案清單與快取。
- 切換方案不會混入其他方案的資料。
- 刪除方案不會刪除來源語言檔。
- 建立方案前可在資料夾識別 Popup 增加其他資料夾；方案建立後若要變更列管範圍，請建立新方案，插件不會自動加入檔案。

### 匯入與匯出方案設定

在 Tool Window「新增方案」下拉選單可選擇「匯入方案設定」或「匯出方案設定」。

- 匯出內容包含方案名稱、列管檔案、使用率 base path、Regex 與排除清單。
- 專案根目錄下的路徑會匯出為 `/` 分隔的相對路徑；專案外路徑保留絕對路徑。
- 不匯出 entry、issue、使用次數或 cache，匯入後會重新解析。
- 匯入以目前開啟專案的根目錄解析相對路徑，並在預覽表顯示方案、原設定路徑、解析後路徑與識別結果。
- 任一列管檔案缺失、副檔名不支援、路徑不安全或使用 `..` 超出專案根目錄時，匯入按鈕會停用。
- 檔案存在但 parser 回報錯誤時會顯示警告，仍可匯入，方便後續使用修復功能。

## 4. 語言與 Namespace 判定

### JSON／YAML

檔名會作為 locale：

```text
lang/en.json       -> locale: en, namespace: 空
lang/zh_TW.yaml    -> locale: zh_TW, namespace: 空
```

### Laravel PHP

父目錄是 locale，檔名是 namespace：

```text
lang/en/messages.php       -> locale: en, namespace: messages
lang/zh_TW/validation.php  -> locale: zh_TW, namespace: validation
```

### JetBrains／Java ResourceBundle Properties

檔名的 base 部分是 namespace；沒有 locale 後綴的 bundle 視為英文：

```text
LanguageManagerBundle.properties        -> locale: en, namespace: LanguageManagerBundle
LanguageManagerBundle_zh_TW.properties  -> locale: zh_TW, namespace: LanguageManagerBundle
```

## 5. 使用翻譯表

翻譯表會把相同 `namespace + key` 的翻譯合併成一列，每個 locale 是一個 value 欄位。

例如：

| Namespace | Key | en | zh_TW |
| --- | --- | --- | --- |
| messages | auth.failed | Invalid credentials | 登入資料錯誤 |

### 搜尋

- **模糊搜尋**：key、value、namespace、locale 或檔案路徑包含輸入文字即可命中。
- **精準搜尋**：key、value、namespace 或完整 `namespace.key` 必須相等，不區分大小寫。
- **語言篩選**：只用指定 locale 判斷哪些 key 命中，但表格仍保留同一 key 的其他語言欄位供比較。
- **翻譯狀態篩選**：預設「全部顯示」；可切換為「缺少任一語言翻譯」（語言不存在或 value 為空），或「使用次數為 0（可能未使用）」。

### 分頁

- 每頁最多 100 列。
- 使用表格下方「上一頁」與「下一頁」切換。
- 搜尋條件改變時會回到第一頁。

### 儲存格選取與剪貼簿

- 點擊任何 cell 會同時把該 cell 所屬 row 作為操作對象，整列會使用目前 IDE 主題的選取色高亮。
- `Ctrl+C`：複製目前選取的 cell；選取多個 cell 時輸出 TSV。
- `Ctrl+V`：只可貼入單一 locale value cell。
- Namespace、Key、使用次數與問題欄位不可貼上。

## 6. 翻譯操作選單

點擊「操作 ▾」可使用：

### 新增翻譯

1. 選擇 namespace 並輸入新的 key。
2. 可捲動表單會一次列出該 namespace 所有可用語言檔。
3. 直接填寫各語言 value；每個 textarea 固定保留三行、72 px 編輯高度，不會被 Dialog 按鈕壓縮。
4. 只需確認一次，插件會以單一批量操作驗證並寫入全部語言，最後只重新載入方案一次。未填欄位會建立為空值，讓缺失 value 分析可以列出。

### 新增語言版本

1. 點擊「操作 ▾」→「新增語言版本」。
2. 選擇來源語言，例如 `en`。
3. 可自由輸入新語言代碼，或點擊欄位右側建議按鈕開啟 ISO 639 語言及常用 BCP 47 變體，例如 `es`、`th`、`es-MX`、`zh-Hant`、`es-419`。輸入與刪除期間不會自動選取或插入候選；只有在 Popup 明確選取後才會取代欄位內容，通過 locale 驗證的專案自訂代碼仍可保留。
4. 可選填語言備註，例如「墨西哥西班牙文、正式語氣」。備註上限 500 字元，會保存於方案、包含於方案匯入／匯出，並帶入日後針對該 locale 的 AI 請求；備註不會改變語言檔路徑。
5. 插件會依來源語言的全部檔案建立目標清單：Laravel `en/auth.php` 會對應為 `es/auth.php`；`en.json` 會對應為 `es.json`。
6. 一般翻譯 value 與展開後的每個 JSON array 項目都會各自清空以等待翻譯；array 結構會保留，避免產生無法解析的檔案。
7. 在 Diff 視窗逐一確認新檔案；按下套用後才會建立檔案、保存備註並加入目前方案。

若目標 locale 已存在、目標檔案已存在、來源檔案解析失敗或多個來源對應到同一路徑，插件會停止建立，不會覆寫既有檔案。

### 編輯所選

1. 選擇翻譯表中的任一 cell。
2. 點擊「操作 ▾」→「編輯所選」。
3. 同一個可捲動表單會列出所選 namespace／key 的全部既有與可補語言，不再出現語言選擇 Popup，也不用切換目標檔案。
4. 每個語言會顯示實際列管檔案路徑及獨立三行 textarea；尚未有翻譯的語言會顯示空白輸入框，填入後即可一起新增。
5. 只需確認一次，全部語言會透過單一批量 RPC 套用；所有 mutation 會先完成驗證，若後續檔案寫入失敗，插件會嘗試還原已寫入檔案。
6. 若要跨全部語言變更 key，請使用「Key 改名」。key 可包含空格、Unicode 與標點，例如 `Not powered on or not detected`。

### 批量刪除

1. 選擇一列或多列。
2. 點擊「批量刪除」。
3. 確認視窗顯示的是所選翻譯鍵列數，不會將各 locale entry 重複計數。
4. 確認後，所選列包含的所有語言 entries 會從來源檔刪除。

### AI 翻譯所選項目

#### 設定 AI Provider

前往 **Settings → Tools → LanguageManager**，設定下列欄位：

| 設定 | 行為 |
| --- | --- |
| Provider | 選擇「OpenAI 相容 API」或「Anthropic Claude API」。 |
| API 端點 | 必須填入完整請求端點，不能只填 Provider 的 base URL；非本機端點必須使用 HTTPS。 |
| 模型 | 填入該端點實際支援的模型名稱。 |
| API Token | 儲存在 JetBrains PasswordSafe，不會寫入語言檔或包含於方案匯出檔。 |
| Temperature | 建議留空，插件會省略參數並使用 Provider／模型預設值。只有模型支援覆寫時才填寫：OpenAI-compatible 0–2；Anthropic 0–1。 |

除本機 `http://localhost`、`127.0.0.1`、`::1` 相容服務外，端點只接受 HTTPS；插件不跟隨 redirect，每次請求 timeout 為 90 秒，回應上限為 2 MB。請求格式依據官方 [OpenAI Chat Completions API](https://developers.openai.com/api/reference/resources/chat) 與 [Anthropic Messages API](https://platform.claude.com/docs/en/api/messages)。

#### 批量翻譯與檢視

1. 在 JOIN 翻譯表選擇 1–100 個 row，點擊「操作 ▾ → AI 翻譯所選項目」。單筆也能執行，但插件會建議把相關資料一起選取，以減少重複對話與 token 用量。
2. 選擇原文來源。若已納管 locale 包含 `en`，預設使用 `en`；否則使用 Key。若某個所選 row 在該來源沒有 value，必須先補上其可編輯原文儲存格才能繼續。也可以改選其他已納管語言，並在送出前逐列編輯原文。這些修改只存在於本次請求，不會改寫來源語言檔。
3. 多選一個或多個目標語言。來源語言不能同時成為目標；目標 locale 必須在該 namespace 有已納管檔案，確保套用時具有明確寫入位置。
4. 插件會對每個目標 locale 各送出一個批量請求。已保存的來源／目標語言備註會作為語言、地區、術語及語氣背景加入請求；提示詞明確規定備註不可改變回應格式，並要求保留 placeholder、ICU／MessageFormat、HTML、Markdown、跳脫、換行與前後空白，禁止翻譯 key 或 item ID。
5. 結果會 JOIN 到同一張檢視表：Namespace 與 Key 用於辨識 row，每個目標 locale 各有一個可編輯 value 欄。此時編輯的只是待套用建議。
6. 繼續檢查合併後的檔案層級 Diff。「套用」會先做 SHA-256 衝突檢查再寫入全部修改；「取消」會離開且不寫入。
7. 點擊「提出其他意見 AI」可說明需要如何調整；新請求會包含已編輯原文及該 locale 上一輪已檢視結果。在意見視窗點擊「返回」會回到原本 Diff，不會結束整個 AI 流程。送出意見後仍會重新顯示結果表與 Diff，不會自動套用。

例如選擇 1 個 row、3 個目標 locale，會建立 3 次翻譯請求與 3 個待套用 mutation。批次中每個原文上限 10,000 字元、總原文上限 60,000 字元。AI 回傳的 ID 必須與請求 row 完全一致；缺失、重複、額外、格式錯誤或過大的回應會在進入 Diff 前被拒絕。

#### 語言代碼建議

插件會把從納管檔名識別到的 locale 原樣放入 `source_locale` 與 `target_locale`，目前不會把自訂代碼轉換成語言名稱。因此使用標準 ISO 639／BCP 47 代碼最穩定：

| 目標語言或變體 | 建議代碼 |
| --- | --- |
| 中性西班牙文 | `es` |
| 西班牙／墨西哥／拉丁美洲西班牙文 | `es-ES`／`es-MX`／`es-419` |
| 泰文 | `th` |
| 繁體中文 | `zh-TW` 或 `zh-Hant` |
| 簡體中文 | `zh-CN` 或 `zh-Hans` |
| 巴西／葡萄牙葡萄牙文 | `pt-BR`／`pt-PT` |
| 塞爾維亞文西里爾／拉丁文字 | `sr-Cyrl`／`sr-Latn` |

既有 `zh_TW` 等底線形式仍可使用，但 BCP 47 的連字號形式通常更容易讓 Provider 明確理解。避免使用 `jp`、`kr`、`cn`、`tw`、`zht` 等國家或自訂縮寫；應改用 `ja`、`ko`、`zh-CN`、`zh-TW` 或其他明確標準代碼。`es`、`pt`、`zh`、`sr` 等通用代碼可能產生中性版本，或由 Provider 自行選擇地區／文字系；若術語或書寫系統很重要，請指定更精確的 locale。

### 複製 key 到指定語言數值

選擇一個或多個 row，點擊「操作 ▾ → 複製 key 到指定語言數值」，選擇目標語言並確認；每個 row 的 literal key 會成為該語言 value。

### Key 改名

1. 選擇一個翻譯 key。
2. 點擊「Key 改名」。
3. 輸入新 key。
4. 方案內所有含有舊 key 的語言檔會一起改名。

若任一檔案已有新 key，操作會拒絕寫入以避免覆蓋。

### IDE 全文搜尋

選擇一列後點擊「IDE 全文搜尋」，插件會開啟 IDE 原生 **Find in Files**，且所有格式都只帶入實際 key。PHP 的檔名 namespace（例如 `auth`）及 Java ResourceBundle 的 bundle namespace（例如 `LanguageManagerFrontendBundle`）都不會加入搜尋文字。

點擊「帶入計算次數格式於全文搜尋」時，插件會從目前方案依序選擇第一個含 `(?<key>…)` 命名群組的使用率 Regex，將整個 key 群組替換成目前 row 的 literal key、移除最外層 `^`／`$`，並自動開啟 Find in Files 的 Regex 模式。key 內的句點等 Regex 符號會逐字元跳脫，例如 `custom\.attribute-name\.rule-name`，不會產生容易被 IDE 再次跳脫的 `\Q...\E`。其他群組及反向參照會保留，例如 `(?<quote>…)` 與 `\k<quote>`。若 Regex 清單沒有 `key` 命名群組，插件會顯示錯誤而不開啟搜尋。

## 7. 問題與建議

切換到「問題與建議」頁籤可查看：

| 類型 | 意義 | 可用操作 |
| --- | --- | --- |
| 格式錯誤／讀取錯誤 | 檔案無法安全解析或讀取 | 開啟來源檔案手動修正 |
| 缺失值 | Value 是空白 | 預覽以 key 補值 |
| 重複鍵 | 同 locale、namespace、key 重複 | 定位後人工判斷 |
| 重複值 | 多個 key 使用相同 value | 定位後人工判斷 |
| 缺少語言 | 某 key 未出現在全部 locale | 定位到翻譯表 |
| 可能未使用 | 專案來源碼未掃描到 key | 預覽刪除或保留 |

### 單列處理

點擊該列最後一欄的「處理」按鈕。按鈕會依類型執行修復預覽、刪除預覽、開啟檔案或定位翻譯表。

### 批量處理

- 「處理所選」：處理目前選取的問題列。
- 「處理全部可修復」：包含缺失值與可能未使用 key。
- 需要人工判斷的項目不會被自動修改。

> 「可能未使用」來自有限範圍的文字掃描，只是建議，不代表 key 一定可以刪除。動態組合的 key 可能無法被掃描到。

## 8. Diff 預覽與安全套用

「修復／正規化」及問題自動處理不會立即寫檔。

1. 插件在 backend 產生目前內容與修改後內容。
2. 顯示 IDE 雙欄 Diff；左側是目前內容，右側是 proposed content。
3. 批量修改時，可從上方選單逐一查看受影響檔案。
4. 選擇「取消」不會修改任何檔案。
5. 選擇「套用變更」後，插件會再次檢查原檔 SHA-256。
6. 若檔案在預覽期間被 IDE、Git 或其他程序修改，插件會拒絕覆蓋並要求重新預覽。

### 修復／正規化會做什麼

- 以 key 補上空白 value。
- 將可解析內容依標準 JSON、YAML、PHP 或 Properties 格式重新輸出。
- 無法解析的檔案不會被猜測修復或寫回。

## 9. 格式注意事項

### JSON

- 根節點必須是 object。
- 巢狀 object 在表格中以點號 key 顯示。
- Array 會展開為 `sections.0.items.3.title` 形式的一般翻譯列；每個項目皆可像單一文字一樣搜尋、編輯、刪除、分析或使用 AI 翻譯，寫回時會重建 array。
- 完整句子作為 key 時，句點會保留為字面 key，不會自動變成巢狀 object。

### YAML

- 使用空白縮排，不可使用 tab。
- 支援單引號、雙引號與一般行尾註解。
- 正規化可能重新排列引號與縮排格式，請先檢查 Diff。

### Laravel PHP

- 僅接受可選的 `declare(strict_types=1);` 與靜態 return array。
- 支援字串、數字、布林與巢狀 array。
- 靜態字串串接（例如 `'第一段'."\n\n".'第二段'`）會合併成單一的一般翻譯值，不再誤報「PHP 陣列項目之間缺少逗號」。
- 支援多行 heredoc 與 nowdoc。`TEXT` 只是範例，`EOT`、`HTML`、`MESSAGE_2026` 等任何前後一致的合法 PHP identifier，都可搭配 `<<<LABEL`、`<<<"LABEL"` 或 `<<<'LABEL'` 使用。識別字必須以字母或底線開頭，後續可包含字母、數字或底線且不可為空；名稱只標示字串邊界，不會限制內容類型。
- 支援語言目錄下的分類子目錄：`en/components/pagination.php` 會使用 locale `en` 與 namespace `components.pagination`；新增 `es` 時會建立 `es/components/pagination.php`，並保留相同的相對目錄結構。
- 不支援其他 `declare` 指令、函式呼叫、變數、heredoc 或字串 interpolation、非字串串接或任意 expression。
- 插件不會執行 PHP。

### JetBrains／Java ResourceBundle Properties

- 接受以 `#` 或 `!` 開頭的註解，以及 `=`、`:` 或空白分隔 key/value。
- 支援反斜線續行及 Java escape，例如 `\\t`、`\\n`、`\\ `、`\\:` 與 `\\u4F60`。
- 同一檔案內的重複 key、空白 key、未完成 escape 或錯誤 `\\uXXXX` 會安全回報 `PARSE_ERROR`，不會寫回來源檔。
- 編輯或正規化後使用 UTF-8 輸出；註解與原始排版不保留，因此套用前請檢查 Diff。
- 新增語言版本時，`Bundle.properties` 可產生 `Bundle_es.properties`；既有 `Bundle_zh_TW.properties` 也會保留 `Bundle` namespace。

## 10. 使用率掃描設定

在 LanguageManager Tool Window 上方先選擇方案，再點擊「方案設定」。Popup 會直接使用 Tool Window 已載入的目前方案，顯示列管語言檔案與獨立掃描設定，不會再從 IDE Settings 頁動態讀取方案。

**Settings → Tools → LanguageManager** 不會動態載入既有方案，管理插件顯示語言、問題與建議顯示偏好，以及新建方案預設值。也可以從 Tool Window 標題列的 JetBrains「更多選項」點擊「在地化管理器設定」前往該頁。

勾選「隱藏重複值建議」或「隱藏可能未使用建議」後，對應類型不會顯示在問題表、底部建議數量或「處理全部可修復項目」中；其他錯誤與建議不受影響。

此設定頁同時保存「新建方案預設值」：base path 可使用目前專案目錄，或輸入向上 1–10 層；預設 Regex 與排除清單會在之後以檔案或資料夾建立方案時複製到新方案，不會覆蓋既有方案。

新方案預設限制為：單一語言檔 2,048 KB、方案語言內容總計 20 MB、單檔 20,000 筆翻譯、方案總計 100,000 筆翻譯。每個既有方案也能獨立調整這四項。插件會先檢查檔案大小才配置 parser 內容；翻譯筆數會在 parser 建立 map 時立即限制，JSON／PHP 巢狀結構最多 128 層。超限內容會略過並列為問題，不會保留在表格或快取。安全硬上限為單檔 10 MB、單一方案 100 MB、單檔 100,000 筆與單一方案 250,000 筆。若本次結果太大，仍可使用目前記憶體狀態，但不會再序列化成過大的磁碟 cache。

此快捷入口直接依插件設定元件定位，不依賴 IDE 顯示語言；英文、繁體中文、簡體中文、日文、韓文、西班牙文或泰文介面都會開啟同一個設定頁。

### 掃描基準路徑

- 留白時使用目前開啟專案的根目錄。
- 可按「瀏覽」指定另一個安全的本機或 WSL 資料夾，例如 monorepo 的 frontend 根目錄。
- 這個路徑只影響使用次數計算，不會把該資料夾中的語言檔自動加入方案。

### 使用率判斷正規表示式

「新建方案預設值」與目前方案設定都會在 Regex 清單下方固定顯示完整 placeholder 說明；新增／編輯視窗也會顯示可直接使用的雙引號範例，例如 `(?:backendMessage|message)\(\s*"(?<key>[^"\r\n]{1,256})"\s*\)`。請依專案實際函式名稱調整前綴。建議限定函式名稱，不要直接配對所有引號文字，因為同一行前面若有其他字串或字元常值，可能先消耗非重疊 match 而漏掉真正的在地化呼叫。

Regex 清單旁的「推薦格式」可加入 Laravel、Symfony、webman、Laminas／Zend、CodeIgniter、CakePHP、Yii、Phalcon、FuelPHP、Slim／Pixie／自訂 translator、Spring `MessageSource`、Java／Kotlin `ResourceBundle` 與 IntelliJ Platform bundle 預設。Slim 與 Pixie 沒有可確認的內建翻譯 API，因此使用通用自訂 helper 格式，加入後應依專案實際函式名稱調整。

Laravel 提供兩種推薦規則。「Laravel」會擷取 helper 的完整參數，精準度較高；「Laravel－僅擷取 key（忽略 namespace／group 前綴）」會忽略可選的 package namespace 與第一個句點以前的 group，例如 `__('filament::components/button.messages.uploading_file')` 擷取 `messages.uploading_file`，`__('components/filament.someLangKey1')` 擷取 `someLangKey1`。這能在前綴難以可靠映射時增加命中率，但不同 package／group 的同名 key 可能形成誤判，因此只作為選用推薦，不會加入預設。若要計數的呼叫位於預設排除的 `vendor` 目錄，請把使用率 base path 直接指向該 package 的來源目錄，或明確調整目前方案的排除清單。

每個 Regex match 會依下列順序擷取候選 key：

1. 命名群組 `(?<key>…)`。
2. 第一個 capture group。
3. 如果沒有群組，使用完整 match。

候選值最後仍需與方案中的 `key` 或 `namespace.key` 完全相同才會計數。Laravel helper 可加入例如：

```regex
(?:__|trans)\(\s*["'](?<key>[^"']+)["']
```

Vue／JavaScript `$t()` 可加入例如：

```regex
\$t\(\s*["'](?<key>[^"']+)["']
```

每個不同 match 都會計數，因此同一行的重複呼叫，以及不同 Regex 格式各自命中的使用位置都會累加。若多個 Regex 捕獲到同一來源位置的相同 key，該位置只計一次，避免重疊規則灌高結果。動態串接的 key 通常無法可靠識別。

掃描器會把方案 Regex 套用到 base path 下每個一般檔案的完整內容，不會依副檔名、檔名或 IDE 檔案類型額外限制，因此自訂模板、無副檔名腳本、產生碼格式、超長單行與跨行 Regex 都能依方案規則計算。只會略過方案排除清單明確指定的目錄，以及方案本身列管的語言檔；後者用於避免把翻譯定義誤算成使用次數，其餘來源範圍完全由方案控制。

### 檢查使用位置

「使用位置」Tab 預設停用且保持空白，一般方案載入不會替未互動的位置資料建立 Swing table。雙擊某一翻譯 row 的「使用次數」cell，才會選取該 `namespace + key`、啟用 Tab，並只顯示該 key 的來源命中。表格包含來源檔、行號、欄位與命中次數，每頁最多 100 筆。初始行／欄顯示「開啟時計算」：掃描階段只快取字元 offset 與來源檔修改時間；雙擊位置或按「開啟位置」後，backend 才計算行／欄、更新快取並把 IDE 游標移到該處。若來源檔在掃描後變更，插件會拒絕舊 offset 並要求重新讀取。

### 排除資料夾

預設清單包含：

- 專案／依賴：`.git`、`.github`、`docs`、`vendor`、`node_modules`。
- Laravel／Gradle／建置：`storage`、`database`、`gradle`、`.gradle`、`build`、`out`、`dist`、`target`。
- IDE：`.idea`、`.run`、`.vscode`、`.fleet`、`.vs`、`.settings`、`.metadata`、`nbproject`。
- AI／環境：`.env`、`.claude`、`.codex`、`.gemini`、`.agents`、`.ai`。

升級時，若清單仍是舊版原廠預設，插件會自動補上新項目；已自訂的清單不會被取代。

- 單一名稱（例如 `vendor`）會排除掃描樹中所有同名資料夾。
- 相對路徑（例如 `tests/fixtures`）只排除基準路徑下的指定分支。
- 可使用「新增／編輯／刪除」調整清單，避免測試資料、fixture 或產生碼造成誤算。「大量新增」可貼上以逗號或換行分隔的資料夾名稱與相對路徑，自動移除空白項目及重複項目，最後仍由 backend 執行安全驗證。
- 在 Project 檔案樹選取一個或多個資料夾，使用「在地化語言管理 → 從目前方案掃描中排除資料夾」。插件會儲存相對於目前方案 base path 的精確路徑，接著使 cache 失效並重新計算。沒有啟用方案或選取內容包含檔案時操作會停用；掃描根目錄本身及根目錄外部資料夾會被拒絕。

按「套用」後，插件會儲存所有已修改方案、使對應 cache 失效，並在背景重新計算目前方案。Regex 最多 20 個、每個最多 512 字元；排除項目最多 1,000 個。

## 11. 快取與重新讀取

方案與快取存放於：

```text
.idea/language-manager/
├── schemes.json
└── cache-{schemeId}.json
```

- 一般切換方案時，檔案 fingerprint 未改變就使用 cache。
- 切換方案與「重新讀取」會出現在 IDE 背景任務指示器，並可由使用者取消。任務會先計算符合條件的來源檔案數，再精確列出準備、語言檔解析、建表、來源檔掃描、分析及快取步驟總數與已完成數。再次切換或重新讀取會取消前一次讀取；parser 與來源掃描透過合作式檢查停止舊工作，generation 檢查則阻止過時的 row、問題或 cache 寫回。Tool Window 狀態文字使用相同動態進度，最新任務完成後回到翻譯／問題數量摘要。
- 點擊「重新讀取」會強制重新解析。
- 來源檔變更、cache format 升級或 fingerprint 不符時會重建 cache。
- 不建議手動編輯 cache；刪除 cache 不會刪除來源語言檔。

## 12. 疑難排解

### 建立方案後沒有反應

- 查看狀態列是否顯示「讀取中」。
- 等待 WSL／遠端檔案第一次存取完成。
- 點擊「重新讀取」。
- 確認檔案仍存在且單檔未超過 10 MB。
- 檢查 IDE log 中包含 `Language Manager` 的片段。

### 顯示解析錯誤

- 在問題列點擊「處理」開啟來源檔。
- JSON 必須有 object 根節點且語法完整。
- YAML 不可用 tab 縮排。
- PHP 必須是可選的 `declare(strict_types=1);` 加上純靜態 return array。

### 貼上沒有作用

- 一次只選擇一個 cell。
- 必須選擇 locale value 欄，不是 Namespace、Key 或診斷欄。
- 確認剪貼簿內容是文字。

### 使用次數顯示 0

- 動態組合 key、模板 helper 或非文字引用可能無法辨識。
- 在 Tool Window 選擇方案後點擊「方案設定」，確認 base path、Regex 與排除清單。
- 掃描會跳過方案排除清單中的資料夾；新方案預設會排除 `.git`、`.github`、`docs`、`vendor` 與常見 AI／IDE 設定目錄。
- 「0」代表掃描未找到，不等同確定未使用。

### AI 翻譯回傳 HTTP 400 或沒有進入檢視表

- 確認 **API 端點** 是完整的 OpenAI-compatible chat-completions 或 Anthropic messages 請求端點，而不是只有 base URL。
- 確認該端點存在設定的模型，且 Token 具有使用權限。
- 若錯誤指出不支援 Temperature，請清空 **Temperature**；留空時插件會完全省略該參數。
- Provider 必須回傳要求的 JSON 翻譯物件。Markdown 說明文字、缺失／重複／額外 ID、無效 JSON 或超過 2 MB 的回應都會被拒絕，不會進入 Diff。
- Timeout 或網路錯誤不會寫入檔案；確認端點後可減少 row 或目標 locale 數量再試。

### AI 翻譯使用了錯誤的地區語言

- 使用 `es-MX`、`pt-BR`、`zh-Hant`、`sr-Latn` 等明確標準 locale，不要使用模糊或自訂縮寫。
- 檢查可編輯原文預覽。只使用 Key 可能缺乏語境；可改選有內容的來源語言，或把本次暫時原文描述得更明確。
- 可直接修改結果表，或使用「提出其他意見 AI」。在意見視窗按「返回」會保留並重新開啟目前 Diff。
- 務必確認目標 locale 欄與最終 Diff；AI 輸出在點擊「套用」前都只是建議。

## 13. 回報問題

工具視窗上方提供「回報問題」連結，會開啟：

[https://github.com/creamgod45/LanguageManage/issues/new](https://github.com/creamgod45/LanguageManage/issues/new)

請選擇適合的 Issue 類型：

- 錯誤回報：可重現的 UI、RPC、解析、寫入或效能問題。
- 功能需求：新的操作、分析、格式或 IDE 整合需求。
- 格式相容性：JSON、YAML、Laravel PHP、ResourceBundle Properties 無法 parse／round-trip 的最小案例。

提交前請移除 log、路徑與語言檔中的密碼、token、客戶名稱及其他敏感資料。

## 14. 查看版本更新說明

上傳 Marketplace 更新後，插件頁面的 **What’s New** 與 IDE 插件管理器會顯示 [CHANGELOG.md](../CHANGELOG.md) 中與目前版本相同的區段。建置會直接產生這份 metadata，不再另外維護容易不同步的摘要。
