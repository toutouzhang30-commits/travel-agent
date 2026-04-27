package com.xingwuyou.travelagent.chat.tool.weather.service;

import com.xingwuyou.travelagent.chat.dto.SourceReferenceDto;
import com.xingwuyou.travelagent.chat.tool.amap.AmapDistrictResolver;
import com.xingwuyou.travelagent.chat.tool.weather.client.AmapWeatherClient;
import com.xingwuyou.travelagent.chat.tool.weather.dto.AmapForecastResponse;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WeatherToolService {
    private static final String AMAP_WEATHER_DOC_URL =
            "https://lbs.amap.com/api/webservice/guide/api/weatherinfo";

    private final AmapDistrictResolver districtResolver;
    private final AmapWeatherClient amapWeatherClient;

    public WeatherToolService(
            AmapDistrictResolver districtResolver,
            AmapWeatherClient amapWeatherClient
    ) {
        this.districtResolver = districtResolver;
        this.amapWeatherClient = amapWeatherClient;
    }

    public WeatherToolResponse execute(WeatherToolRequest request) {
        //参数检查，检查传过来的是否为空
        if (request == null || !StringUtils.hasText(request.city())) {
            return failure(request, "缺少城市信息，暂时无法查询天气。");
        }

        //城市编码
        String adcode = districtResolver.resolveAdcode(request.city());
        if (!StringUtils.hasText(adcode)) {
            return failure(request, "当前天气工具暂只支持北京、上海、杭州。");
        }

        try {
            //发起查询
            AmapForecastResponse response = amapWeatherClient.queryForecast(adcode);
            if (!"1".equals(response.status())
                    || response.forecasts() == null
                    || response.forecasts().isEmpty()) {
                return failure(request, "高德天气接口调用失败：" + response.info());
            }

            //这个获取的是json里面的城市
            AmapForecastResponse.Forecast forecast = response.forecasts().get(0);

            //高德返回了几天的数据，询问是否超界
            if (forecast.casts() == null || forecast.casts().size() <= request.dayOffset()) {
                return failure(request, "高德天气没有返回对应日期的预报。");
            }

            AmapForecastResponse.Cast cast = forecast.casts().get(request.dayOffset());

            SourceReferenceDto source = SourceReferenceDto.tool(
                    forecast.city(),
                    "高德开放平台",
                    AMAP_WEATHER_DOC_URL,
                    forecast.reporttime(),
                    "高德天气预报查询",
                    "WeatherTool"
            );

            return new WeatherToolResponse(
                    true,
                    forecast.city(),
                    request.dayOffset(),
                    cast.date(),
                    cast.dayweather(),
                    cast.nightweather(),
                    cast.daytemp(),
                    cast.nighttemp(),
                    cast.daywind(),
                    cast.nightwind(),
                    buildAdvice(cast),
                    source,
                    forecast.reporttime(),
                    null
            );
        } catch (Exception e) {
            return failure(request, "天气查询失败：" + e.getMessage());
        }
    }

    private String buildAdvice(AmapForecastResponse.Cast cast) {
        String dayWeather = cast.dayweather() == null ? "" : cast.dayweather();
        Integer dayTemp = parseInt(cast.daytemp());

        if (dayWeather.contains("雨")) {
            return "建议准备雨具，并优先安排室内或更灵活的活动。";
        }
        if (dayTemp != null && dayTemp >= 32) {
            return "白天气温较高，建议避开正午暴晒时段。";
        }
        if (dayTemp != null && dayTemp <= 8) {
            return "气温偏低，建议注意保暖并控制室外停留时长。";
        }
        return "整体天气较稳定，可按原计划安排室外活动。";
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    //标准化错误输出
    private WeatherToolResponse failure(WeatherToolRequest request, String errorMessage) {
        SourceReferenceDto source = SourceReferenceDto.tool(
                request == null ? null : request.city(),
                "高德开放平台",
                AMAP_WEATHER_DOC_URL,
                null,
                "高德天气预报查询",
                "WeatherTool"
        );

        return new WeatherToolResponse(
                false,
                request == null ? null : request.city(),
                request == null ? 0 : request.dayOffset(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                source,
                null,
                errorMessage
        );
    }
}
