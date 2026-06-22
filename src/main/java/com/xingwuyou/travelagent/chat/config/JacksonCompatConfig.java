package com.xingwuyou.travelagent.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonCompatConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    @Bean
    public tools.jackson.databind.ObjectMapper toolsObjectMapper() {
        return new tools.jackson.databind.ObjectMapper();
    }
}