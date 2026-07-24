[English](performance_memory.md) | [繁體中文](performance_memory.zh.md)

# 記憶體與大型方案驗證

本專案提供可重現的回歸測試，不以理論物件大小取代實測：

```powershell
$env:JAVA_HOME = (Resolve-Path '.intellijPlatform/ides/IU-2025.3.5/jbr').Path
.\gradlew.bat :backend:test --tests cg.creamgod45.LocalizationMemoryFootprintTest --rerun-tasks
```

機器可讀的量測結果會產生於：

```text
backend/build/reports/language-manager-memory/12-language.properties
```

## 實測案例

測試會產生 12 個真實 JSON 語言檔與一個真實程式碼來源檔：

- 每個語言 8,000 個 key，合計解析 96,000 筆翻譯。
- 來源呼叫不是 mock；8,000 個呼叫全部交由正式環境的 Regex 使用率掃描器處理。
- 每個邏輯 key 對應 12 個語言 entry，因此掃描器實際產生並驗證 96,000 筆使用位置。
- 12 個語言檔共 7,149,396 bytes（6.82 MiB）。每個檔案都低於預設 2,048 KiB 單檔上限，總量低於預設 20 MiB 與 100,000 筆方案上限。
- 使用正式的 `LanguageFileCodec`、`UsageScanSupport`、DTO 與 RPC 序列化資料形狀。

在啟用壓縮物件參照的 JetBrains Runtime 21 上，參考結果如下：

| 量測項目 | 優化前 | backend 按需分頁後 | 減少 |
| --- | ---: | ---: | ---: |
| Frontend 保留物件圖 | 29,377,744 bytes（28.02 MiB） | 24,385,536 bytes（23.26 MiB） | 4,992,208 bytes（4.76 MiB） |
| 完整狀態 RPC JSON payload | 50,621,862 bytes（48.28 MiB） | 28,952,053 bytes（27.61 MiB） | 21,669,809 bytes（20.67 MiB） |

Backend 的翻譯 entry 加使用位置保留物件圖為 29,377,688 bytes（28.02 MiB）。使用位置仍須保留在 backend 供跳轉使用，但不再複製到每一份 frontend 狀態，也不會隨一般狀態 RPC 更新反覆傳輸。選取 key 後，每頁只請求最多 100 筆去重後的位置。

## 數據能證明的範圍

測試會先斷言實際解析筆數與掃描位置筆數，再比較優化前後；若 frontend 物件圖或 RPC payload 沒有變小，測試會直接失敗。JOL 量測的是 Java 保留物件圖，不代表解析瞬間峰值、整個 IDE、Swing 繪製或 JVM metadata。不同 JBR 與壓縮參照設定會讓絕對 bytes 有差異，但同一 runtime 上的回歸比較可以重現。

解析峰值另由以下機制限制：讀取前檢查檔案 bytes、解析期間執行單檔與方案 entry 上限、方案內容總量預算、巢狀深度限制、單檔 parser 例外隔離，以及舊載入工作的協作式取消。
