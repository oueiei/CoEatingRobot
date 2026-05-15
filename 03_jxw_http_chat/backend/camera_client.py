# camera_client.py
import cv2
import requests
import time
from eat_classifier import EatingModel

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

def run_camera():
    model = EatingModel()

    # 自動選擇鏡頭索引
    cam_idx = get_camera_index()
    cap = cv2.VideoCapture(cam_idx, cv2.CAP_DSHOW)
    time.sleep(1.0)
    server_url = "http://127.0.0.1:8000/api/update_status"

    print("相機程式啟動...")
    current_time = time.time()
    last_time = current_time-1
    
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret: break
        frame = cv2.flip(frame, 1)   
        current_time = time.time()                 
        
        # 當偵測到 EatMeal 時，把時間點傳給 Server
        if (current_time-last_time) > 0.5:
            label, conf, diff, diff_s, eat_frequency = model.predict(frame)
            last_time = current_time
            if "EatMeal" in label and conf > 0.7:
                data = {
                    "label": label,
                    "eat_frequency": eat_frequency,
                    "last_seen_time": time.time()
                }
                try:
                    # 這裡發送請求給 Server
                    requests.post(server_url, json=data, timeout=0.5)
                except Exception as e:
                    print(f"Server 尚未啟動... {e}")
            elif "EatSnack" in label and conf > 0.7:
                data = {
                    "label": label,
                    "eat_frequency": eat_frequency,
                    "last_seen_time": time.time()               
                }
                try:
                    # 這裡發送請求給 Server
                    requests.post(server_url, json=data, timeout=0.5)
                except Exception as e:
                    print(f"Server 尚未啟動... {e}")
        
        status_text = f"Status: {label} ({conf:.2f})"
        cv2.putText(frame, status_text, (20, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.8,(255, 200, 0),2)
        cv2.imshow("Camera Source", frame)
        if cv2.waitKey(1) & 0xFF == ord('q'): break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    run_camera()