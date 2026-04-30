import os
import json
import uuid
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
from openai import OpenAI
import time

EATINGTIME = 10.0

# 載入環境變數
load_dotenv()
OPENAI_API_KEY = os.getenv('OPENAI_API_KEY')

# 初始化全域變數
latest_status = {
    "label": "None",
    "last_seen_eatmeal": None
}

app = Flask(__name__)
CORS(app)  # 啟用CORS支援，允許前端呼叫

# 確保資料目錄存在
os.makedirs('data/conversations', exist_ok=True)

# 讀取介紹文本
try:
    with open('intro.txt', 'r', encoding='utf-8') as f:
        intro_text = f.read()
except FileNotFoundError:
    intro_text = "你好！我是凱比。很開心今天要跟你一起吃午餐。"
    with open('intro.txt', 'w', encoding='utf-8') as f:
        f.write(intro_text)

# OpenAI客戶端
client = OpenAI(api_key=OPENAI_API_KEY)

'''
***可以改地方***
'''
# 系統提示模板
system_prompt_general = """你是一個友善的社交機器人助手，現在你在和一位高齡者一起吃便當。
便當的菜色是{intro}。你想知道他的日常生活、飲食健康狀況，也熱於分享自己的狀況。
盡量使用者分享一件事，你也分享一件事。
例如:使用者:我早餐吃飯糰。你:我早餐也吃飯糰。使用者:我早餐吃韓式料理。你:我也喜歡吃韓式料理，特別是炸雞。
問具體一點的問題，不要問太廣泛的。
每次回覆不超過 100 字。

你們的過去對話為{context}。
你要用繁體中文回答問題，保持禮貌並使用簡單的語言，讓參與者感到輕鬆愉快。

回答的格式為 json 格式，並包含：
1. question(string) - 你的回覆文字
2. is_ended(bool) - false
"""

interview_prompt_eating = """你是一個友善的社交機器人助手，現在你在和一位高齡者一起吃便當。
便當的菜色是{intro}。你想知道他的日常生活、飲食健康狀況，也熱於分享自己的狀況。
盡量使用者分享一件事，你也分享一件事。
例如:使用者:我早餐吃飯糰。你:我早餐也吃飯糰。使用者:我早餐吃韓式料理。你:我也喜歡吃韓式料理，特別是炸雞。
問具體一點的問題，不要問太廣泛的。

另外，你看到他才剛剛吃一口食物，所以你打算多說一點話之後再換他說話。

你們的過去對話為{context}。
你要用繁體中文回答問題，保持禮貌並使用簡單的語言，讓參與者感到輕鬆愉快。

回答的格式為 json 格式，並包含：
1. question(string) - 你的回覆文字
2. is_ended(bool) - false
"""


def get_user_data(user_name):
    """獲取用戶資料，如果不存在則初始化"""
    file_path = f"data/conversations/{user_name}.json"
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        # 如果檔案不存在或JSON無效，初始化新資料
        user_data = {
            "turns": 0,
            "conversation": [],
            "is_ended": False
        }
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(user_data, f, ensure_ascii=False, indent=2)
        return user_data

def save_message(user_name, speaker, message):
    """儲存訊息並更新用戶資料"""
    user_data = get_user_data(user_name)
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    user_data["conversation"].append({
        "timestamp": timestamp,
        "speaker": speaker,
        "message": message
    })
    
    file_path = f"data/conversations/{user_name}.json"
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(user_data, f, ensure_ascii=False, indent=2)

'''
***可以改地方***
'''
def jxw_bot(user_name, question):
    """與AI互動，生成回覆"""
    # 獲取用戶資料
    user_data = get_user_data(user_name)
            
    # 生成對話歷史文本
    history = ""
    for msg in user_data["conversation"]:
        history += f"{msg['timestamp']} | {msg['speaker']}: {msg['message']}\n"
    
    turns=user_data.get("turns", 0)
    if(turns<=2):
        # 知識問答環節
        system_prompt = system_prompt_general.format(
            intro=intro_text, 
            context=history,
            turns=user_data.get("turns", 0)
        )
    elif(turns>2 and turns<=9):
        # 使用訪談提示
        system_prompt = interview_prompt_eating.format(
            intro=intro_text,
            context=history, 
            turns=user_data.get("turns", 0)
        )
    elif(turns>=30):
        # 如果對話結束，返回結束訊息
        return {
            "question": "謝謝你今天來！我差不多吃飽了。期待下次再見！",
            "is_ended": True
        }
    
    # 生成AI回覆
    try:
        completion = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": "參與者回應道：" + question},
            ],
        )
        
        raw = completion.choices[0].message.content
        # 嘗試解析JSON
        try:
            # 移除可能的Markdown格式
            json_text = raw.replace('```json', '').replace('```', '').strip()
            data = json.loads(json_text)
            
            # 更新用戶資料
            if data.get("is_ended", False):
                user_data["is_ended"] = True
            user_data["turns"] += 1
        
            with open(f"data/conversations/{user_name}.json", 'w', encoding='utf-8') as f:
                json.dump(user_data, f, ensure_ascii=False, indent=2)
                
            return data
        except json.JSONDecodeError:
            # 如果無法解析JSON，返回原始文本作為問題
            return {
                "question": raw,
                "is_ended": False
            }
    except Exception as e:
        print(f"錯誤: {e}")
        return {
            "question": "抱歉，我遇到了一些問題，請稍後再試。",
            "is_ended": False
        }

