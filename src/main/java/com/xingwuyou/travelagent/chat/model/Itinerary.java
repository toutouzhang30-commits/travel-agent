package com.xingwuyou.travelagent.chat.model;

import java.util.List;

//冗余保存核心信息，前两个方便前端在标题显示
//天数需要串起来
public record Itinerary(String destination,
                        Integer tripDays,
                        List<ItineraryDay> days) {
}
