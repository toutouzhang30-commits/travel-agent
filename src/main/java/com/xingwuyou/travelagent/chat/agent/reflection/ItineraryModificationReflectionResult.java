package com.xingwuyou.travelagent.chat.agent.reflection;

public record ItineraryModificationReflectionResult(boolean requiresRevision,
                                                    String revisionInstruction,
                                                    String publicSummary) {
}
