package com.xingwuyou.travelagent.chat.tool.maps;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

//城市解析器
@Component
public class AmapDistrictResolver {
    //高德需要城市的地标
    private static final Map<String, String> CITY_ADCODE = Map.of(
            "北京", "110000",
            "上海", "310000",
            "杭州", "330100"
    );

    public String resolveAdcode(String city) {
        if (!StringUtils.hasText(city)) {
            return null;
        }

        String normalized = city.replace("市", "").trim();

        //其实就是一个翻译官，将杭州变成高德能看懂的地表
        for (Map.Entry<String, String> entry : CITY_ADCODE.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
