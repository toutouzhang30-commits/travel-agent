package com.xingwuyou.travelagent.chat.component;

import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

//对完整性进行检查
@Component
public class RequirementCompletenessChecker {
    public List<String> findMissingFields(TripRequirement tripRequirement){
        TripRequirement requirement = normalize(tripRequirement);
        List<String> miss=new ArrayList<>();
        if(!StringUtils.hasText(requirement.destination())){
            miss.add("destination");
        }
        if(!StringUtils.hasText(requirement.budget())){
            miss.add("budget");
        }
        if(requirement.tripDays()==null||requirement.tripDays()<=0){
            miss.add("tripDays");
        }
        return miss;
    }

    public List<String> findMissingPreferencFields(TripRequirement tripRequirement){
        TripRequirement requirement = normalize(tripRequirement);
        boolean hasPace = StringUtils.hasText(requirement.pacePreference());
        boolean hasInterests = requirement.interests() != null && !requirement.interests().isEmpty();

        if (hasPace || hasInterests) {
            return List.of();
        }
        return List.of("preference");
    }

    public boolean isReady(TripRequirement tripRequirement){
        return findMissingFields(tripRequirement).isEmpty();
    }

    private TripRequirement normalize(TripRequirement tripRequirement) {
        return tripRequirement == null
                ? new TripRequirement(null, null, null, null, List.of())
                : tripRequirement;
    }
}
