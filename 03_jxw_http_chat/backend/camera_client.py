# camera_client.py
import cv2
import requests
import time
from eat_classifier import EatingModel
import json  # 記得引入 json 模組

def get_camera_index():
    """
    偵測可用的鏡頭：優先嘗試索引 1 (通常是外接鏡頭)，不行則回傳 0 (內建鏡頭)
    """
    # 嘗試開啟索引 1
    test_cap = cv2.VideoCapture(1, cv2.CAP_DSHOW)
    if test_cap.isOpened():
        print("檢測到外接 Webcam (Index 1)")
        test_cap.release()
        return 1
    
    print("未檢測到外接鏡頭，切換至內建鏡頭 (Index 0)")
    return 0

def select_roi_area(frame):
    """
    跳出視窗讓使用者用滑鼠框選區域。
    框選完後按『Space』或『Enter』鍵確認，按『c』鍵取消。
    回傳值: (x, y, w, h)
    """
    print("\n--- 進入選取區域模式 ---")
    print("操作說明:")
    print("1. 連按滑鼠左鍵並拖曳，框出你要辨識的範圍")
    print("2. 框選完畢後，按下『Space』或『Enter』確認")
    print("3. 想要重選或取消，按下『c』鍵")
    
    # selectROI 會自動開啟一個視窗讓使用者框選
    # showCrosshair=True 會顯示十字準心，fromCenter=False 代表從角落開始拖曳
    roi = cv2.selectROI("Select ROI (Press Space/Enter to Confirm)", frame, showCrosshair=True, fromCenter=False)
    
    # 關閉 selectROI 建立的臨時視窗
    cv2.destroyWindow("Select ROI (Press Space/Enter to Confirm)")
    
    # 如果使用者直接按 c 取消，回傳的 w 和 h 會是 0
    if roi[2] == 0 or roi[3] == 0:
        print("❌ 取消框選，回復全螢幕辨識。")
        return None
        
    print(f"✅ 成功設定辨識區域: X={roi[0]}, Y={roi[1]}, 寬={roi[2]}, 高={roi[3]}")
    return roi


