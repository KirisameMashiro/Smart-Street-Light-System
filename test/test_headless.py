#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
笔记本内置摄像头行人检测 + MQTT发布（无界面版本）
用途：实时检测摄像头画面中的行人数量，并通过MQTT发布到EMQX
"""

import cv2
import time
import json
import paho.mqtt.client as mqtt
from ultralytics import YOLO

# ==================== 配置区域 ====================
# 摄像头配置
CAMERA_ID = 0  # 内置摄像头通常是0

# MQTT配置（根据你的EMQX实际地址修改）
MQTT_BROKER = "47.108.58.107"
MQTT_PORT = 1883
MQTT_TOPIC = "camera/person_count"
MQTT_CLIENT_ID = "laptop_person_detector"

# 检测配置
DETECTION_INTERVAL = 1.0  # 每秒检测一次并发送
CONFIDENCE_THRESHOLD = 0.5  # 置信度阈值

# ==================== 初始化 ====================
print("正在初始化系统...")

# 1. 初始化YOLO模型
print("加载YOLO模型...")
model = YOLO("yolov8n-person.pt")
model.classes = [0]  # 只检测 'person' 类别
print("模型加载完成")

# 2. 初始化摄像头
print("打开内置摄像头...")
cap = cv2.VideoCapture(CAMERA_ID)
if not cap.isOpened():
    print("无法打开摄像头，请检查摄像头是否被占用")
    exit(1)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
print("摄像头打开成功")

# 3. 初始化MQTT客户端
print(f"连接MQTT Broker: {MQTT_BROKER}:{MQTT_PORT}...")
mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, MQTT_CLIENT_ID)
try:
    mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
    print("MQTT连接成功")
except Exception as e:
    print(f"MQTT连接失败: {e}")
    print("将继续运行，但不会发布消息")

# ==================== 主循环 ====================
print("\n开始实时检测，按 Ctrl+C 退出\n")

last_publish_time = 0
frame_count = 0

try:
    while True:
        ret, frame = cap.read()
        if not ret:
            print("无法读取摄像头画面，尝试重新连接...")
            time.sleep(1)
            continue

        current_time = time.time()

        if current_time - last_publish_time >= DETECTION_INTERVAL:
            results = model(frame, conf=CONFIDENCE_THRESHOLD, verbose=False)

            person_count = 0
            for result in results:
                if result.boxes is not None:
                    person_count = len(result.boxes)

            payload = json.dumps({
                "timestamp": int(current_time),
                "count": person_count,
                "camera_id": "laptop_cam"
            })

            try:
                result = mqtt_client.publish(MQTT_TOPIC, payload)
                if result.rc == mqtt.MQTT_ERR_SUCCESS:
                    print(f"[检测] 人数={person_count}, 时间={int(current_time)}")
                else:
                    print(f"发布失败, 错误码: {result.rc}")
            except Exception as e:
                print(f"发布异常: {e}")

            last_publish_time = current_time
            frame_count += 1

except KeyboardInterrupt:
    print("\n\n检测停止")

# ==================== 清理资源 ====================
print("正在清理资源...")
cap.release()
mqtt_client.disconnect()
print("程序已退出")
