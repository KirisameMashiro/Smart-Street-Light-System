#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
路灯传感器模拟器（纯 MQTT 模式）

功能：
1. 启动时通过 HTTP API 从后端获取路灯列表
2. 每 3-5 秒为每个路灯生成模拟传感器数据并发布 MQTT 消息
3. 订阅 smartlight/control/+ 接收后端控制命令，更新内存中的状态
4. 不再直连数据库，避免与后端产生锁冲突
"""

import json
import random
import time
import threading
import configparser
import os
import urllib.request
import urllib.error
import paho.mqtt.client as mqtt
from datetime import datetime

# ==================== 读取配置文件 ====================
CONFIG_FILE = 'config.ini'

def load_config():
    if not os.path.exists(CONFIG_FILE):
        print(f"[Error] Configuration file '{CONFIG_FILE}' not found.")
        print("Please copy config.example.ini to config.ini and fill in your credentials.")
        exit(1)
    config = configparser.ConfigParser()
    config.read(CONFIG_FILE, encoding='utf-8')
    return config

config = load_config()

# MQTT 配置
MQTT_BROKER = config.get('mqtt', 'broker')
MQTT_PORT = config.getint('mqtt', 'port')
MQTT_KEEPALIVE = config.getint('mqtt', 'keepalive')
MQTT_CLIENT_ID = config.get('mqtt', 'client_id')
MQTT_USERNAME = config.get('mqtt', 'username', fallback=None)
MQTT_PASSWORD = config.get('mqtt', 'password', fallback=None)

# 模拟器配置
INTERVAL_MIN = config.getfloat('simulator', 'interval_min')
INTERVAL_MAX = config.getfloat('simulator', 'interval_max')

# 后端 API 地址（用于启动时获取路灯列表）
BACKEND_API_URL = config.get('backend', 'api_url', fallback='http://localhost:8080')

# ==================== 全局状态（纯内存） ====================
lights = []             # [{id, light_code, status, brightness}, ...]
light_lock = threading.Lock()
CONTROL_TOPIC = 'smartlight/control/+'

# ==================== 从后端 API 获取路灯列表 ====================
def fetch_lights_from_api():
    """启动时调用后端 REST API 获取所有路灯的基本信息"""
    url = f"{BACKEND_API_URL}/api/lights"
    print(f"[API] Fetching lights from {url} ...")
    try:
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode('utf-8')
            result = json.loads(body)
            # 后端返回 {code, data, message} 格式
            raw_list = result.get('data', [])
            if not raw_list:
                print("[Warn] API returned empty light list")
                return []
            parsed = []
            for item in raw_list:
                parsed.append({
                    'id': item.get('id'),
                    'light_code': item.get('lightCode', ''),
                    'status': item.get('status', 0),
                    'brightness': item.get('brightness', 0)
                })
            print(f"[API] Fetched {len(parsed)} lights")
            return parsed
    except urllib.error.URLError as e:
        print(f"[API Error] Cannot fetch lights: {e}")
        print("[Warn] Will retry after MQTT connects...")
        return []
    except Exception as e:
        print(f"[API Error] {e}")
        return []

# ==================== 传感器数据生成 ====================
def generate_sensor_data(light):
    status = light['status']
    brightness = light['brightness']
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    if status == 0:   # 关灯
        illuminance = random.randint(0, 50)
        power = round(random.uniform(0.5, 5.0), 2)
        current = round(random.uniform(0.01, 0.05), 3)
        voltage = round(random.uniform(215, 225), 1)
    else:             # 开灯
        ratio = brightness / 100.0
        illuminance = int(200 + ratio * 1600)
        power = round(50 + ratio * 100, 2)
        current = round(0.3 + ratio * 0.5, 3)
        voltage = round(random.uniform(215, 225), 1)

    temperature = round(random.uniform(25, 40), 1)
    humidity = round(random.uniform(50, 80), 1)

    return {
        "lightId": light['id'],
        "illuminance": illuminance,
        "power": power,
        "voltage": voltage,
        "current": current,
        "temperature": temperature,
        "humidity": humidity,
        "collectTime": now
    }

# ==================== MQTT 回调 ====================
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("[MQTT] Connected.")
        client.subscribe(CONTROL_TOPIC)
        print(f"[MQTT] Subscribed to {CONTROL_TOPIC}")
        # 连接后再次尝试获取路灯列表（如果之前失败的话）
        global lights
        with light_lock:
            if not lights:
                lights = fetch_lights_from_api()
    else:
        print(f"[MQTT] Connection failed, code {rc}")

def on_message(client, userdata, msg):
    """
    收到后端控制命令 → 只更新内存中的状态，不再写数据库。
    后端已经自己写了数据库，模拟器无需重复写入。
    """
    topic = msg.topic
    payload = msg.payload.decode('utf-8')
    print(f"[MQTT] Received on {topic}: {payload}")

    parts = topic.split('/')
    if len(parts) < 3:
        print("[Warn] Invalid topic format")
        return
    light_code = parts[2]

    try:
        cmd = json.loads(payload)
        command_type = cmd.get('command')
        if not command_type:
            print("[Warn] No 'command' field")
            return

        with light_lock:
            light = next((l for l in lights if l['light_code'] == light_code), None)
            if light is None:
                print(f"[Warn] Light {light_code} not found in memory")
                return

            if command_type == 'switch':
                status = cmd.get('status')
                if status is None:
                    return
                light['status'] = status
                if status == 0:
                    light['brightness'] = 0
                elif light.get('brightness', 0) == 0:
                    light['brightness'] = 100
                print(f"[Mem] {light_code} status → {status}")

            elif command_type == 'brightness':
                brightness = cmd.get('brightness')
                if brightness is None:
                    return
                light['brightness'] = brightness
                if brightness > 0:
                    light['status'] = 1
                else:
                    light['status'] = 0
                print(f"[Mem] {light_code} brightness → {brightness}")

            elif command_type == 'set':
                status = cmd.get('status')
                brightness = cmd.get('brightness')
                if status is None or brightness is None:
                    return
                light['status'] = status
                light['brightness'] = brightness
                print(f"[Mem] {light_code} status={status}, brightness={brightness}")

            else:
                print(f"[Warn] Unknown command: {command_type}")

    except json.JSONDecodeError:
        print("[Warn] Invalid JSON")
    except Exception as e:
        print(f"[Error] {e}")

# ==================== 定时发布传感器数据 ====================
def publish_sensor_data(client):
    while True:
        interval = random.uniform(INTERVAL_MIN, INTERVAL_MAX)
        time.sleep(interval)

        with light_lock:
            snapshot = lights.copy()

        if not snapshot:
            print("[Warn] No lights in memory, skipping publish")
            continue

        for light in snapshot:
            data = generate_sensor_data(light)
            topic = f"smartlight/sensor/{light['id']}"
            payload = json.dumps(data)
            result = client.publish(topic, payload)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                print(f"[Pub] {topic} -> {payload}")
            else:
                print(f"[Pub Error] {topic}")

# ==================== 主函数 ====================
def main():
    # 启动时从后端 API 获取路灯列表（代替之前的直连数据库）
    global lights
    lights = fetch_lights_from_api()

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, MQTT_CLIENT_ID)
    client.on_connect = on_connect
    client.on_message = on_message

    if MQTT_USERNAME and MQTT_PASSWORD:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    try:
        client.connect(MQTT_BROKER, MQTT_PORT, MQTT_KEEPALIVE)
    except Exception as e:
        print(f"[Error] Cannot connect to broker: {e}")
        return

    pub_thread = threading.Thread(target=publish_sensor_data, args=(client,), daemon=True)
    pub_thread.start()

    try:
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n[Info] Stopping...")
        client.disconnect()

if __name__ == '__main__':
    main()