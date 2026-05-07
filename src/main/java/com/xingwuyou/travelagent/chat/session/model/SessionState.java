package com.xingwuyou.travelagent.chat.session.model;

import com.xingwuyou.travelagent.chat.model.Itinerary;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;

//它是一个 record，代表了某一次对话在某一时刻的完整快照
//之前只保存需求和计划表，现在话需要保存工作记忆
public record SessionState(TripRequirement requirement,
                           Itinerary itinerary,
                           WorkingMemory memory
) {
    public SessionState(TripRequirement requirement, Itinerary itinerary) {
        this(requirement, itinerary, WorkingMemory.empty());
    }

    public SessionState merge(TripRequirement nextRequirement, Itinerary nextItinerary, WorkingMemory nextMemory) {
        return new SessionState(
                nextRequirement != null ? nextRequirement : requirement,
                nextItinerary != null ? nextItinerary : itinerary,
                nextMemory != null ? nextMemory : memory
        );
    }
    public SessionState merge(TripRequirement nextRequirement, Itinerary nextItinerary) {
        return merge(nextRequirement, nextItinerary, memory);
    }

}
