package com.xingwuyou.travelagent.chat.agent.reflection;

public record KnowledgeReflectionResult(boolean requiresRevision,
                                        String revisionInstruction,
                                        String publicSummary) {
}
