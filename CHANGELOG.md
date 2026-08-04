# Language Manager Changelog

## 1.5.6

### English

- Add **Localization Manager → Create Translation from Selection** to the editor context menu. It is enabled only when text is selected and an active scheme exists, then opens one modal for the key, all locale values, and optional similar-text replacement assistance.
- Add ordered placeholder replacement conditions such as `__('%key%')` for `.php` and `@lang('%key%')` for `.blade.php`, plus framework suggestions. Conditions remain in insertion order and the first matching suffix wins; templates are treated only as data and never as Regex or executable code.
- Scan only the active scheme working directory, honor its exclusion list, skip managed language files, binary/invalid UTF-8 files and oversized source files, and cap candidate discovery. The match list is lazy: double-clicking a file generates its Diff only then.
- Add a final multi-file confirmation Diff that lists every affected path and its state. Generated language-file changes are read-only, source-code results are editable, and apply regenerates the preview, verifies the exact file set and SHA-256 hashes, then writes atomically with rollback.
- Add scheme renaming to **Scheme Settings**. The name and usage-scan settings are validated and saved together while the scheme ID, managed files, cache ownership, and isolation remain unchanged.
- Make the folder-based creation dialog's scheme-name field prominent and fully editable before creation. Shared validation trims the name and rejects blank, over-80-character, or control-character input in both file- and folder-based creation and settings.
- Split GitHub Issue Forms into dedicated English and Traditional Chinese versions for bug reports, feature requests, and format compatibility reports. The New Issue page now presents six clearly labeled templates plus separate English and Traditional Chinese user-manual links.

---

### 繁體中文

- 在編輯器右鍵選單加入「**在地化管理器 → 以選取範圍建立翻譯文字**」。只有選取文字且目前方案已啟用時才能操作；同一個 Modal 可填寫 key、全部 locale 翻譯值，以及選填的類似文字替換輔助。
- 新增依加入順序執行的 placeholder 替換條件，例如 `.php` 使用 `__('%key%')`、`.blade.php` 使用 `@lang('%key%')`，並提供框架建議樣板。條件不會自動排序，第一個符合副檔名的條件優先；樣板只當作字串資料，不會被當成 Regex 或程式碼執行。
- 掃描範圍只限目前方案工作目錄，遵守排除清單並略過列管語言檔、二進位／無效 UTF-8 與過大程式碼檔案，同時限制候選數量。檔案清單採 lazy 流程，只有雙擊項目時才建立該檔案 Diff。
- 新增最終多檔再次確認 Diff，列出所有變更檔案位置與狀態。產生的語言檔維持唯讀，程式碼替換結果可編輯；套用時後端會重新計算預覽、核對完整檔案集合與 SHA-256，再以具回復機制的原子寫入完成。
- 在「**方案設定**」加入方案改名。方案名稱與使用率掃描設定會一起驗證並儲存，方案 ID、列管檔案、快取歸屬及隔離狀態皆維持不變。
- 將資料夾建立方案視窗的名稱欄位改為醒目且可完整編輯的獨立欄位。依檔案／資料夾建立及方案設定共用相同驗證：名稱會自動去除前後空白，並拒絕空白、超過 80 個字元或包含控制字元的輸入。
- 將 GitHub Issue Forms 的錯誤回報、功能需求與格式相容性拆分為獨立英文及繁體中文版本。New Issue 頁面會清楚顯示六份模板，並分別提供英文與繁中使用者手冊連結。

## 1.5.5

### English

