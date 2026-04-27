package com.xingwuyou.travelagent.chat.tool.weather.client;

import com.xingwuyou.travelagent.chat.config.AmapProperties;
import com.xingwuyou.travelagent.chat.tool.weather.dto.AmapForecastResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AmapWeatherClient {
    //从高德里面取回数据
    private final RestClient amapRestClient;
    private final AmapProperties amapProperties;

    public AmapWeatherClient(
            @Qualifier("amapRestClient") RestClient amapRestClient,
            AmapProperties amapProperties
    ) {
        this.amapRestClient = amapRestClient;
        this.amapProperties = amapProperties;
    }

    public AmapForecastResponse queryForecast(String cityAdcode) {
        AmapForecastResponse response = amapRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/weather/weatherInfo")//接口路径
                        .queryParam("key", amapProperties.apiKey())//通行码
                        .queryParam("city", cityAdcode)
                        .queryParam("extensions", "all")
                        .build())
                .retrieve()
                //json转换成java对象
                .body(AmapForecastResponse.class);

        if (response == null) {
            throw new IllegalStateException("高德天气返回为空");
        }
        return response;
    }
}
