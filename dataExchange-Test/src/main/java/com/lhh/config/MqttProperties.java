package com.lhh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MQTT 监听配置属性类
 * <p>
 * 绑定 application.yaml 中 mqtt.* 前缀的配置项，
 * 供 {@link MqttClientConfig} 建立连接和订阅 topic 使用。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/30 15:00
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    /** broker 地址，如 tcp://localhost:1883 */
    private String serverUrl;

    /** 客户端 ID，留空则自动生成 */
    private String clientId;

    /** broker 用户名（无认证可为 null） */
    private String username;

    /** broker 密码（无认证可为 null） */
    private String password;

    /** 订阅的 topic 列表，支持 + / # 通配符 */
    private List<String> topics;

    /** 订阅 QoS：0-最多一次 1-至少一次 2-恰好一次 */
    private int qos = 1;

    /** keep-alive 心跳间隔（秒） */
    private int keepAlive = 60;

    /** 连接超时（秒） */
    private int connectionTimeout = 10;
}