- Fix a serious logic error in the Project view **Exclude Folders from Current Scheme Scan** action: when the selection contained a folder that could not be excluded (the scan root itself or a folder outside the scan root), the backend threw and aborted the whole batch, so none of the other valid folders were excluded. Each folder is now classified independently—valid folders below the scan root are added while unexcludable ones are skipped and reported. Additions that fail validation (over-long or unsafe) or that would push the list past the 1,000-entry cap are likewise skipped instead of aborting: the folders that still fit are added and the rest are reported. The notification distinguishes added, partially-added (with a skipped count), skipped-only, and already-excluded outcomes. Directly editing the exclusion list in Settings keeps its strict over-limit validation, so the two paths stay separate.
- Surface per-file editability directly in the Rename Key preview dropdown. Each entry now shows an icon plus an "editable"/"read-only" tag—editable source-code files are highlighted and read-only language files are grayed—so the currently selected file's state is visible in the dropdown itself instead of only in the hint line below it. The hint line is retained and revalidated on selection so its longer read-only text no longer truncates.
- Extend **Rename Key** with an optional source-usage synchronization checkbox. When enabled, the backend validates cached Regex offsets against `oldKey` or `namespace.oldKey`, prepares language-file and source-code changes without writing, and shows one Diff before apply.
- Make the right side of matched source-code Diff entries editable while keeping generated language-file changes read-only. User-edited results are applied only after the backend regenerates the preview, verifies the exact file set and SHA-256 hashes, enforces size/control-character limits, and performs atomic writes with rollback.
- Show a dynamic Diff hint for the currently selected file: language-file changes are identified as read-only, while matched source-code changes explicitly indicate that the proposed content on the right can be edited.
- Keep cached usage-location records only in the backend and fetch them on demand after the user double-clicks a Usage cell. The RPC returns at most 100 deduplicated locations per page, so normal state updates and lazy line/column resolution no longer serialize and copy up to 250,000 location records into the frontend.
- Add a reproducible 12-locale memory regression test that uses the production JSON parser, production Regex usage scanner, and JOL retained-size measurement. The 96,000-entry reference run remains within default scheme limits and records both frontend retained memory and serialized RPC payload savings in `docs/performance_memory.md`.

---

### 繁體中文

- 修正 Project 檢視中「**從目前方案掃描排除資料夾**」的嚴重邏輯錯誤。批量選取包含掃描根目錄或根目錄外的資料夾時，現在會逐項處理：有效資料夾仍會加入排除清單，無法排除、不安全、過長或超出 1,000 筆上限的項目則會略過並回報，不再讓整批操作中止。通知會區分新增成功、部分新增、全部略過及已存在等結果；設定頁直接編輯排除清單時仍維持嚴格的上限驗證。
- 在 Key 改名預覽的檔案下拉選單中直接顯示每個檔案的可編輯狀態。可編輯的程式碼檔案與唯讀語言檔案會以圖示、文字標籤及不同樣式辨識，切換檔案時下方提示也會同步更新。
- **Key 改名**新增「同步程式碼使用位置」選項。啟用後，後端會依快取的 Regex 位置驗證 `oldKey` 或 `namespace.oldKey`，先產生語言檔與程式碼變更，再以單一 Diff 預覽確認。
- 符合的程式碼檔案可直接編輯 Diff 右側結果，產生的語言檔變更仍維持唯讀。套用前後端會重新建立預覽、核對完整檔案集合與 SHA-256、驗證內容大小及控制字元，並以具備回復機制的原子寫入完成變更。
- Diff 會依目前選取的檔案動態提示：語言檔案明確標示為唯讀，程式碼檔案則提示右側修改內容可以編輯。
- 使用位置紀錄只保留在後端快取；使用者雙擊「使用次數」欄位後才按需讀取。RPC 每頁最多回傳 100 筆去重後的位置，避免一般狀態更新與延遲計算行列位置時，將最多 250,000 筆紀錄序列化並複製到前端。
- 新增可重現的 12 種 locale 記憶體回歸測試，使用正式 JSON parser、Regex 使用率掃描器與 JOL retained-size 測量。96,000 筆翻譯的參考案例符合方案預設限制，並在 `docs/performance_memory.zh.md` 記錄前端保留記憶體與 RPC 序列化資料量的改善。

## 1.5.4

