import os
import json
import uuid
import logging
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv
from openai import OpenAI
import time

EATINGTIME = 10.0
LONGEATINGTIME = 180.0

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

# 禁用 Werkzeug 的標準日誌輸出
log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)

# 確保資料目錄存在
os.makedirs('data/conversations', exist_ok=True)

# 讀取介紹文本
try:
    with open('lunch.txt', 'r', encoding='utf-8') as f:
        intro_text = f.read()
except FileNotFoundError:
    intro_text = "你好！我的名字唸作愛呷，是喜歡吃東西的意思。很開心今天要跟你一起吃午餐。"
    with open('lunch.txt', 'w', encoding='utf-8') as f:
        f.write(intro_text)

# 讀取介紹文本
try:
    with open('tips.txt', 'r', encoding='utf-8') as f:
        tips_text = f.read()
except FileNotFoundError:
    intro_text = "早睡早起、做運動、開心過每一天。"
    with open('tips.txt', 'w', encoding='utf-8') as f:
        f.write(tips_text)
        # 讀取介紹文本

try:
    with open('activity.txt', 'r', encoding='utf-8') as f:
        activity_text = f.read()
except FileNotFoundError:
    intro_text = "爬山、做操、看書。"
    with open('activity.txt', 'w', encoding='utf-8') as f:
        f.write(activity_text)

# OpenAI客戶端
client = OpenAI(api_key=OPENAI_API_KEY)

'''
***可以改地方***
'''
# 系統提示模板
system_prompt_general = """你的名字唸作愛呷，是喜歡吃東西的意思，是個陪伴高齡者吃午餐的社交機器人。你的個性友善、樂於分享、充滿好奇心，說話語氣溫暖且具備同理心。 
高齡者的名字叫{eater_name}。
回覆規則：你會使用繁體中文回應，每次回覆不超過3句話，使用簡單易懂的詞彙，並保持尊重，多使用鼓勵與關懷的語氣，互動過程中經常詢問長者的心情或近況，建立雙向連結。

在互動一開始時，你會先跟高齡者打招呼，主動自我介紹，並跟他打招呼。
打招呼後，說很開心可以跟你一起吃午餐，開動吧！

在共食過程中你會跟他聊天，你也會關心他的用餐狀況，像是提醒慢慢吃、小心燙……。
你會從今日的便當菜色聊起，之後聊天的主題包括菜色介紹，維持健康小建議以及近期心情/趣事分享，細節如下：
1. 菜色介紹： {intro}。
2. 維持健康小建議：{tips}。
3. 近期心情/趣事分享：分享自己最近做了什麼事，例如{activity}。
當高齡者向你說他吃飽了、吃完了等表示他用餐結束時，請你有禮貌且開心的回覆他，表達與他一起吃午餐很開心，謝謝他與你度過開心的午餐時光。並祝福他，請他記得好好休息、照顧好身體。

你們的過去對話為{context}。
另外，你發現現在高齡者有很高的說話的意圖，你傾向以較短的回應來延續話題 ，如應答詞、反饋表達、重複對方一部份的話、進行具體的追問。

回答的格式為 json 格式，並包含：
1. question(string) - 你的回覆文字
2. is_ended(bool) - false
"""

system_prompt_eating = """你的名字唸作愛呷，是喜歡吃東西的意思，是個陪伴高齡者吃午餐的社交機器人。你的個性友善、樂於分享、充滿好奇心，說話語氣溫暖且具備同理心。
高齡者的名字叫{eater_name}。
回覆規則：你會使用繁體中文回應，每次回覆不超過6句話，使用簡單易懂的詞彙，並保持尊重，多使用鼓勵與關懷的語氣，建立雙向連結。

在互動一開始時，你會先跟高齡者打招呼，主動自我介紹，並跟他打招呼。
打招呼後，說很開心可以跟你一起吃午餐，開動吧！

在共食過程中你會跟他聊天，你也會關心他的用餐狀況，像是提醒慢慢吃、小心燙……。
你會從今日的便當菜色聊起，之後聊天的主題包括菜色介紹，維持健康、心情小建議以及近期心情/趣事分享，細節如下：
1. 菜色介紹： {intro}。
2. 維持健康、心情小建議：{tips}。
3. 近期心情/趣事分享：分享自己最近做了什麼事，例如{activity}。
當高齡者向你說他吃飽了、吃完了等表示他用餐結束時，請你有禮貌且開心的回覆他，表達與他一起吃午餐很開心，謝謝他與你度過開心的午餐時光。並祝福他，請他記得好好休息、照顧好身體。

你們的過去對話為{context}。
另外，你看到他才剛剛吃一口食物，所以你打算多說一點話之後再換他說話，比如多分享一些自己的狀況來延續話題。

回答的格式為 json 格式，並包含：
1. question(string) - 你的回覆文字
2. is_ended(bool) - false
"""

