import cv2
import time
from backend.eat_classifier import EatingModel

class SmartCamera:
    def __init__(self, camera_id=0):
        # 初始化硬體
        self.cap = cv2.VideoCapture(camera_id)
        # 初始化 AI 模型
        self.model = EatingModel()
        self.is_running = True

    def get_status(self):
        """讀取一影格並進行辨識，回傳完整資訊"""
        ret, frame = self.cap.read()
        if not ret:
            return None, None, None, None
        
        frame = cv2.flip(frame, 1)
        # 呼叫模型進行辨識
        label, conf, eat_diff = self.model.predict(frame)
        
        return frame, label, conf, eat_diff

    def stop(self):
        """關閉相機資源"""
        self.cap.release()
        cv2.destroyAllWindows()