- Replace the internal/experimental `reportRawProgress` integration with the stable `Task.Backgroundable` and `ProgressIndicator` APIs. Cancellable stage text, detail text, exact fractions, and superseded-task cancellation are preserved without `RawProgressReporterHandle` or `RawProgressReporter` bytecode references.
- Raise each scheme's exclusion capacity from 100 to 1,000 entries and add comma/newline bulk entry. The Project file-tree context menu now contains **Localization Manager → Exclude Folders from Current Scheme Scan**, stores precise paths relative to the active scheme base path, disables itself without an active scheme, invalidates cache, and recounts safely.
- Add determinate load progress to the JetBrains background task and Tool Window status. The backend first counts eligible source files, then reports `preparation + language files + table build + source files + analysis + cache` as an exact step total through a dedicated lightweight RPC Flow.
- Add an on-demand **Usage Locations** table. Double-click a translation row's Usage cell to enable the tab and show only that key's cached source file, match offset, and occurrence records with 100-row pagination. Line and column are calculated only when a location is opened, cached afterward, and rejected if the source file changed after scanning.
- Show scheme switching and manual reloads as cancellable JetBrains background tasks. Starting a newer scheme load cancels the obsolete request, parser and usage-scan checkpoints stop work cooperatively, and a generation guard prevents stale rows, issues, or cache data from replacing the latest scheme. Tool Window status text now derives from backend busy state plus tracked UI operations, so completed or superseded jobs cannot leave a false loading indicator.
- Fix valid PHP language files being incorrectly reported as “missing a comma between PHP array items” when a translation uses static string concatenation such as `'first'."\n\n".'second'`; all string segments are now combined into one editable translation value.
- Add multiline PHP heredoc and nowdoc translation support. Delimiters are not limited to `TEXT`: any matching valid PHP identifier such as `EOT`, `HTML`, `MESSAGE_2026`, `<<<LABEL`, or `<<<'LABEL'` is supported and the content is treated as an ordinary translation value.
- Keep PHP parsing static and safe: variables, heredoc interpolation, function calls, non-string concatenation, and executable expressions remain rejected and PHP is never evaluated or included.
- Add an opt-in Laravel “Key only” recommended usage Regex that ignores uncertain package namespace and group prefixes. It captures `messages.uploading_file` from `__('filament::components/button.messages.uploading_file')` without changing table namespaces or existing exact-match behavior; projects should use it only when the increased recall is worth possible same-key false positives.

## 1.5.3

- Expand every JSON array scalar into an ordinary translation row (for example, `sections.0.items.3.title`) so table JOIN, search, editing, deletion, missing-value analysis, usage counts, and AI translation behave the same as scalar dictionary entries.
- Preserve and rebuild nested JSON array shapes on write, including empty arrays and arrays missing items in another locale; creating a locale now clears each expanded array translation independently.
- Add round-trip coverage for three 10-level examples with 100 objects each, plus a 10,000-object asymmetric-locale stress test to prevent crashes when one large language file is mostly missing from another locale.
- Support PHP language files organized in category subdirectories, such as `en/components/pagination.php`; locale detection identifies the parent language-code directory, namespaces retain the relative category path, and new locales preserve the same subdirectory structure.
- Align usage scanning with scheme Regex scope by removing hidden extension/file-type, 2,000-file, 512 KB, and 4,096-character line filters; custom and multiline patterns now run against complete regular-file content while managed language files and configured exclusions remain skipped.

## 1.5.2

