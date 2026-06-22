package com.xingwuyou.travelagent.chat.agent.reaction;

public record ToolRepairDecision(ToolRepairAction action,
                                 String rewrittenMessage,
                                 String publicMessage) {
}
