package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@Component
public class WebSourceLinkRepairRunner implements org.springframework.boot.ApplicationRunner {
    private final WebSourceLinkRepairService repairService;

    @Value("${travel.rag.web.repair.full-recrawl:false}")
    private boolean fullRecrawl;

    public WebSourceLinkRepairRunner(WebSourceLinkRepairService repairService) {
        this.repairService = repairService;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (!fullRecrawl) {
            return;
        }
        repairService.resetForFullRecrawl();
    }
}