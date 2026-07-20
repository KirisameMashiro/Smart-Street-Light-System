#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
路灯传感器模拟器（纯 MQTT 模式）
"""

import json
import random
import time
import threading
import configparser
import os
import hashlib
import urllib.request
import urllib.error
import socket
import paho.mqtt.client as mqtt
from datetime import datetime

CONFIG_FILE = 'config.ini'

def load_config():
    if not os.path.exists(CONFIG_FILE):
        print(f"[Error] Configuration file '{CONFIG_FILE}' not found.")
        exit(1)
    config = configparser.ConfigParser()
    config.read(CONFIG_FILE, encoding='utf-8')
    return config

config = load_config()

MQTT_BROKER = config.get('mqtt', 'broker')
MQTT_PORT = config.getint('mqtt', 'port')
MQTT_KEEPALIVE = config.getint('mqtt', 'keepalive')
MQTT_CLIENT_ID = config.get('mqtt', 'client_id')
MQTT_USERNAME = config.get('mqtt', 'username', fallback=None)
MQTT_PASSWORD = config.get('mqtt', 'password', fallback=None)

INTERVAL_MIN = config.getfloat('simulator', 'interval_min')
INTERVAL_MAX = config.getfloat('simulator', 'interval_max')

BACKEND_API_URL = config.get('backend', 'api_url', fallback='http://localhost:8080')

lights = []
light_lock = threading.Lock()
CONTROL_TOPIC = 'smartlight/control/+'
connected_event = threading.Event()

def fetch_lights_from_api():
    url = f"{BACKEND_API_URL}/api/lights"
    print(f"[API] Fetching lights from {url} ...")
    try:
        req = urllib.request.Request(url, method='GET')
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode('utf-8')
            result = json.loads(body)
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
        return []
    except Exception as e:
        print(f"[API Error] {e}")
        return []

def generate_sensor_data(light):
    status = light['status']
    brightness = light['brightness']
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    # 基于路灯 ID 的稳定偏移系数，让每盏灯功耗天然不同（±7%）
    seed = int(hashlib.md5(str(light['id']).encode()).hexdigest()[:8], 16)
    factor = 0.93 + (seed % 140) / 1000.0  # 0.93 ~ 1.07

    if status == 0:
        illuminance = random.randint(5, 50)
        power = round(random.uniform(0.3, 2.0) * factor, 2)
        current = round(random.uniform(0.001, 0.01) * factor, 3)
        voltage = round(random.uniform(215, 225), 1)
    else:
        # 开灯状态：照度随亮度变化，范围 300~450
        ratio = brightness / 100.0
        # 照度在 300~450 之间随亮度线性变化，并加入少量随机波动
        base_illuminance = 300 + ratio * 150
        illuminance = int(base_illuminance + random.randint(-20, 20))
        # 确保在范围内
        illuminance = max(300, min(450, illuminance))
        
        base_power = (50 + ratio * 100) * factor
        power = round(base_power + random.uniform(-3, 3), 2)
        base_current = (0.3 + ratio * 0.5) * factor
        current = round(base_current + random.uniform(-0.01, 0.01), 3)
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

def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        print(f"[MQTT] Connected to {MQTT_BROKER}:{MQTT_PORT}")
        client.subscribe(CONTROL_TOPIC, qos=1)
        print(f"[MQTT] Subscribed to {CONTROL_TOPIC}")
        connected_event.set()
        global lights
        with light_lock:
            if not lights:
                lights = fetch_lights_from_api()
    else:
        print(f"[MQTT] Connection failed, reason code: {reason_code}")
        connected_event.clear()

def on_disconnect(client, userdata, flags, reason_code, properties):
    print(f"[MQTT] Disconnected, reason code: {reason_code}")
    connected_event.clear()

def on_message(client, userdata, msg):
    topic = msg.topic
    payload = msg.payload.decode('utf-8')

    print(f"[MQTT RECV] Topic: {topic}, Payload: {payload}")

    parts = topic.split('/')
    if len(parts) < 3:
        return
    light_code = parts[2]

    try:
        cmd = json.loads(payload)
        command_type = cmd.get('command')
        if not command_type:
            return

        with light_lock:
            light = next((l for l in lights if l['light_code'] == light_code), None)
            if light is None:
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

            elif command_type == 'brightness':
                brightness = cmd.get('brightness')
                if brightness is None:
                    return
                light['brightness'] = brightness
                if brightness > 0:
                    light['status'] = 1
                else:
                    light['status'] = 0

            elif command_type == 'set':
                status = cmd.get('status')
                brightness = cmd.get('brightness')
                if status is None or brightness is None:
                    return
                light['status'] = status
                light['brightness'] = brightness

    except json.JSONDecodeError:
        pass
    except Exception as e:
        print(f"[Error] {e}")

def publish_sensor_data(client):
    while True:
        interval = random.uniform(INTERVAL_MIN, INTERVAL_MAX)
        time.sleep(interval)

        if not connected_event.is_set():
            print("[Warn] Waiting for MQTT connection...")
            connected_event.wait(timeout=5)
            continue

        with light_lock:
            snapshot = lights.copy()

        if not snapshot:
            continue

        for light in snapshot:
            if light['id'] == 30:  # 跳过 ID 为 30 的路灯
                print(f"[Skip] Light ID 30 skipped")
                continue
            
            data = generate_sensor_data(light)
            topic = f"smartlight/sensor/{light['id']}"
            payload = json.dumps(data)
            result = client.publish(topic, payload, qos=1)
            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                print(f"[Pub] {topic}")
            else:
                print(f"[Pub Error] {topic}, rc={result.rc}")
            time.sleep(0.02)

def main():
    global lights
    lights = fetch_lights_from_api()

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, MQTT_CLIENT_ID)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message

    client.enable_logger()

    client.reconnect_delay_set(min_delay=2, max_delay=60)

    if MQTT_USERNAME and MQTT_PASSWORD:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    client.connect(MQTT_BROKER, MQTT_PORT, MQTT_KEEPALIVE)

    pub_thread = threading.Thread(target=publish_sensor_data, args=(client,), daemon=True)
    pub_thread.start()

    try:
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n[Info] Stopping...")
        client.disconnect()

if __name__ == '__main__':
    main()
