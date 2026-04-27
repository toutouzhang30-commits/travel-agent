package com.xingwuyou.travelagent.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

//专门绑定 travel.tools.amap.* 配置
@ConfigurationProperties(prefix = "travel.tools.amap")
public record AmapProperties(String apiKey,
                             String baseUrl,
                             int connectTimeout,
                             int readTimeout) {
}
