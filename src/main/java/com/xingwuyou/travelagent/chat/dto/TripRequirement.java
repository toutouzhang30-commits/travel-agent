package com.xingwuyou.travelagent.chat.dto;

import java.util.List;

//DTO，数据传输对象
public record TripRequirement(String destination,
                              Integer tripDays,
                              String budget,
                              String pacePreference,
                              List<String> interests) {
}