def run_camera():
    model = EatingModel()

    # 自動選擇鏡頭索引
    cam_idx = get_camera_index()
    cap = cv2.VideoCapture(cam_idx, cv2.CAP_DSHOW)
    time.sleep(1.0)
    server_url = "http://127.0.0.1:8000/api/update_status"

    print("相機程式啟動...")
    print("【新功能說明】 按 'r' 鍵可以手動設定/重設辨識框框")
    print("【操作說明】 按 's' 開始錄影與紀錄 | 按 'e' 結束錄影與紀錄 | 按 'q' 退出程式")
    current_time = time.time()
    last_time = current_time-1

    # --- 錄影與紀錄狀態控制變數 ---
    is_recording = False
    video_writer = None
    json_log_data = []  # 用來暫存所有偵測紀錄的列表
    file_prefix = ""    # 儲存目前的檔案時間戳記字串

    # 儲存 ROI 座標的變數 (x, y, w, h) ---
    roi_box = None
    
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret: break
        frame = cv2.flip(frame, 1)   
        current_time = time.time()

        # 如果有設定框框，就只切下框框內的影像給模型
        if roi_box is not None:
            x, y, w, h = roi_box
            # 利用 Python 矩陣切片（Slicing）裁切影像 [y:y+h, x:x+w]
            inference_frame = frame[y:y+h, x:x+w]
        else:
            inference_frame = frame # 否則用完整畫面                 
        
        # 當偵測到 EatMeal 時，把時間點傳給 Server
        if (current_time-last_time) > 0.5:

            label, conf, diff, diff_s, eat_frequency, label_con = model.predict(inference_frame)
            last_time = current_time

            if "EatMeal" in label and conf > 0.8:
                data = {
                    "label": label,
                    "eat_frequency": eat_frequency,
                    "last_seen_time": time.time(),
                    "label_con": label_con
                }
                try:
                    # 這裡發送請求給 Server
                    requests.post(server_url, json=data, timeout=0.5)
                except Exception as e:
                    print(f"Server 尚未啟動... {e}")
            elif "EatSnack" in label and conf > 0.8:
                data = {
                    "label": label,
                    "eat_frequency": eat_frequency,
                    "last_seen_time": time.time(),
                    "label_con": label_con               
                }
                try:
                    # 這裡發送請求給 Server
                    requests.post(server_url, json=data, timeout=0.5)
                except Exception as e:
                    print(f"Server 尚未啟動... {e}")
        
            if ("EatMeal" in label or "EatSnack" in label) and is_recording:
                    timestamp = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(current_time))
                    # 建立單筆紀錄的字典資料
                    record = {
                        "timestamp": timestamp,
                        "unix_time": current_time,
                        "status": label,
                        "confidence": round(float(conf), 4),
                        "eat_frequency": round(float(eat_frequency), 4),
                        "label_con": label_con
                    }
                    json_log_data.append(record)        


        # --- 將資訊與框框繪製到原始 frame 上（這樣錄影才能錄到框框） ---
        
        # 如果有啟用 ROI，在畫面上畫出黃色虛線/實線框，提醒使用者目前只看這裡
        if roi_box is not None:
            x, y, w, h = roi_box
            cv2.rectangle(frame, (x, y), (x + w, y + h), (0, 255, 255), 1)
            cv2.putText(frame, "ROI DETECTION ACTIVE", (x, y - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1)


        status_text = f"Status: {label} ({conf:.2f})({eat_frequency:.2f})"
        cv2.putText(frame, status_text,(20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.8,(255, 200, 0),2)

        # --- 新增：如果在錄影狀態，畫面上顯示紅點提示，並寫入影片幀 ---
        if is_recording:
            # 在右上角畫一個紅色實心圓代表錄影中
            cv2.circle(frame, (frame.shape[1] - 30, 30), 10, (0, 0, 255), -1)
            cv2.putText(frame, "REC", (frame.shape[1] - 85, 38), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
            
            # 寫入影片檔案
            if video_writer:
                video_writer.write(frame)


        cv2.imshow("Camera Source", frame)
        
        # --- 按鍵事件偵測 ---
        key = cv2.waitKey(1) & 0xFF

        # 💡 新增：按 'r' 鍵觸發手動選取區域
        if key == ord('r'):
            # 如果目前已經有框框，按 r 就直接重設（變回全螢幕辨識）
            if roi_box is not None:
                roi_box = None
                print("🔄 已重設，回復全螢幕辨識模式。")
            else:
                # 呼叫框選函式，傳入當前的這一幀畫面
                roi_box = select_roi_area(frame)
        
        # 1. 按 s 開始紀錄與錄影
        if key == ord('s'):
            if not is_recording:
                # 拿當前時間當作檔名，避免覆蓋舊檔案
                file_prefix = time.strftime('%Y%m%d_%H%M%S', time.localtime())
                
                # 初始化空的紀錄陣列
                json_log_data = []
                
                # 初始化影片檔
                height, width, _ = frame.shape
                fourcc = cv2.VideoWriter_fourcc(*'mp4v') # 使用 MP4 編碼器
                video_writer = cv2.VideoWriter(f"data/video/eating_video_{file_prefix}.mp4", fourcc, 20.0, (width, height))
                
                is_recording = True
                print("▶ 進入錄影與紀錄狀態。")
            else:
                print("⚠ 已經在錄影中！")

        # 2. 按 e 結束紀錄與錄影
        elif key == ord('e'):
            if is_recording:
                is_recording = False
                
                # 安全釋放 VideoWriter
                if video_writer:
                    video_writer.release()
                    video_writer = None
                
                # 將儲存的資料轉存為結構完整的 JSON 檔案
                json_filename = f"data/eating/eating_log_{file_prefix}.json"
                try:
                    with open(json_filename, "w", encoding="utf-8") as f:
                        # indent=4 可以讓輸出的 JSON 檔案自動排版，具備可讀性
                        # ensure_ascii=False 確保如果裡面有中文，不會變成 \u4e2d 這種亂碼
                        json.dump(json_log_data, f, indent=4, ensure_ascii=False)
                    print(f"■ 終止錄影，JSON 紀錄已儲存至：{json_filename}")
                except Exception as ex:
                    print(f"儲存 JSON 發生錯誤: {ex}")
            else:
                print("⚠ 目前並未開始錄影。")

        # 3. 按 q 退出
        elif key == ord('q'): 
            break


        # 離開程式前的安全防護（防止錄到一半按 q 退出導致 JSON 沒存到）
    if is_recording:
        if video_writer: video_writer.release()
        if json_log_data:
            with open(f"data/eating/eating_log_{file_prefix}_autosave.json", "w", encoding="utf-8") as f:
                json.dump(json_log_data, f, indent=4, ensure_ascii=False)

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    run_camera()