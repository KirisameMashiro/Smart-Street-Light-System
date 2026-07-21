#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
笔记本内置摄像头行人检测 + MQTT发布人流数据
流程：
1. 调用后端 API 查询第一个 hasCamera=true 的路灯 ID
2. 实时检测摄像头画面中的行人数量
3. 通过 MQTT 发布人流量数据到 smartlight/flow/{lightId}
4. 后端 MqttConfig 接收后写入 pedestrian_flow 表 + Redis 缓存
"""

import cv2
import time
import json
import requests
import paho.mqtt.client as mqtt
from ultralytics import YOLO

# ==================== 配置区域 ====================
CAMERA_ID = 0  # 内置摄像头通常是0
BACKEND_API = "http://localhost:8080/api"  # 后端 API 地址

# MQTT配置
MQTT_BROKER = "47.108.58.107"
MQTT_PORT = 1883
MQTT_CLIENT_ID = "laptop_person_detector"

# 检测配置
DETECTION_INTERVAL = 1.0  # 每秒检测一次并发送
CONFIDENCE_THRESHOLD = 0.5

# ==================== 获取第一个有监控的路灯ID ====================
print("🔍 查询第一个有监控的路灯...")
light_id = None
try:
    resp = requests.get(f"{BACKEND_API}/lights", timeout=5)
    lights = resp.json().get("data", [])
    for light in lights:
        if light.get("hasCamera"):
            light_id = light["id"]
            print(f"✅ 找到有监控的路灯: ID={light_id}, 名称={light.get('lightName', '')}")
            break
    if light_id is None:
        print("⚠️  未找到有监控的路灯，使用默认 ID=1")
        light_id = 1
except Exception as e:
    print(f"⚠️  查询路灯列表失败: {e}")
    light_id = 1

MQTT_TOPIC = f"smartlight/flow/{light_id}"

# ==================== 初始化 ====================
print("🚀 正在初始化系统...")

# 1. 初始化YOLO模型
print("📦 加载YOLO模型...")
model = YOLO("yolov8n-person.pt")
model.classes = [0]  # 只检测 'person'
print("✅ 模型加载完成")

# 2. 初始化摄像头
print("📷 打开内置摄像头...")
cap = cv2.VideoCapture(CAMERA_ID)
if not cap.isOpened():
    print("❌ 无法打开摄像头，请检查摄像头是否被占用")
    exit(1)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
print("✅ 摄像头打开成功")

# 3. 初始化MQTT客户端
print(f"🔗 连接MQTT Broker: {MQTT_BROKER}:{MQTT_PORT}...")
mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, MQTT_CLIENT_ID)
try:
    mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
    print("✅ MQTT连接成功")
except Exception as e:
    print(f"⚠️  MQTT连接失败: {e}")
    print("将继续运行，但不会发布消息")

mqtt_client.loop_start()

# ==================== 主循环 ====================
print(f"\n🎯 开始实时检测，发布到 MQTT 主题: {MQTT_TOPIC}")
print("  按 'q' 键退出\n")

last_publish_time = 0

while True:
    ret, frame = cap.read()
    if not ret:
        print("⚠️  无法读取摄像头画面，尝试重新连接...")
        time.sleep(1)
        continue

    current_time = time.time()

    if current_time - last_publish_time >= DETECTION_INTERVAL:
        # 执行YOLO推理
        results = model(frame, conf=CONFIDENCE_THRESHOLD, verbose=False)

        # 统计行人数量
        person_count = 0
        for result in results:
            if result.boxes is not None:
                person_count = len(result.boxes)

        # 在画面上绘制检测框
        annotated_frame = results[0].plot() if results else frame
        cv2.putText(
            annotated_frame,
            f"People: {person_count}",
            (10, 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            1,
            (0, 255, 0),
            2
        )

        # 准备MQTT消息并发布
        now_str = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(current_time))
        payload = json.dumps({
            "lightId": light_id,
            "flowCount": person_count,
            "collectTime": now_str
        })

        try:
            result = mqtt_client.publish(MQTT_TOPIC, payload, qos=1)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                print(f"📤 发布: lightId={light_id}, 人数={person_count}, 时间={now_str}")
            else:
                print(f"⚠️  发布失败, 错误码: {result.rc}")
        except Exception as e:
            print(f"⚠️  发布异常: {e}")

        last_publish_time = current_time
    else:
        annotated_frame = frame
        cv2.putText(
            annotated_frame,
            "Detecting...",
            (10, 30),
            cv2.FONT_HERSHEY_SIMPLEX,
            1,
            (0, 255, 0),
            2
        )

    cv2.imshow("YOLO Person Detection (Press 'q' to quit)", annotated_frame)

    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# ==================== 清理资源 ====================
print("\n🔄 正在清理资源...")
cap.release()
cv2.destroyAllWindows()
mqtt_client.loop_stop()
mqtt_client.disconnect()
print("👋 程序已退出")