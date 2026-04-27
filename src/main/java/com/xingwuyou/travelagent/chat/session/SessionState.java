package com.xingwuyou.travelagent.chat.session;

import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;

//它是一个 record，代表了某一次对话在某一时刻的完整快照
public record SessionState(TripRequirement requirement,
                           Itinerary itinerary) {

    public SessionState merge(TripRequirement nextRequirement, Itinerary nextItinerary) {
        return new SessionState(
                nextRequirement != null ? nextRequirement : requirement,
                nextItinerary != null ? nextItinerary : itinerary
        );
    }
}