def eat_bot(user_name, question):
    """與AI互動，生成回覆"""
    # 獲取用戶資料
    user_data = get_user_data(user_name)
            
    # 生成對話歷史文本
    history = ""
    for msg in user_data["conversation"]:
        history += f"{msg['timestamp']} | {msg['speaker']}: {msg['message']}\n"
    
    turns=user_data.get("turns", 0)

    eat_time = latest_status["last_seen_eatmeal"]
    current_time = time.time()
    seconds_ago = EATINGTIME+1
    system_prompt = system_prompt_general.format(
            intro=intro_text, 
            context=history,
            turns=user_data.get("turns", 0)
        )


    if eat_time:
        seconds_ago = int(current_time - eat_time)
        status_info = f"\n[背景資訊：目前偵測到使用者上次用餐是在 {seconds_ago} 秒前。]"
        
        if seconds_ago <= EATINGTIME:
            system_prompt = interview_prompt_eating.format(
            intro=intro_text,
            context=history, 
            turns=user_data.get("turns", 0)
        )

    else:
        status_info = "\n[背景資訊：目前尚未偵測到使用者用餐的紀錄。]"

    print(status_info)
    

    
    
    # 生成AI回覆
    try:
        completion = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": "參與者回應道：" + question},
            ],
        )
        
        raw = completion.choices[0].message.content
        # 嘗試解析JSON
        try:
            # 移除可能的Markdown格式
            json_text = raw.replace('```json', '').replace('```', '').strip()
            data = json.loads(json_text)
            
            # 更新用戶資料
            if data.get("is_ended", False):
                user_data["is_ended"] = True
            user_data["turns"] += 1
        
            with open(f"data/conversations/{user_name}.json", 'w', encoding='utf-8') as f:
                json.dump(user_data, f, ensure_ascii=False, indent=2)
                
            return data
        except json.JSONDecodeError:
            # 如果無法解析JSON，返回原始文本作為問題
            return {
                "question": raw,
                "is_ended": False
            }
    except Exception as e:
        print(f"錯誤: {e}")
        return {
            "question": "抱歉，我遇到了一些問題，請稍後再試。",
            "is_ended": False
        }


@app.route("/api/update_status", methods=["POST"])
def update_status():
    """接收來自相機程式的更新"""
    global latest_status
    data = request.json  # Flask 取得 JSON 的方式
    if not data:
        return jsonify({"status": "error", "message": "No data"}), 400
        
    latest_status["label"] = data.get("label")
    latest_status["last_seen_eatmeal"] = data.get("last_seen_time")
    return jsonify({"status": "ok"})

@app.route('/api/chat', methods=['POST'])
def chat():
    """處理聊天請求"""
    data = request.json
    user_message = data.get('message', '')
    user_name = data.get('user_name')

    
    # 驗證必要參數
    if not user_message:
        return jsonify({"error": "訊息不能為空"}), 400
    if not user_name:
        return jsonify({"error": "用戶名稱不能為空"}), 400
    timestamp_1 = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{timestamp_1}| 使用者 {user_name} 發送訊息: {user_message}")
    # 儲存用戶訊息
    save_message(user_name, "user", user_message)
    
    # 獲取機器人回覆
    response = eat_bot(user_name, user_message)
    timestamp_2 = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"{timestamp_2}| 機器人回覆: {response['question']}")
    # 儲存機器人回覆
    save_message(user_name, "bot", response["question"])
    
    return jsonify(response)

@app.route('/api/reset', methods=['POST'])
def reset_chat():
    """重置聊天"""
    data = request.json
    user_name = data.get('user_name')
    
    if not user_name:
        return jsonify({"error": "用戶名稱不能為空"}), 400
    
    # 重置用戶資料
    user_data = {
        "turns": 0,
        "conversation": [],
        "is_ended": False
    }
    
    with open(f"data/conversations/{user_name}.json", 'w', encoding='utf-8') as f:
        json.dump(user_data, f, ensure_ascii=False, indent=2)
    
    return jsonify({"status": "success"})

@app.route('/api/create_user', methods=['POST'])
def create_user():
    """創建新用戶"""
    new_user_name = str(uuid.uuid4())
    
    # 初始化用戶資料
    user_data = {
        "turns": 0,
        "conversation": [],
        "is_ended": False
    }
    
    with open(f"data/conversations/{new_user_name}.json", 'w', encoding='utf-8') as f:
        json.dump(user_data, f, ensure_ascii=False, indent=2)
    
    return jsonify({"user_name": new_user_name})

if __name__ == '__main__':
    # Flask 內建伺服器
    app.run(host='0.0.0.0', port=8000, debug=True)
