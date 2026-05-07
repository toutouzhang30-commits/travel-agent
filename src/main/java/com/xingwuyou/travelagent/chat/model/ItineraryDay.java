package com.xingwuyou.travelagent.chat.model;

//标记第几天，还有三分法行程规划
public record ItineraryDay(Integer dayNumber,
                           String date,
                           String weatherSummary,
                           ItinerarySlot morning,
                           ItinerarySlot afternoon,
                           ItinerarySlot evening) {
}