- 插件顯示語言新增西班牙文（`es`）與泰文（`th`）明確選項及自動 IDE locale 識別，並補齊插件中繼資料、frontend UI、backend 診斷與七語言字典一致性測試。
- 「新增語言版本」改用一般文字欄位搭配明確開啟的語言代碼建議 Popup；輸入或刪除文字時不再重建 ComboBox model、搶選第一筆候選或自動回填，只有使用者選取候選才套用代碼。
- 修正「新增語言版本」在 Darcula／Dark Theme 開啟時，editable locale ComboBox 尚未安裝 UI editor，導致 `BasicComboBoxUI.getDisplaySize()` 於 EDT 拋出 NullPointerException。
- 「新增語言版本」的目標 locale 改為可自由輸入的自動完成欄位，列出 ISO 639 與常用 BCP 47 語言／地區／文字系代碼；使用者仍可輸入專案自訂代碼。
- 新增每個 locale 的選填語言備註，例如「墨西哥西班牙文、正式語氣」；備註保存於方案、支援匯入／匯出，並以受限 JSON 欄位帶入 AI 翻譯 Prompt 作為語言、地區、術語與語氣背景。
- Properties bundle locale 推導支援 `zh-Hant`、`sr-Latn`、`es-419` 等 BCP 47 script／numeric region tag，確保推薦代碼建立後仍能正確重新載入。
- 擴充中英文使用者手冊的 AI Provider、批量翻譯、Diff、多輪意見、語言代碼、安全限制與疑難排解說明。

## 1.5.1

- 修正部分 OpenAI-compatible 與 Anthropic 模型因固定傳送 `temperature=0.1` 而回傳 HTTP 400；Temperature 改為選填設定，預設留空時完全省略參數並使用 Provider／模型預設值，需要時才依 Provider 有效範圍送出。
- 重整 AI 翻譯 Modal：來源可選 Key 或任一語言（優先預設 `en`，沒有時使用 Key），逐列原文可在送出前編輯；目標語言支援多選，產生結果以每個目標語言一欄合併檢視，最後以同一份檔案 Diff 套用。
- 修正 AI 翻譯表格欄名引用不存在的字典 key 而顯示 `!column.namespace!`／`!column.key!`；「提出其他意見」按返回時會回到原 Diff 預覽，不再結束整個 AI 翻譯流程。
- 依 JetBrains 官方規範重製 Marketplace 與 Tool Window 圖示：Marketplace Logo 使用 40×40 SVG、36 px 圓形可視區與 2 px 透明安全邊界，並以小型原生向量取代過大的自動描圖 SVG。

## 1.5.0

- 主插件 descriptor 明確宣告 `com.intellij.modules.platform`，讓 Marketplace 依通用平台模組推導 IntelliJ IDEA、PhpStorm、WebStorm、PyCharm、DataGrip 等相容產品，不綁定單一語言 IDE。
- 新增 OpenAI-compatible 與 Anthropic Claude AI Provider 批量翻譯：API Token 使用 JetBrains PasswordSafe，最多一次處理 100 個 row，結果必須先檢視、編輯及確認檔案 Diff；Diff 可 Apply、取消或「提出其他意見 AI」，後者會帶入上一輪來源與翻譯上下文建立新一輪請求。單筆操作會以 Toast 提醒多選可節省 token。
- 「操作」新增「複製 key 到指定語言數值」，可對多個所選 row 一次套用。
- 使用率 Regex 新增 PHP、Spring／Java／Kotlin、ResourceBundle 與 JetBrains Plugin 推薦格式選單；Slim／Pixie 使用通用自訂 translator 格式，不假設框架內建 API。
- 修正多個使用率 Regex 與同一行重複呼叫只計為一次的問題；現在依實際 match 累加，並以捕獲位置與 key 去除不同 Regex 對同一段文字的重複命中。
- Marketplace、Plugin Manager 與 Tool Window Sidebar 圖示改用 LanguageManager 多語言圓形品牌標誌；提供 Light／Dark Theme 的 40×40 Marketplace 版本，以及 16×16、20×20 的 Light／Dark JetBrains Sidebar 完整變體。
- 新增與編輯翻譯改為同頁列出目前 namespace 的全部語言；外層使用可捲動面板，每個語言 textarea 固定保留三行／72 px 高度，不再被底部按鈕壓縮。
- 多語言表單改走單一批量 RPC：一次解析、先驗證全部 mutation、依序原子寫入並只 reload 一次；後續寫檔失敗時會嘗試還原已寫入檔案。

