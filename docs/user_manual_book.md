# 在地化管理器使用者操作手冊

本手冊說明如何在 JetBrains IDE 中使用「在地化管理器 (LanguageManager)」建立語言方案、管理翻譯、檢查問題及安全套用修復。

> 插件不會自動選取、納管或修改任何語言檔。只有加入目前方案的檔案才會被讀取或寫入。

## 1. 支援內容

### 支援格式

- JSON：`.json`
- YAML：`.yaml`、`.yml`
- Laravel PHP：`.php`，內容可有 `declare(strict_types=1);`，之後必須是靜態 `return [...]` 或 `return array(...)`

### 介面語言

插件會跟隨 IDE 顯示語言，目前提供：

- English
- 繁體中文
- 简体中文
- 日本語
- 한국어

## 2. 安裝插件

### 從 ZIP 安裝

1. 開啟 IDE 設定。
2. 前往 **Plugins**。
3. 點擊齒輪圖示，選擇 **Install Plugin from Disk…**。
4. 選擇 `LanguageManage-{version}.zip`，不要先解壓縮。
5. 依 IDE 提示重新啟動。

安裝後，IDE 右側工具視窗列會出現「在地化管理器」圖示。

## 3. 建立第一個方案

1. 開啟「在地化管理器」工具視窗。
2. 點擊「新增方案」下拉選單。
3. 選擇建立方式：
   - 「依檔案選取」：在 file chooser 中選擇一個或多個 JSON、YAML/YML、PHP 語言檔，再輸入方案名稱。
   - 「依資料夾選取」：一次選擇一個或多個資料夾，例如 `en`、`zh_CN`、`zh_TW`，等待 backend 合併掃描並嘗試解析支援格式檔案。
4. 資料夾識別視窗會顯示完整路徑、格式、locale、namespace、entry 筆數與識別結果。解析失敗的檔案會保留錯誤原因，但無法勾選；如有遺漏，可點擊「增加資料夾」重新合併掃描。
5. 輸入容易辨識的方案名稱，例如「網站前台」或「Admin API」，確認要列管的可識別檔案後點擊「建立方案」。
6. 等待狀態列完成讀取；翻譯表會顯示解析結果。

資料夾模式整批最多檢查 500 個支援格式檔案、每個根目錄遞迴深度最多 16 層，並略過 `.git`、`.idea`、`vendor`、`node_modules`、`build`、`storage`、`cache` 等常見非來源目錄。若同時選到父目錄和子目錄，會依正規化完整路徑去重。掃描只提供候選清單，不會自動納管未經確認的檔案。

方案的特性：

- 每個方案擁有獨立檔案清單與快取。
- 切換方案不會混入其他方案的資料。
- 刪除方案不會刪除來源語言檔。
- 建立方案前可在資料夾識別 Popup 增加其他資料夾；方案建立後若要變更列管範圍，請建立新方案，插件不會自動加入檔案。

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

1. 選擇要寫入的方案檔案。
2. 確認由檔案推導出的語言與 namespace。
3. 輸入 key 與 value。
4. 確認後插件會安全寫回檔案並重新載入方案。

### 新增語言版本

1. 點擊「操作 ▾」→「新增語言版本」。
2. 選擇來源語言，例如 `en`。
3. 輸入新語言代碼，例如西班牙文 `es`。
4. 插件會依來源語言的全部檔案建立目標清單：Laravel `en/auth.php` 會對應為 `es/auth.php`；`en.json` 會對應為 `es.json`。
5. 一般翻譯 value 會清空以等待翻譯；JSON array 會保留原結構，避免產生無法解析的檔案。
6. 在 Diff 視窗逐一確認新檔案，按下套用後才會建立檔案並加入目前方案。

若目標 locale 已存在、目標檔案已存在、來源檔案解析失敗或多個來源對應到同一路徑，插件會停止建立，不會覆寫既有檔案。

### 編輯所選

1. 選擇翻譯表中的任一 cell。
2. 點擊「操作 ▾」→「編輯所選」。
3. 若點擊 locale value 欄，插件會直接編輯該語言，不再額外顯示語言選擇 Popup；若點擊 Namespace 或 Key 欄，則使用該列第一個既有語言。
4. 編輯 Form 以「標題｜輸入框」雙欄排列；在視窗切換檔案時，locale、namespace 與 value 會更新為該檔案中相同 key 的內容，若尚無相同 key，value 會清空。
5. 修改 key 或 value 後確認；儲存目標是目前選取的檔案。

### 批量刪除

1. 選擇一列或多列。
2. 點擊「批量刪除」。
3. 確認視窗顯示的是所選翻譯鍵列數，不會將各 locale entry 重複計數。
4. 確認後，所選列包含的所有語言 entries 會從來源檔刪除。

### Key 改名

1. 選擇一個翻譯 key。
2. 點擊「Key 改名」。
3. 輸入新 key。
4. 方案內所有含有舊 key 的語言檔會一起改名。

若任一檔案已有新 key，操作會拒絕寫入以避免覆蓋。

### IDE 全文搜尋

選擇一列後點擊「IDE 全文搜尋」，插件會開啟 IDE 原生 **Find in Files**，並自動帶入完整的 `namespace.key`。

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
- 將可解析內容依標準 JSON、YAML 或 PHP 格式重新輸出。
- 無法解析的檔案不會被猜測修復或寫回。

## 9. 格式注意事項

### JSON

- 根節點必須是 object。
- 巢狀 object 在表格中以點號 key 顯示。
- Array value 在 cell 中以 JSON 文字顯示，寫回時仍保持 array。
- 完整句子作為 key 時，句點會保留為字面 key，不會自動變成巢狀 object。

### YAML

- 使用空白縮排，不可使用 tab。
- 支援單引號、雙引號與一般行尾註解。
- 正規化可能重新排列引號與縮排格式，請先檢查 Diff。

### Laravel PHP

- 僅接受可選的 `declare(strict_types=1);` 與靜態 return array。
- 支援字串、數字、布林與巢狀 array。
- 不支援其他 `declare` 指令、函式呼叫、變數、字串串接或任意 expression。
- 插件不會執行 PHP。

## 10. 快取與重新讀取

方案與快取存放於：

```text
.idea/language-manager/
├── schemes.json
└── cache-{schemeId}.json
```

- 一般切換方案時，檔案 fingerprint 未改變就使用 cache。
- 點擊「重新讀取」會強制重新解析。
- 來源檔變更、cache format 升級或 fingerprint 不符時會重建 cache。
- 不建議手動編輯 cache；刪除 cache 不會刪除來源語言檔。

## 11. 疑難排解

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
- 掃描會跳過 vendor、node_modules、build、storage 等目錄。
- 「0」代表掃描未找到，不等同確定未使用。

## 12. 回報問題

工具視窗上方提供「回報問題」連結，會開啟：

[https://github.com/creamgod45/LanguageManage/issues/new](https://github.com/creamgod45/LanguageManage/issues/new)

請選擇適合的 Issue 類型：

- 錯誤回報：可重現的 UI、RPC、解析、寫入或效能問題。
- 功能需求：新的操作、分析、格式或 IDE 整合需求。
- 格式相容性：JSON、YAML、Laravel PHP 無法 parse／round-trip 的最小案例。

提交前請移除 log、路徑與語言檔中的密碼、token、客戶名稱及其他敏感資料。
