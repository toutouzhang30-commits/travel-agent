package com.xingwuyou.travelagent.chat.rag.ingest;


import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

//启动自动入库
//每次应用启动时执行
@Component
public class RagBootstrapRunner implements ApplicationRunner {
    private final RagIngestionService ragIngestionService;
    public RagBootstrapRunner(RagIngestionService ragIngestionService){
        this.ragIngestionService=ragIngestionService;
    }
    @Override
    public void run(ApplicationArguments args) {
        ragIngestionService.ingestAll();
    }
}