## 1.4.1

- 新增方案級語言內容載入預算：解析前限制單檔與方案總容量、解析過程限制單檔與方案總翻譯筆數，並在快取命中時重新驗證，降低未分類大型語言檔造成 OOM 的風險。
- IDE 新方案預設與 Tool Window 方案設定可分別調整四項載入上限；超限檔案會安全略過並顯示問題。
- JSON／YAML／PHP／Properties parser 在建立翻譯 map 時即停止超額筆數，JSON／PHP 另限制 128 層巢狀結構；過大結果不寫入磁碟 cache，避免序列化造成額外記憶體尖峰。

- 使用率 Regex 清單新增固定顯示的完整 placeholder 說明，新增／編輯視窗提供可直接修改的函式限定範例，並解釋 `(?<key>…)`、quote backreference 與非重疊 match 可能漏算的情況。
- 插件最低相容版本設為 JetBrains Platform build `253.5`（IntelliJ IDEA 2025.3.5），移除最高版本限制；記錄 Marketplace 對 2025.3.5～2026.2 RC 的相容性驗證結果。
- 顯示語言的明確選擇改由標準 ResourceBundle 依 locale 載入，修正 2025.3.5 平台忽略指定語言而回退英文的差異。
- IDE 全文搜尋改為所有格式只帶入實際 key，不再加入 PHP 檔名或 ResourceBundle bundle namespace。
- 翻譯操作新增「帶入計算次數格式於全文搜尋」：以目前 key 取代方案使用率 Regex 的 `(?<key>…)` 群組、移除最外層 `^`／`$`，並自動開啟 IDE Regex 搜尋模式。
- 修正使用率 Regex 帶入 Find in Files 時 `\Q...\E` 與反斜線可能被 IDE 二次跳脫而無法命中的問題；Regex 模式會先啟用，key 改為逐字元跳脫。
- Tool Window「新增方案」下拉選單新增「匯入方案設定」與「匯出方案設定」，使用有版本的可攜式 JSON。
- 匯出包含方案名稱、列管檔案、base path、Regex 與排除清單；專案內路徑自動轉為相對路徑，不匯出 cache、entries 或 issues。
- 匯入前顯示方案／檔案／解析路徑／識別結果預覽；缺失、超出專案根目錄或不安全路徑會禁止匯入。
- `Settings → Tools → LanguageManager` 保留穩定的插件顯示語言選項；方案設定改由 Tool Window 目前選取方案直接開啟。
- 每個方案可指定獨立的使用率掃描 base path；留白時仍使用目前 IDE 專案根目錄。
- 使用率判斷改為可編輯的 Regex 清單，支援 `(?<key>…)` 命名群組、第一個 capture group 或完整 match。
- 排除資料夾改為可編輯清單，保留 `.git`、`.idea`、`vendor`、`node_modules` 等預設項目，也可加入相對路徑。
- 儲存方案掃描設定後會淘汰該方案 cache 並在背景重新計算；舊 `schemes.json` 自動套用預設值。
- 移除 `SimpleListCellRenderer.create(...)`，避免 Plugin Verifier 的 scheduled-for-removal API 警告。
- Tool Window 原生「更多選項」保留單一「在地化管理器設定」入口，直接跳轉插件顯示語言設定頁。
- 設定快捷入口改為直接依 `Configurable` 類別定位，不再把 extension ID 當作顯示名稱搜尋；不同 IDE 顯示語言都能精準開啟插件頁面。
- Tool Window 新增「方案設定」按鈕，使用既有即時方案 state 顯示列管檔案、base path、Regex 與排除清單，不再由 Settings configurable 動態載入專案資料。
- 插件設定新增「新方案預設值」：可選目前專案或向上 1–10 層作為 base path，並持久化預設 Regex 與排除清單；檔案／資料夾兩種建立方案流程都會套用。
- 預設 Regex 改為辨識括號內引號包覆的 Unicode key；預設排除清單包含 Git、文件、依賴、Laravel storage/database、Gradle、建置產物、IDE 與 AI 設定目錄。
- 排除清單新增 `database`、`gradle`、`.gradle`、`build`、`out`、`dist`、`target`、`node_modules`、`.idea`、`.fleet`、`.vs`、`.settings`、`.metadata`、`nbproject`；舊版原廠預設自動遷移，不覆蓋自訂清單。
- 翻譯 key 不再限制為識別字格式，可使用含空格、Unicode 與標點的句子（例如 `Not powered on or not detected`）；仍禁止空白、控制字元與超過 256 字元。
- 預設使用率 Regex 同步支援含空格與標點的句子型 key。
- 翻譯表新增狀態篩選：全部顯示、缺少任一語言翻譯、使用次數為 0（可能未使用），並與搜尋、語言篩選及分頁共同作用。
- 進階設定新增隱藏「重複值」與「可能未使用」建議；隱藏後同步從問題表、狀態數量與全部批量處理中排除。

