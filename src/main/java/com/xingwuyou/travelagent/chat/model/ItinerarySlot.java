package com.xingwuyou.travelagent.chat.model;

//最小原子
//活动，去这个活动的原因，活动的大概花销
public record ItinerarySlot(String activityName,
                            String reason,
                            String budgetNote) {
}
