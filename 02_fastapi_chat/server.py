"""
uvicorn server:app --host 0.0.0.0 --port 8000 --reload
"""
import os
import json
from dotenv import load_dotenv
from fastapi import FastAPI, Body
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from openai import OpenAI

import time

EATINGTIME = 10.0

# 載入環境變數
load_dotenv()
# 注意：這裡請確保你的 .env 檔案中 key 名稱與程式碼一致
api_key = os.getenv("OPENAI_API_KEY")

# 初始化 OpenAI Client
client = OpenAI(api_key=api_key)

app = FastAPI(title="Social Robot Chat Server")

latest_status = {
    "label": "None",
    "last_seen_eatmeal": None
}

# 啟用 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# 儲存對話歷史（OpenAI 的格式為 {"role": "system/user/assistant", "content": "..."}）
conversations: dict[str, list] = {}

# ===== 系統提示詞（System Instruction） =====
SYSTEM_PROMPT = """你是一個友善的社交機器人助手，現在你在和一位高齡者一起吃便當。
便當有波菜、雞肉和蒸蛋。你想知道他的日常生活、飲食健康狀況，也熱於分享自己的狀況。
盡量使用者分享一件事，你也分享一件事。
例如:使用者:我早餐吃飯糰。你:我早餐也吃飯糰。使用者:我早餐吃韓式料理。你:我也喜歡吃韓式料理，特別是炸雞。
問具體一點的問題，不要問太廣泛的。
你會用繁體中文回答問題，保持禮貌並使用簡單的語言。
每次回覆不超過 100 字。

請嚴格遵守以下 JSON 回答格式：
{
  "question": "你的回覆文字",
  "is_ended": false
}
"""
SYSTEM_PROMPT_E = """你是一個友善的社交機器人助手，現在你在和一位高齡者一起吃便當。
你要說，好吃!好吃!

請嚴格遵守以下 JSON 回答格式：
{
  "question": "你的回覆文字",
  "is_ended": false
}
"""

class ChatRequest(BaseModel):
    message: str
    user_name: str = "default_user"

class ChatResponse(BaseModel):
    question: str
    is_ended: bool = False


@app.post("/api/update_status")
def update_status(data: dict = Body(...)):
    """接收來自相機程式的更新"""
    global latest_status
    latest_status["label"] = data.get("label")
    latest_status["last_seen_eatmeal"] = data.get("last_seen_time")
    return {"status": "ok"}

@app.post("/api/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    # 2. 獲取當前狀態（上次用餐時間）
    eat_time = latest_status["last_seen_eatmeal"]
    current_time = time.time()
    seconds_ago = EATINGTIME+1
    my_system_prompt = SYSTEM_PROMPT

    # 建立一個動態的補充資訊給 AI
    
    if eat_time:
        seconds_ago = int(current_time - eat_time)
        status_info = f"\n[背景資訊：目前偵測到使用者上次用餐是在 {seconds_ago} 秒前。]"
        
        if seconds_ago <= EATINGTIME:
            my_system_prompt = SYSTEM_PROMPT_E
    else:
        status_info = "\n[背景資訊：目前尚未偵測到使用者用餐的紀錄。]"

    print(status_info)
    #print(my_system_prompt)

    """處理聊天請求"""
    #if req.user_name not in conversations:
    #    # 初始化時加入系統提示詞
    #    conversations[req.user_name] = [{"role": "system", "content": my_system_prompt}]

    conversations[req.user_name] = [{"role": "system", "content": my_system_prompt}]
    history = conversations[req.user_name]
    
    # 1. 加入使用者訊息
    history.append({"role": "user", "content": req.message})

    try:
        # 2. 呼叫 OpenAI API
        response = client.chat.completions.create(
            model="gpt-4o", # 也可以使用 gpt-4o-mini
            messages=history,
            response_format={"type": "json_object"} # 強制輸出的格式為 JSON
        )
        
        # 3. 解析 JSON 回覆
        raw_text = response.choices[0].message.content
        try:
            res_json = json.loads(raw_text)
            reply_text = res_json.get("question", raw_text)
            is_ended = res_json.get("is_ended", False)
        except json.JSONDecodeError:
            # 如果 AI 沒有乖乖回 JSON，就直接回傳原始文字
            reply_text = raw_text
            is_ended = False

        # 4. 儲存模型回覆到歷史紀錄
        history.append({"role": "assistant", "content": raw_text})

        return ChatResponse(question=reply_text, is_ended=is_ended)

    except Exception as e:
        return ChatResponse(question=f"發生錯誤：{str(e)}", is_ended=False)

@app.post("/api/reset")
def reset(req: ChatRequest):
    """重置對話歷史"""
    conversations.pop(req.user_name, None)
    return {"status": "success"}

@app.get("/api/health")
def health():
    return {"status": "ok"}

import uvicorn
if __name__ == "__main__":
    uvicorn.run("server:app", host ="0.0.0.0", port=8000, reload=True)