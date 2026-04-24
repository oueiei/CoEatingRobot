import cv2
import numpy as np
import tensorflow as tf
import time


class EatingModel:
    eat_time_diff = None

    def __init__(self, model_path="model_unquant.tflite", label_path="labels.txt"):
            self.interpreter = tf.lite.Interpreter(model_path=model_path)
            self.interpreter.allocate_tensors()
            self.input_details = self.interpreter.get_input_details()
            self.output_details = self.interpreter.get_output_details()
            
            with open(label_path, "r", encoding="utf-8") as f:
                self.labels = [line.strip() for line in f.readlines()]

            # 新增：紀錄每個標籤最後出現的時間戳記 { "標籤名": 時間 }
            self.last_seen_times = {label: None for label in self.labels}

    def predict(self, frame):
        img = cv2.resize(frame, (224, 224))
        img = np.expand_dims(img, axis=0).astype(np.float32)
        img = (img / 127.5) - 1
            
        self.interpreter.set_tensor(self.input_details[0]['index'], img)
        self.interpreter.invoke()
        prediction = self.interpreter.get_tensor(self.output_details[0]['index'])
            
        index = np.argmax(prediction[0])
        label = self.labels[index]
        confidence = prediction[0][index]

        # --- 時間差計算邏輯 ---
        current_time = time.time()
            
        # 如果信心值夠高（例如 > 70%），我們才認定「真的看到了」
        if confidence > 0.7:
            # 更新最後看到的時間
            self.last_seen_times[label] = current_time

        self.eat_time_diff = self.get_last_seen_time_by_name("EatMeal")
            
        return label, confidence, self.eat_time_diff
    
    def get_last_seen_time_by_name(self, name):
        for label, last_time in self.last_seen_times.items():
            if name in label:  # 使用 in 來忽略前面的編號
                if last_time is not None:
                    current_time = time.time()
                    return current_time - last_time
        return None