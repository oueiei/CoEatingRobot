# camera_client.py
import cv2
import requests
import time
from backend.eat_classifier import EatingModel

def run_camera():
    model = EatingModel()
    cap = cv2.VideoCapture(0)
    server_url = "http://127.0.0.1:8000/api/update_status"

    print("相機程式啟動...")
    
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret: break
        frame = cv2.flip(frame, 1)
        
        label, conf, diff = model.predict(frame)
        
        # 當偵測到 EatMeal 時，把時間點傳給 Server
        if "EatMeal" in label and conf > 0.7:
            data = {
                "label": label,
                "last_seen_time": time.time()
            }
            try:
                # 這裡發送請求給 Server
                requests.post(server_url, json=data, timeout=0.5)
            except Exception as e:
                print(f"Server 尚未啟動... {e}")

        cv2.imshow("Camera Source", frame)
        if cv2.waitKey(1) & 0xFF == ord('q'): break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    run_camera()