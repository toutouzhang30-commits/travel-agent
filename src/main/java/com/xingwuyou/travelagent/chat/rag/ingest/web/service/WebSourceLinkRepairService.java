package com.xingwuyou.travelagent.chat.rag.ingest.web.service;

import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceLinkRepository;
import com.xingwuyou.travelagent.chat.rag.ingest.web.repository.WebSourceRepository;
import org.springframework.stereotype.Service;

@Service
public class WebSourceLinkRepairService {
    private final WebSourceRepository sourceRepository;
    private final WebSourceLinkRepository linkRepository;

    public WebSourceLinkRepairService(
            WebSourceRepository sourceRepository,
            WebSourceLinkRepository linkRepository
    ) {
        this.sourceRepository = sourceRepository;
        this.linkRepository = linkRepository;
    }

    public void resetForFullRecrawl() {
        sourceRepository.disablePromotedSourcesForRecrawl();
        linkRepository.clearAllLinksForFullRecrawl();
    }
}