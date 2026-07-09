package com.smartlight.backend.event;

import org.springframework.context.ApplicationEvent;

/**
 * MQTT 重连成功事件。
 * MqttConfig 在 connectComplete(reconnect=true) 时发布，
 * MqttPublishService 监听此事件后执行全量状态同步。
 */
public class MqttReconnectedEvent extends ApplicationEvent {

    public MqttReconnectedEvent(Object source) {
        super(source);
    }
}