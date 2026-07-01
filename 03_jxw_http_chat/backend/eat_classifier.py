import cv2
import numpy as np
import tensorflow as tf
import time
import collections


class EatingModel:
    eat_time_diff = None
    snack_eat_time_diff = None
    denominator_time = 30.0
    start_time = 0
    last_lable = "NoLable"
    last_conf = 0.5
    label_con = False


    def __init__(self, model_path="model_unquant_f_0701.tflite", label_path="labels.txt"):
            self.interpreter = tf.lite.Interpreter(model_path=model_path)
            self.interpreter.allocate_tensors()
            self.input_details = self.interpreter.get_input_details()
            self.output_details = self.interpreter.get_output_details()
            
            with open(label_path, "r", encoding="utf-8") as f:
                self.labels = [line.strip() for line in f.readlines()]

            # 新增：紀錄每個標籤最後出現的時間戳記 { "標籤名": 時間 }
            self.last_seen_times = {label: None for label in self.labels}

            # --- 新增：專門存儲 "EatMeal" 發生時間點的隊列 ---
            self.eat_history = collections.deque()
            self.start_time = time.time()

    def predict(self, frame):
        label_con = False
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

        if(label == self.last_lable) and (self.last_conf > 0.8):
            label_con = True
            
        # 如果信心值夠高，我們才認定「真的看到了」
        if confidence > 0.8:
            # 更新最後看到的時間
            self.last_seen_times[label] = current_time
            # 如果偵測到正在吃東西，紀錄這個時間點
            if ("EatMeal" in label) or ("EatSnack" in label):
                if (not self.eat_history) or (not label_con):
                    self.eat_history.append(current_time)

        # --- 移除之前的舊紀錄 ---
        while self.eat_history and (current_time - self.eat_history[0] > self.denominator_time):
            self.eat_history.popleft()
        
        # 計算頻率 (吃了幾次)
        if (current_time-self.start_time) >= self.denominator_time:
            eat_frequency = len(self.eat_history)/self.denominator_time
        else:
            eat_frequency = 0

        self.eat_time_diff = self.get_last_seen_time_by_name("EatMeal")
        self.snack_eat_time_diff = self.get_last_seen_time_by_name("EatSnack")

        self.last_lable = label
        self.last_conf = confidence
            
        return label, confidence, self.eat_time_diff, self.snack_eat_time_diff, eat_frequency, label_con
    
    def get_last_seen_time_by_name(self, name):
        for label, last_time in self.last_seen_times.items():
            if name in label:  # 使用 in 來忽略前面的編號
                if last_time is not None:
                    current_time = time.time()
                    return current_time - last_time
        return None