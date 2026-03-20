# 磯永吉問答機器人設計

## Frontend MVVM & Single Flow 設計原則

## 🏗️ MVVM 架構概述

MVVM (Model-View-ViewModel) 是一種將 UI 邏輯與業務邏輯分離的架構模式，特別適合 Android 開發。

### 核心組件

```shell
View (UI層) ↔ ViewModel (邏輯層) ↔ Model (資料層)
```

- **View**: Fragment/Activity，負責 UI 顯示
- **ViewModel**: 處理 UI 邏輯，管理 LiveData
- **Model**: Repository/DataSource，處理資料存取

---

## 後端 LLM Chatbot 設計架構

## 🏛️ 整體架構概述

```shell
Flask Server (Python) → OpenAI GPT-4 API → 教育問答系統
```

### 核心特色

- **教育導向**：專為磯永吉小屋展覽設計的問答系統
- **階段式互動**：知識問答 + 體驗收集的兩階段設計
- **狀態管理**：追蹤對話進度和使用者回應
- **個人化回饋**：根據答案正確性提供不同回應

---
