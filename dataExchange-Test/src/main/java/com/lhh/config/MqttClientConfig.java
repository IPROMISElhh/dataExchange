package com.lhh.config;

import com.lhh.mqtt.MqttDataLogListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * MQTT 客户端配置与订阅入口
 * <p>
 * 应用启动时（@PostConstruct）连接 broker 并订阅配置的 topic 列表，
 * 收到消息后交由 {@link MqttDataLogListener} 解析入库；
 * 应用关闭时（@PreDestroy）断开连接并释放资源。
 * </p>
 * <p>
 * 连接稳定性：开启 automaticReconnect 断线自动重连，
 * 并在 connected / reconnected 回调中重复执行订阅（cleanSession=true 时
 * 重连后 broker 不保留旧订阅，必须重新 subscribe）。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/30 15:00
 */
@Slf4j
@Component
public class MqttClientConfig {

    /** MQTT 配置属性（broker 地址、topic 列表、QoS 等） */
    @Autowired
    private MqttProperties mqttProperties;

    /** 消息监听处理器：topic 消息 → 解析 → 热表落库 */
    @Autowired
    private MqttDataLogListener mqttDataLogListener;

    /** Paho MQTT 客户端，持有连接与订阅会话 */
    private MqttClient mqttClient;

    /**
     * 应用启动后建立连接并注册回调、订阅 topic
     */
    @PostConstruct
    public void connect() {
        try {
            // ① 客户端 ID：配置为空则自动生成，避免 broker 端 clientId 冲突被踢线
            String clientId = mqttProperties.getClientId();
            if (clientId == null || clientId.isBlank()) {
                clientId = "mqtt-data-log-" + UUID.randomUUID();
            }

            // ② 创建客户端（内存持久化，QoS>0 的在途消息不落磁盘）
            mqttClient = new MqttClient(mqttProperties.getServerUrl(), clientId, new MemoryPersistence());

            // ③ 连接参数：认证、心跳、断线自动重连
            MqttConnectOptions options = new MqttConnectOptions();
            if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isBlank()) {
                options.setUserName(mqttProperties.getUsername());
                options.setPassword(mqttProperties.getPassword() == null
                        ? new char[0] : mqttProperties.getPassword().toCharArray());
            }
            options.setCleanSession(true);                       // 不保留会话，重连后需重新订阅
            options.setAutomaticReconnect(true);                 // 断线自动重连
            options.setKeepAliveInterval(mqttProperties.getKeepAlive());
            options.setConnectionTimeout(mqttProperties.getConnectionTimeout());

            // ④ 注册回调：连接建立（含重连成功）时重新订阅，收到消息时分发给监听器
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT {} serverURI={}", reconnect ? "重连成功" : "连接成功", serverURI);
                    subscribeTopics();
                }

                @Override
                public void connectionLost(Throwable cause) {
                    // automaticReconnect 已开启，broker 会自行重连，这里仅告警
                    log.warn("MQTT 连接断开，等待自动重连", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Paho 投递线程：分发给业务监听器，内部已捕获异常，不会中断接收
                    mqttDataLogListener.handle(topic, new String(message.getPayload(), StandardCharsets.UTF_8));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 纯订阅端不涉及发布回执，留空
                }
            });

            // ⑤ 发起连接（首次订阅在 connectComplete 回调中完成）
            mqttClient.connect(options);
            log.info("MQTT 客户端已启动 clientId={}, serverUrl={}", clientId, mqttProperties.getServerUrl());
        } catch (Exception e) {
            log.error("MQTT 客户端启动失败，消息监听功能不可用", e);
        }
    }

    /**
     * 订阅配置的 topic 列表（首次连接和自动重连后都会调用）
     */
    private void subscribeTopics() {
        List<String> topics = mqttProperties.getTopics();
        if (CollectionUtils.isEmpty(topics)) {
            log.warn("MQTT topics 配置为空，未订阅任何主题");
            return;
        }
        int qos = mqttProperties.getQos();
        for (String topic : topics) {
            try {
                mqttClient.subscribe(topic, qos);
                log.info("MQTT 订阅成功 topic={}, qos={}", topic, qos);
            } catch (Exception e) {
                log.error("MQTT 订阅失败 topic={}, qos={}", topic, qos, e);
            }
        }
    }

    /**
     * 应用关闭时断开 MQTT 连接，释放 Paho 线程资源
     */
    @PreDestroy
    public void disconnect() {
        if (mqttClient == null) {
            return;
        }
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
            log.info("MQTT 客户端已断开连接");
        } catch (Exception e) {
            log.warn("MQTT 客户端关闭异常", e);
        }
    }
}
