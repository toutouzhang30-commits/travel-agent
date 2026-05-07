package com.xingwuyou.travelagent.chat.agent.reflection;

public record ItineraryReflectionResult(boolean requiresRevision,
                                        String revisionInstruction,
                                        String publicSummary
) {
}
