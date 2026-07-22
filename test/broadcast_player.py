#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
广播播放模拟器
================
模拟灯杆设备接收 MQTT 广播命令，下载语音文件并通过电脑扬声器播放。

流程：
1. 连接 MQTT Broker，订阅 smartlight/broadcast/+ 主题
2. 收到广播命令后，解析 JSON 消息体
3. 通过 HTTP 下载 voiceFileUrl 指向的 WAV 语音文件
4. 调用系统默认播放器播放音频
5. 支持多路灯：自动匹配消息中的 lightCode 到路灯 ID

使用方式：
    pip install paho-mqtt requests
    python broadcast_player.py

配置说明：
    修改下方 CONFIG 区域中的 MQTT_BROKER、BACKEND_API 等参数
"""

import json
import os
import platform
import subprocess
import tempfile
import time
import threading
from datetime import datetime

import requests
import paho.mqtt.client as mqtt

# 检测操作系统
_SYSTEM = platform.system()

# ==================== 配置区域 ====================

# MQTT 配置
MQTT_BROKER = "47.108.58.107"
MQTT_PORT = 1883
MQTT_CLIENT_ID = "laptop_broadcast_player"
MQTT_TOPIC = "smartlight/broadcast/+"

# 后端 API 地址（用于下载语音文件）
BACKEND_API = "http://192.168.20.123:8080/api"

# 播放控制
PLAY_COOLDOWN_SECONDS = 5  # 同一路灯两次播放之间的冷却时间（秒）
AUTO_DELETE_TEMP = True     # 播放后是否自动删除临时文件

# ==================== 内部状态 ====================

# 记录每个路灯上次播放时间，避免重复触发
_last_play_time = {}
_lock = threading.Lock()


def timestamp() -> str:
    """返回当前时间字符串，用于日志"""
    return datetime.now().strftime("%H:%M:%S")


def download_voice(url: str) -> str | None:
    """
    从后端下载语音文件到临时目录
    返回临时文件路径，失败返回 None
    """
    try:
        print(f"  📥 下载语音文件: {url}")
        resp = requests.get(url, timeout=30)
        if resp.status_code != 200:
            print(f"  ❌ 下载失败: HTTP {resp.status_code}")
            return None

        # 写入临时文件
        suffix = ".wav"
        fd, tmp_path = tempfile.mkstemp(suffix=suffix, prefix="broadcast_voice_")
        with os.fdopen(fd, "wb") as f:
            f.write(resp.content)

        file_size_kb = len(resp.content) / 1024
        print(f"  ✅ 下载完成: {file_size_kb:.1f} KB → {tmp_path}")
        return tmp_path

    except requests.exceptions.Timeout:
        print(f"  ❌ 下载超时")
        return None
    except requests.exceptions.ConnectionError:
        print(f"  ❌ 连接失败，后端服务是否已启动？({url})")
        return None
    except Exception as e:
        print(f"  ❌ 下载异常: {e}")
        return None


def play_voice_file(file_path: str) -> bool:
    """
    播放 WAV 语音文件（纯内置，无需额外依赖）
    Windows → winsound (内置)
    macOS   → afplay (内置)
    Linux   → aplay / paplay (内置)
    """
    if not os.path.exists(file_path):
        print(f"  ❌ 文件不存在: {file_path}")
        return False

    try:
        if _SYSTEM == "Windows":
            import winsound
            winsound.PlaySound(file_path, winsound.SND_FILENAME)
            return True
        elif _SYSTEM == "Darwin":
            subprocess.run(["afplay", file_path], check=True)
            return True
        else:
            # Linux: 优先 PulseAudio，回退 ALSA
            for cmd in (["paplay", file_path], ["aplay", file_path]):
                try:
                    subprocess.run(cmd, check=True, timeout=60)
                    return True
                except (FileNotFoundError, subprocess.TimeoutExpired):
                    continue
            raise RuntimeError("未找到可用的音频播放器 (paplay/aplay)")

    except Exception as e:
        print(f"  ❌ 播放失败: {e}")
        return False


# ==================== MQTT 回调 ====================

def on_connect(client, userdata, flags, rc, properties=None):
    """MQTT 连接成功回调"""
    if rc == 0:
        print(f"✅ MQTT 已连接: {MQTT_BROKER}:{MQTT_PORT}")
        client.subscribe(MQTT_TOPIC)
        print(f"📻 已订阅主题: {MQTT_TOPIC}")
        print(f"⏳ 等待广播命令...\n")
    else:
        print(f"❌ MQTT 连接失败: rc={rc}")


def on_disconnect(client, userdata, flags, rc, properties=None):
    """MQTT 断开连接回调"""
    if rc != 0:
        print(f"⚠️  MQTT 连接断开 (rc={rc})，将自动重连...")


def on_message(client, userdata, msg):
    """接收到广播命令"""
    global _last_play_time

    try:
        # 解析 JSON 消息体
        payload_str = msg.payload.decode("utf-8")
        data = json.loads(payload_str)

        command = data.get("command", "")
        if command != "broadcast":
            return  # 不是广播命令，忽略

        light_code = data.get("lightCode", "?")
        content = data.get("content", "")
        voice_file_url = data.get("voiceFileUrl", "")

        # 从 topic 中提取 lightCode（smartlight/broadcast/L001）
        topic_parts = msg.topic.split("/")
        topic_light_code = topic_parts[-1] if len(topic_parts) >= 3 else None

        print(f"\n{'='*60}")
        print(f"🔔 [{timestamp()}] 收到广播命令")
        print(f"   Topic:    {msg.topic}")
        print(f"  路灯编号:  {light_code}")
        print(f"  广播内容:  {content[:80]}{'...' if len(content) > 80 else ''}")
        print(f"  语音地址:  {voice_file_url}")
        print(f"{'='*60}")

        # ---- 冷却检查 ----
        with _lock:
            now = time.time()
            last = _last_play_time.get(light_code, 0)
            if now - last < PLAY_COOLDOWN_SECONDS:
                remaining = PLAY_COOLDOWN_SECONDS - (now - last)
                print(f"  ⏱️  冷却中（{remaining:.1f}秒后可用），跳过本次播放")
                return
            _last_play_time[light_code] = now

        # ---- 下载语音文件 ----
        if not voice_file_url:
            print(f"  ⚠️  消息中没有 voiceFileUrl，仅记录不播放")
            print(f"  📝 广播内容: {content}")
            return

        tmp_file = download_voice(voice_file_url)
        if tmp_file is None:
            return

        # ---- 播放 ----
        print(f"  🔊 开始播放...")
        success = play_voice_file(tmp_file)

        if success:
            print(f"  ✅ 播放完成")

        # ---- 清理临时文件 ----
        if AUTO_DELETE_TEMP:
            try:
                os.remove(tmp_file)
                print(f"  🗑️  临时文件已删除")
            except Exception:
                pass

    except json.JSONDecodeError:
        print(f"  ⚠️  消息不是有效的 JSON: {msg.payload.decode('utf-8', errors='replace')[:100]}")
    except Exception as e:
        print(f"  ❌ 处理消息异常: {e}")


def get_lights_from_api():
    """从后端 API 获取有扬声器的路灯列表，用于信息展示"""
    try:
        resp = requests.get(f"{BACKEND_API}/lights", timeout=5)
        lights = resp.json().get("data", [])
        speaker_lights = [l for l in lights if l.get("hasSpeaker")]
        if speaker_lights:
            print(f"\n📋 已注册的有扬声器路灯:")
            for l in speaker_lights:
                print(f"   ID={l['id']}, 编号={l.get('lightCode', '?')}, "
                      f"名称={l.get('lightName', '?')}, "
                      f"位置={l.get('location', '?')}")
            print()
        else:
            print(f"⚠️  未找到带扬声器的路灯\n")
    except Exception as e:
        print(f"⚠️  查询路灯列表失败: {e}\n")


# ==================== 主入口 ====================

def main():
    print("=" * 60)
    print("  📢 广播播放模拟器")
    print("  模拟灯杆设备接收 MQTT 广播命令并播放语音")
    print("=" * 60)
    print(f"  MQTT Broker:  {MQTT_BROKER}:{MQTT_PORT}")
    print(f"  订阅主题:    {MQTT_TOPIC}")
    print(f"  后端 API:    {BACKEND_API}")
    print(f"  冷却时间:    {PLAY_COOLDOWN_SECONDS} 秒")
    print()

    # 展示路灯信息
    get_lights_from_api()

    # 创建 MQTT 客户端（使用 paho-mqtt v2 API）
    client = mqtt.Client(
        client_id=MQTT_CLIENT_ID,
        protocol=mqtt.MQTTv5,
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2
    )
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message

    # 连接 MQTT Broker
    print(f"🔌 正在连接 MQTT Broker...")
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
    except Exception as e:
        print(f"❌ 无法连接 MQTT Broker: {e}")
        print(f"   请确认 Broker 地址 {MQTT_BROKER}:{MQTT_PORT} 可达")
        return

    # 启动 MQTT 网络循环（非阻塞）
    client.loop_start()

    print(f"✅ 广播播放模拟器已启动\n")
    print(f"💡 提示:")
    print(f"   1. 确保后端服务已启动（{BACKEND_API} 可访问）")
    print(f"   2. 确保该广播已通过前端「生成语音」按钮生成了语音文件")
    print(f"   3. 在「广播策略」中配置人流量触发条件，启动策略")
    print(f"   4. 运行 test.py 发送人流量数据来触发广播")
    print(f"   5. 或使用 MQTTX 手动发送测试消息:")
    print(f"      Topic: smartlight/broadcast/L001")
    print(f'      Payload: {{"command":"broadcast","lightCode":"L001","content":"测试","voiceFileUrl":"http://localhost:8080/api/broadcast/broadcasts/1/voice-file"}}')
    print(f"\n⏳ 等待广播命令... (按 Ctrl+C 退出)\n")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"\n🛑 正在退出...")
    finally:
        client.loop_stop()
        client.disconnect()
        print(f"👋 已退出")


if __name__ == "__main__":
    main()