## 1.3.4

- 新增 JetBrains／Java ResourceBundle `.properties` 語言檔支援，可安全解析、編輯、正規化寫回及資料夾探索。
- 支援 base bundle 與 locale 後綴命名，例如 `LanguageManagerBundle.properties` 對應英文，`LanguageManagerBundle_zh_TW.properties` 對應繁體中文。
- 支援註解、`=`／`:`／空白分隔、續行、跳脫字元及 `\\uXXXX`；重複 key、空 key 與錯誤 Unicode escape 會回報 `PARSE_ERROR`。
- 「新增語言版本」可由 base bundle 產生例如 `LanguageManagerBundle_es.properties`，並保留相同 namespace 與 key 結構。

## 1.3.3

- 新增 `Settings → Tools → LanguageManager` 全域進階設定，可保留自動跟隨 IDE，或指定英文、繁中、簡中、日文、韓文；套用後會立即重建已開啟的插件工具視窗。
- 將所有模組的 JVM toolchain 與 bytecode target 從 Java 25 降為 IntelliJ Platform 261 要求的 Java 21，並使用 Gradle 官方 Foojay resolver 自動供應缺少的 JDK，避免 2026.1 最低執行環境無法載入插件 class。
- 移除 Kotlin 為 `ToolWindowFactory` 產生的相容橋接，排除 Plugin Verifier 回報的 4 次 deprecated API 與 6 次 experimental API 使用；並加入位元碼回歸測試。

## 1.3.2

- 修正 Marketplace 描述檔驗證：插件名稱改用合法的拉丁字元，並讓說明以前 40 字元以上的英文內容起首；IDE 內的多國語言名稱與操作介面不受影響。
- 修正翻譯表直接修改共用 renderer 顏色，造成滑鼠移動重繪時 Hover Row 誤沿用選取反色的問題。
- 整列高亮改由 JTable 原生 selection 狀態與 IDE Look & Feel 繪製，保留單一儲存格複製／貼上及 Light/Dark 主題支援。
- 翻譯編輯 Form 的 Value TextArea 起始高度調整為三行／72 px，超出內容仍可使用 ScrollPane 捲動。
- Sidebar Header 從固定 FlowLayout 改為回應式 Grid；Tool Window 變窄時控制項會自動換列並重新計算高度，避免水平擠壓或裁切。

## 1.3.1

- PHP 安全 parser 支援 Laravel 語言檔在 `return` 前使用 `declare(strict_types=1);`，修正官方簡中／繁中範例被誤判為「預期：return」。
- 仍只接受靜態 `return` array；其他 `declare` 指令、變數、函式呼叫與可執行 PHP 會繼續拒絕。

## 1.3.0

