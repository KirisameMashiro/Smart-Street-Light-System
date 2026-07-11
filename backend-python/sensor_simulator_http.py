#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# ============================================================
# 注意：此文件已被禁用，请勿直接运行！
#
# 原因：HTTP直写数据库方式仅供测试使用，生产环境应使用 MQTT 方式上报数据。
# 如需启用，请取消文件底部 if __name__ == '__main__': main() 的注释。
# ============================================================

"""
路灯传感器模拟器（HTTP API + 数据库直写模式）
- 启动时先生成7天历史数据（时间跨度大、光照差距大）直接写入数据库
- 然后持续通过 HTTP API 上报实时数据
"""

import json
import random
import time
import threading
import configparser
import os
import urllib.request
import urllib.error
import pymysql
from datetime import datetime, timedelta

CONFIG_FILE = 'config.ini'

def load_config():
    if not os.path.exists(CONFIG_FILE):
        print(f"[Error] Configuration file '{CONFIG_FILE}' not found.")
        exit(1)
    config = configparser.ConfigParser()
    config.read(CONFIG_FILE, encoding='utf-8')
    return config

config = load_config()

INTERVAL_MIN = config.getfloat('simulator', 'interval_min')
INTERVAL_MAX = config.getfloat('simulator', 'interval_max')
BACKEND_API_URL = config.get('backend', 'api_url', fallback='http://localhost:8080')

DB_HOST = config.get('database', 'host', fallback='localhost')
DB_PORT = config.getint('database', 'port', fallback=3306)
DB_USER = config.get('database', 'user', fallback='root')
DB_PASS = config.get('database', 'password', fallback='123456')
DB_NAME = config.get('database', 'database', fallback='smart_light')

lights = []
light_lock = threading.Lock()


def get_db_conn():
    return pymysql.connect(
        host=DB_HOST, port=DB_PORT, user=DB_USER,
        password=DB_PASS, database=DB_NAME, charset='utf8mb4'
    )


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
    except Exception as e:
        print(f"[API Error] {e}")
        return []


def generate_history_data(light_list):
    """
    为每个路灯生成7天历史数据，每小时一条：
    - 光照值模拟昼夜变化：白天高(500~2000 lux)，夜晚低(0~50 lux)
    - 加入随机波动使数据差距更大
    - 时间跨度：过去7天到当前小时
    """
    now = datetime.now().replace(minute=0, second=0, microsecond=0)
    start = now - timedelta(days=7)

    conn = get_db_conn()
    cursor = conn.cursor()

    # 先清空旧数据
    cursor.execute("TRUNCATE TABLE sensor_data_hourly")
    conn.commit()
    print("[DB] Table sensor_data_hourly truncated")

    total = 0
    for light in light_list:
        current = start
        rows = []
        while current <= now:
            hour = current.hour

            # 模拟昼夜光照变化
            if 6 <= hour <= 18:
                # 白天：6点开始上升，12点最高，18点下降
                if hour <= 12:
                    base_lux = int(300 + (hour - 6) * 200 + random.uniform(-100, 100))
                else:
                    base_lux = int(300 + (18 - hour) * 200 + random.uniform(-100, 100))
                base_lux = max(200, min(2000, base_lux))
            elif 19 <= hour <= 20:
                # 黄昏过渡
                base_lux = random.randint(50, 300)
            else:
                # 夜晚：接近0
                base_lux = random.randint(0, 50)

            # 30% 概率出现极端值
            rand = random.random()
            if rand < 0.1:
                base_lux = random.randint(0, 10)
            elif rand < 0.2:
                base_lux = random.randint(1800, 2000)

            ratio = base_lux / 2000.0
            avg_power = round(5 + ratio * 150, 2)
            max_power = round(avg_power + random.uniform(5, 20), 2)
            min_power = round(max(0.5, avg_power - random.uniform(5, 20)), 2)
            avg_voltage = round(random.uniform(215, 225), 2)
            avg_current = round(0.02 + ratio * 0.8, 3)
            avg_temp = round(random.uniform(20, 45), 2)
            avg_humidity = round(random.uniform(40, 90), 2)
            total_energy = round(avg_power * random.uniform(0.8, 1.0), 3)

            rows.append((
                light['id'],
                current.strftime('%Y-%m-%d %H:%M:%S'),
                base_lux, avg_power, avg_voltage, avg_current,
                avg_temp, avg_humidity, total_energy, 1,
                max_power, min_power
            ))
            current += timedelta(hours=1)

        # 批量插入
        sql = """INSERT INTO sensor_data_hourly
            (light_id, hour_start, avg_illuminance, avg_power, avg_voltage,
             avg_current, avg_temperature, avg_humidity, total_energy,
             data_count, max_power, min_power)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"""
        cursor.executemany(sql, rows)
        total += len(rows)
        print(f"[DB] Light {light['id']}: inserted {len(rows)} records")

    conn.commit()
    cursor.close()
    conn.close()
    print(f"[DB] Total inserted: {total} records")


def generate_sensor_data(light, point_index=0):
    """生成实时传感器数据（光照差距大）"""
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    base_lux = random.randint(0, 2000)
    rand = random.random()
    if rand < 0.3:
        base_lux = random.randint(0, 50)
    elif rand < 0.6:
        base_lux = random.randint(1500, 2000)

    phase = point_index * 50
    lux_variation = random.randint(-300, 300) + phase
    illuminance = max(0, min(2000, base_lux + lux_variation))

    ratio = illuminance / 2000.0
    power = round(5 + ratio * 150, 2)
    current = round(0.02 + ratio * 0.8, 3)
    voltage = round(random.uniform(215, 225), 1)
    temperature = round(random.uniform(20, 45), 1)
    humidity = round(random.uniform(40, 90), 1)

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


def publish_sensor_data():
    while True:
        interval = random.uniform(INTERVAL_MIN, INTERVAL_MAX)
        time.sleep(interval)

        with light_lock:
            snapshot = lights.copy()

        if not snapshot:
            continue

        batch_data = []
        for index, light in enumerate(snapshot):
            batch_data.append(generate_sensor_data(light, point_index=index))

        url = f"{BACKEND_API_URL}/api/sensor-data/ingest/batch"
        payload = json.dumps(batch_data).encode('utf-8')
        req = urllib.request.Request(url, data=payload, method='POST')
        req.add_header('Content-Type', 'application/json')

        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                body = resp.read().decode('utf-8')
                result = json.loads(body)
                if result.get('code') == 200:
                    print(f"[HTTP] Batch sent: {len(batch_data)} lights")
                else:
                    print(f"[HTTP Error] {result.get('message')}")
        except Exception as e:
            print(f"[HTTP Error] {e}")


def main():
    global lights
    lights = fetch_lights_from_api()
    if not lights:
        print("[Error] No lights, exiting")
        return

    # 1. 生成7天历史数据直接写入数据库
    print("[Info] Generating 7-day history data...")
    generate_history_data(lights)
    print("[Info] History data generation complete")

    # 2. 启动实时数据上报
    print("[Info] Starting real-time publisher...")
    pub_thread = threading.Thread(target=publish_sensor_data, daemon=True)
    pub_thread.start()

    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        print("\n[Info] Stopping...")


# if __name__ == '__main__':
#     main()
