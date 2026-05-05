package com.xingwuyou.travelagent.chat.tool.weather;

import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolRequest;
import com.xingwuyou.travelagent.chat.tool.weather.dto.WeatherToolResponse;
import com.xingwuyou.travelagent.chat.tool.weather.service.WeatherToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WeatherTool {
    private final WeatherToolService weatherToolService;

    public WeatherTool(WeatherToolService weatherToolService) {
        this.weatherToolService = weatherToolService;
    }

    @Tool(description = """
            查询指定城市未来几天的天气预报。
            只用于实时天气、气温、是否下雨、未来几天天气等问题。
            不用于回答“雨天怎么玩”这类静态旅游玩法问题。
            """)
    //辅助模型将用户散乱的自然语言（如“杭州明天”）转化为结构化的 WeatherToolRequest 对象
    public WeatherToolResponse queryWeather(
            @ToolParam(description = "天气查询结构化参数，包含城市、日期偏移、查询天数和原始问题。")
            WeatherToolRequest request
    ) {
        //调用第三方的逻辑
        return weatherToolService.execute(request);
    }
}
