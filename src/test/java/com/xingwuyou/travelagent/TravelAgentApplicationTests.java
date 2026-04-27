package com.xingwuyou.travelagent;

import com.xingwuyou.travelagent.chat.component.RequirementCompletenessChecker;
import com.xingwuyou.travelagent.chat.component.TripRequirementExtractor;
import com.xingwuyou.travelagent.chat.dto.TripRequirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TravelAgentApplicationTests {

    @Autowired
    private RequirementCompletenessChecker checker;

    @Autowired
    private TripRequirementExtractor extractor;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExtractNumericBudgetFromBudgetFollowUp() {
        TripRequirement current = new TripRequirement("北京", 5, null, null, List.of());

        TripRequirement extracted = extractor.extract("300", current, List.of("budget"));

        assertThat(extracted.budget()).isEqualTo("300");
        assertThat(extracted.destination()).isNull();
        assertThat(extracted.tripDays()).isNull();
    }

    @Test
    void shouldExtractNumericTripDaysFromTripDaysFollowUp() {
        TripRequirement current = new TripRequirement("上海", null, "3000", null, List.of());

        TripRequirement extracted = extractor.extract("3", current, List.of("tripDays"));

        assertThat(extracted.tripDays()).isEqualTo(3);
        assertThat(extracted.budget()).isNull();
        assertThat(extracted.destination()).isNull();
    }

    @Test
    void shouldPreserveCollectedContextAcrossTurns() {
        TripRequirement current = new TripRequirement("杭州", 3, null, null, List.of());
        TripRequirement extracted = extractor.extract("300", current, List.of("budget"));
        TripRequirement merged = new TripRequirement(
                extracted.destination() != null ? extracted.destination() : current.destination(),
                extracted.tripDays() != null ? extracted.tripDays() : current.tripDays(),
                extracted.budget() != null ? extracted.budget() : current.budget(),
                extracted.pacePreference() != null ? extracted.pacePreference() : current.pacePreference(),
                current.interests()
        );

        assertThat(merged.destination()).isEqualTo("杭州");
        assertThat(merged.tripDays()).isEqualTo(3);
        assertThat(merged.budget()).isEqualTo("300");
        assertThat(checker.findMissingFields(merged)).isEmpty();
    }
}