- 建立方案時可一次多選多個語言資料夾，例如 `en`、`zh_CN`、`zh_TW`，並合併識別結果。
- 資料夾識別 Popup 新增「增加資料夾」，重新掃描全部所選目錄並保留既有勾選狀態。
- 多資料夾掃描會以正規化完整路徑去重，整批最多識別 500 個支援檔案；重疊選取父目錄與子目錄不會產生重複資料。
- Laravel 目錄結構會以資料夾名稱作 locale、PHP 檔名作 namespace，讓不同語言的同名檔案可在翻譯表對照。
- 「新增語言版本」可從既有 locale 建立另一個完整語言版本，例如從 `en` 建立 `es`；一般翻譯值清空，JSON 陣列保留合法結構。
- 新語言建立前提供逐檔 Diff 預覽，確認目標檔不存在且內容未衝突後才安全建立檔案並加入目前方案。
- 編輯翻譯會直接使用目前點擊的 locale 儲存格，不再額外顯示重複的語言選擇 Popup。
- 翻譯編輯 Form 改為「標題｜輸入框」雙欄對齊版面，value 欄位可垂直延展並保留檔案切換功能。

## 1.2.1

- 修正編輯翻譯時切換目標檔案後，locale、namespace、key、value 與 entry ID 未同步更新的問題。
- 目標檔案沒有相同 key 時會顯示空白 value，確認後以新增翻譯處理，不會誤用原檔案 entry ID。
- 修正批量刪除以各語言 entry 數量取代所選 JOIN 列數，導致確認數量被放大的問題。
- 翻譯表選取任一儲存格時會高亮整列，並沿用 IDE 主題的 selection 顏色支援亮色與暗色模式。

## 1.2.0

- 「新增方案」改為下拉選單，可依單一／多個檔案或整個資料夾建立方案。
- 資料夾模式會在 backend 安全遞迴掃描 JSON、YAML/YML、PHP，並實際嘗試解析每個檔案。
- 新增識別確認視窗，顯示格式、語言、namespace、翻譯筆數與解析錯誤；只有成功識別且由使用者勾選的檔案會被納管。
- 資料夾掃描限制深度 16、最多 500 個支援格式檔案，並略過常見依賴、建置與快取目錄。

## 1.1.1

- 新增工具視窗「回報問題」外部連結，可直接開啟 GitHub Issue 建立頁面。
- 新增錯誤回報、功能需求與格式相容性三種 GitHub Issue Forms。
- 新增專案級 `AGENTS.md`，保存架構、安全、UI、多國語言、測試與 Git 工作原則。
- 新增使用者操作手冊與完整開發／需求文件導覽。

## 1.1.0

- 新增 JetBrains 標準日文（`ja`）、韓文（`ko`）與簡體中文（`zh_CN`）語言字典。
- 插件中繼資料、完整前端介面與後端診斷現在支援英文、繁體中文、簡體中文、日文及韓文。
- 多語言測試會比對所有字典與英文基準的資源鍵，避免任何按鈕、頁籤或錯誤訊息漏翻。

## 1.0.15

- 插件完整介面支援英文與繁體中文，包括工具視窗、按鈕、操作選單、頁籤、搜尋模式、表格欄位、分頁、狀態與對話框。
- Diff 預覽、剪貼簿提示、檔案選擇及問題處理流程皆改用在地化資源鍵。
- 後端解析錯誤、輸入驗證與翻譯品質建議加入英文／繁中資源包，並更新快取格式以重新產生在地化診斷。
- 新增前後端語言資源鍵一致性測試，避免翻譯漏鍵。

## 1.0.14

- 「修復/正規化」與問題處理在寫檔前顯示 IDE 雙欄 Diff 預覽，逐檔比較目前內容與修改後內容。
- 使用者可在預覽視窗選擇套用或取消；批量處理可切換檢視所有受影響檔案。
- 套用前再次驗證原檔 SHA-256，偵測預覽後的外部修改並拒絕覆蓋衝突。

