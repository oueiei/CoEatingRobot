import cv2
import time
from backend.eat_classifier import EatingModel

def run_video_processing():
    model = EatingModel()
    cap = cv2.VideoCapture(0)

    # 初始化上一次列印的時間
    last_print_time = 0 
    # 設定間隔秒數
    print_interval = 0.5

    while cap.isOpened():
        ret, frame = cap.read()
        if not ret: break
        frame = cv2.flip(frame, 1)

        # 取得標籤、信心度、以及距離上次看到的時間差
        label, conf, eat_diff = model.predict(frame)

        # 取得目前時間
        current_time = time.time()
        if current_time - last_print_time >= print_interval:
            if conf > 0.7:
                last_print_time = current_time
                print(f"偵測到: {label}")
                if eat_diff is not None:
                    print(f"偵測到: {label}, 距離上次吃東西過了 {eat_diff:.2f} 秒")


            # 範例應用：如果吃東西標籤超過 10 秒
            if eat_diff is not None and eat_diff > 10.0:
                print("已十秒沒吃東西！")

        # 畫面顯示
        cv2.putText(frame, f"{label} (eat_diff: {str(round(eat_diff, 2)) if eat_diff else 'N/A'}s)", 
                    (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)
        
        cv2.imshow("Time Tracker", frame)
        if cv2.waitKey(1) & 0xFF == ord('q'): break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    run_video_processing()