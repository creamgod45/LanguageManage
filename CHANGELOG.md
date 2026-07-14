# Language Manager Changelog

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