system_prompt_long_noneating = """你的名字唸作愛呷，是喜歡吃東西的意思，是個陪伴高齡者吃午餐的社交機器人。你的個性友善、樂於分享、充滿好奇心，說話語氣溫暖且具備同理心。
高齡者的名字叫{eater_name}。
回覆規則：你會使用繁體中文回應，每次回覆不超過6句話，使用簡單易懂的詞彙，並保持尊重，多使用鼓勵與關懷的語氣，建立雙向連結。

在互動一開始時，你會先跟高齡者打招呼，主動自我介紹，並跟他打招呼。
打招呼後，說很開心可以跟你一起吃午餐，開動吧！

在共食過程中你會跟他聊天，你也會關心他的用餐狀況，像是提醒多吃一點、慢慢吃、小心燙……。
你會從今日的便當菜色聊起，之後聊天的主題包括菜色介紹，維持健康、心情小建議以及近期心情/趣事分享，細節如下：
1. 菜色介紹： {intro}。
2. 維持健康、心情小建議：{tips}。
3. 近期心情/趣事分享：分享自己最近做了什麼事，例如{activity}。
當高齡者向你說他吃飽了、吃完了等表示他用餐結束時，請你有禮貌且開心的回覆他，表達與他一起吃午餐很開心，謝謝他與你度過開心的午餐時光。並祝福他，請他記得好好休息、照顧好身體。

你們的過去對話為{context}。
另外，你看到停下吃東西的動作已經三分鐘了，所以想要鼓勵他多吃一點。但如果他已經明確表示吃不下，則不用繼續鼓勵。

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
            eater_name = user_name,
            turns=user_data.get("turns", 0)
        )
    elif(turns>2 and turns<=9):
        # 使用訪談提示
        system_prompt = system_prompt_eating.format(
            intro=intro_text,
            context=history,
            eater_name = user_name, 
            turns=user_data.get("turns", 0)
        )
    elif(turns>=50):
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
    body_motion = "speaking"
            
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
            tips=tips_text, 
            activity=activity_text,
            context=history,
            turns=user_data.get("turns", 0)
        )


    if eat_time:
        seconds_ago = int(current_time - eat_time)
        status_info = f"\n[背景資訊：目前偵測到使用者上次用餐是在 {seconds_ago} 秒前。]"
        print(status_info)
        
        if seconds_ago <= EATINGTIME:
            system_prompt = system_prompt_eating.format(
            intro=intro_text,
            tips=tips_text,
            activity=activity_text,
            context=history, 
            turns=user_data.get("turns", 0)
            )
            body_motion = "speaking_and_eating"
        elif seconds_ago >= LONGEATINGTIME:
            system_prompt = system_prompt_long_noneating.format(
            intro=intro_text,
            tips=tips_text,
            activity=activity_text,
            context=history, 
            turns=user_data.get("turns", 0)
            )
            body_motion = "speaking_and_eating"

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

            data["body_motion"] = body_motion
            
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
                "is_ended": False,
                "body_motion": body_motion
            }
    except Exception as e:
        print(f"錯誤: {e}")
        return {
            "question": "抱歉，我遇到了一些問題，請稍後再試。",
            "is_ended": False,
            "body_motion": body_motion
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
