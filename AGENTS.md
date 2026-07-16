# LanguageManage Project Instructions

## Communication

- 與專案擁有者溝通時預設使用繁體中文。
- 回報結果時先說明完成項目、測試結果與安裝包位置，再補充必要技術細節。
- 能從程式碼與本機官方範例確認的事項直接處理；只有會實質改變需求方向時才提問。

## Product principles

- 插件只管理使用者在方案中明確選取的語言檔，不得自動納管檔案。
- 每個方案必須彼此隔離；任何 mutation 都只能寫入該方案的檔案。
- 支援 JSON、YAML/YML、Laravel PHP 靜態 `return` array 與 JetBrains/Java ResourceBundle Properties。
- PHP 內容只能 parse，禁止 eval、include、執行函式或外部程序。
- 翻譯表以 `namespace + key` JOIN，多語言各為獨立欄位，每頁上限 100 列。
- 選取單一 cell 時，row action 必須映射到該 cell 所屬 row；複製單一 cell 不得輸出整列。
- 低頻或數量較多的操作集中於 dropdown，避免工具列按鈕過長。
- 使用率掃描設定以方案隔離，支援安全 base path、Regex key 擷取與可編輯排除清單；舊方案需保留預設值相容性。
- 問題表必須提供單列與批量操作；可自動修改的操作需先提供 Diff 預覽。
- 修復、正規化與問題自動處理在寫入前必須顯示 before/after，並以 SHA-256 防止預覽後覆蓋衝突。

## Architecture

- 維持 `shared`、`frontend`、`backend` split-mode 邊界。
- `shared` 只放 RPC 契約、可序列化 DTO 與不依賴 IDE UI 的純邏輯。
- `frontend` 負責 Swing UI、EDT、tool window、Diff 與 RPC repository，不直接操作 backend 檔案。
- `backend` 負責 project service、IO、parser、cache、分析與 RPC 實作。
- Backend IO 使用 `Dispatchers.IO`；同 project 的 mutation 使用 mutex 序列化。
- UI 狀態透過 RPC `Flow`／`StateFlow` 推送，不新增輪詢機制。
- 不在 plugin module 重複打包 IntelliJ Platform 已提供的 Kotlin／serialization runtime。

## Security and file handling

- 所有來源檔讀取都要捕捉 parser／IO 例外，單檔失敗不得拖垮整個方案。
- 僅接受一般本機檔案；拒絕 URI、LDAP、`file:`、Windows device path、`GLOBALROOT` 與控制字元。
- 維持副檔名、檔案大小、輸入長度、locale、namespace 與 key 白名單驗證。
- 所有輸出依 JSON、YAML、PHP、Properties 格式正確跳脫。
- 寫檔使用 temporary file + atomic move，不直接覆寫半成品。
- 錯誤訊息顯示前移除危險控制字元並限制長度。

## UI and localization

- 所有使用者可見文字都必須進 resource bundle，不可新增硬編碼 UI 文案。
- Base 英文、`zh_TW`、`zh_CN`、`ja`、`ko`、`es`、`th` 七套字典必須同步新增相同 key。
- 插件中繼資料、frontend UI 與 backend 診斷三層字典都要考慮。
- 新增或修改字典後更新 bundle parity tests。
- 使用 JetBrains 原生元件與 API，例如 Diff viewer、Find in Files、ActionLink、Messages。

## Testing and delivery

- 新功能需加入與風險相稱的 test case；parser、安全、RPC DTO、搜尋及語言字典不可只靠手動驗證。
- 交付前至少執行 `test` 與 `buildPlugin`，確認 ZIP 含 shared/frontend/backend module 及所有語言 bundle。
- 功能更新需同步 `CHANGELOG.md`；架構、API 或操作方式改變時同步 README 與 `docs/`。
- 發布版本使用不含 `SNAPSHOT` 的語意版本號；一般功能修正遞增 patch，明顯功能集合遞增 minor。

## Git workflow

- 保留使用者既有修改，不使用 destructive reset 或 checkout。
- 使用者要求同步時先 `git pull --rebase --autostash`，處理完衝突再繼續。
- Commit 前確認不包含 `build/`、`.idea/`、log、ZIP 或客戶端診斷壓縮檔。
- 只有使用者明確要求時才 commit／push；commit message 使用清楚的 Conventional Commit 風格。