## 1.0.13

- 插件名稱更新為「在地化管理器 (LanguageManager)」。
- 插件描述加入獨立的中文說明與 English Description 區段。
- 使用 JetBrains resource bundle 提供基礎英文與繁體中文插件中繼資料。

## 1.0.12

- 修正「問題與建議」表格的「處理」欄只有按鈕外觀、無法點擊互動的問題。
- 僅開放操作欄使用按鈕 editor，其餘診斷資料欄維持唯讀。

## 1.0.11

- 將翻譯表的新增、編輯、批量刪除、Key 改名與 IDE 全文搜尋收進「操作」下拉選單，縮短工具列寬度。

## 1.0.10

- 翻譯表選取任一儲存格後，可按「IDE 全文搜尋」開啟 PhpStorm 原生「在檔案中尋找」。
- 搜尋視窗會自動帶入 `namespace.key` 完整翻譯鍵，並以一般文字而非正規表示式搜尋。

## 1.0.9

- 翻譯表與問題表支援單一儲存格選取，同時保留 row 操作對象。
- `Ctrl+C` 僅複製選取儲存格；單欄多列會輸出逐行資料。
- `Ctrl+V` 可將文字貼入單一語言 value 儲存格，並拒絕貼入 Namespace、Key 或診斷欄位。

## 1.0.8

- 修正 JSON 使用完整句子作為 key 時，句點被誤判成巢狀路徑而造成的 key 階層衝突。
- JSON 寫回會保留原始 key path；字面句點與真正巢狀物件可正確區分。

## 1.0.7

- 問題與建議表加入每列「處理」按鈕。
- 支援多選批量處理與一鍵處理全部可安全修復項目。
- 空值可精準修復指定列，未使用鍵可確認後單筆或批量刪除。
- 格式錯誤可直接開啟來源檔案，其他建議可定位至 JOIN 翻譯表。

## 1.0.6

- 依據 PhpStorm client log 修正：不再於 project service constructor 同步載入方案或走訪 WSL 檔案系統。
- 既有方案改由可取消的背景 IO coroutine 載入，RPC 與工具視窗可立即完成初始化。

## 1.0.5

- 修正大型 WSL 專案建立方案時，使用率分析造成長時間無畫面回應的問題。
- 使用率分析改為單次 token 掃描，跳過 vendor、node_modules、storage、build 等目錄。
- 加入 parser cache format version，升級後會自動淘汰舊解析錯誤快取。
- 使用者操作開始時立即顯示處理中狀態，並將載入進度與錯誤寫入 IDE log。

## 1.0.4

- 翻譯表改為依 `namespace + key` JOIN，每種語言各自顯示為 value 欄位。
- 搜尋語言時仍保留相同翻譯鍵的其他語言欄位，便於並排比較。
- 加入翻頁控制，每頁最多顯示 100 個翻譯鍵。

## 1.0.3

- 支援 JSON array value；表格中以 JSON 顯示，寫回時保持 array 型別與巢狀內容。
- JSON array 可包含字串、數字、布林值、null、物件與其他陣列。

## 1.0.2

- 修正 split-mode content modules 載入不同 `kotlinx.serialization` runtime 所造成的 `KSerializer` class cast 錯誤。
- 插件不再打包 Kotlin serialization runtime，統一使用 IntelliJ Platform 提供的版本。

## 1.0.1

- 修正 IDE 未安裝 PHP、YAML 或 Properties 插件時，backend RPC provider 無法載入的問題。
- 語言格式解析器維持獨立實作，不再要求不必要的 IDE 語言插件。

## 1.0.0

- 支援 JSON、YAML 與 Laravel PHP 語言檔。
- 加入獨立方案、記憶體/磁碟快取與 split-mode RPC。
- 加入表格 CRUD、批量刪除、搜尋、語言篩選與 key 改名。
- 加入安全 parser、格式診斷、正規化修復及翻譯品質建議。
