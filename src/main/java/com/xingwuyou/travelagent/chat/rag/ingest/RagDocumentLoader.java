package com.xingwuyou.travelagent.chat.rag.ingest;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

//读取文档内容
@Component
public class RagDocumentLoader {
    public List<String> loadAllMarkdown(){
        try {
            Resource[] resources=new PathMatchingResourcePatternResolver().getResources("classpath:/rag/*.md");
            List<String> documents=new ArrayList<>();
            for(Resource resource:resources){
                String content=new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                documents.add(content);
            }
            return documents;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rag documents", e);
        }
    }
}
