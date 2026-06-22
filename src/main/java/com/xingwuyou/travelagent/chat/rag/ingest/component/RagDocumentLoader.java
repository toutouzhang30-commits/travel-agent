package com.xingwuyou.travelagent.chat.rag.ingest.component;

import com.xingwuyou.travelagent.chat.rag.ingest.dto.RagRawDocument;
import com.xingwuyou.travelagent.chat.rag.ingest.web.service.WebRagDocumentLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

//读取文档内容，负责读到内存中
@Component
public class RagDocumentLoader {
    private final WebRagDocumentLoader webRagDocumentLoader;
    private final PdfRagDocumentLoader pdfRagDocumentLoader;

    public RagDocumentLoader(
            WebRagDocumentLoader webRagDocumentLoader,
            PdfRagDocumentLoader pdfRagDocumentLoader
    ) {
        this.webRagDocumentLoader = webRagDocumentLoader;
        this.pdfRagDocumentLoader = pdfRagDocumentLoader;
    }

    public List<RagRawDocument> loadAllMarkdown() {
        //List<RagRawDocument> markdown = loadClasspathMarkdown();
        //List<RagRawDocument> web = webRagDocumentLoader.loadEnabledWebSources();
        //return Stream.concat(markdown.stream(), web.stream()).toList();
        List<RagRawDocument> markdown = loadClasspathMarkdown();
        List<RagRawDocument> pdf = pdfRagDocumentLoader.loadConfiguredPdfs();

        return Stream.concat(markdown.stream(), pdf.stream()).toList();
    }

    private List<RagRawDocument> loadClasspathMarkdown() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:/rag/*.md");

            return Arrays.stream(resources)
                    .sorted(Comparator.comparing(this::safeFilename))
                    .map(this::toRawDocument)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rag documents", e);
        }
    }



    //防止空指针异常
    private String safeFilename(Resource resource) {
        return resource.getFilename() == null ? resource.getDescription() : resource.getFilename();
    }

    private RagRawDocument toRawDocument(Resource resource) {
        try {
            String filename = safeFilename(resource);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new RagRawDocument(filename, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read rag document: " + safeFilename(resource), e);
        }
    }

}
