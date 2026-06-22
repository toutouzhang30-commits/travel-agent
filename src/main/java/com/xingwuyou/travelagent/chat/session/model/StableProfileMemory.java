package com.xingwuyou.travelagent.chat.session.model;

import java.util.List;

public record StableProfileMemory(String destination,
                                  Integer tripDays,
                                  String budget,
                                  String pacePreference,
                                  List<String> interests,
                                  String startDate) {
}
