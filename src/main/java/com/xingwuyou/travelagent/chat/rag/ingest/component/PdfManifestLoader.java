package com.xingwuyou.travelagent.chat.rag.ingest.component;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.PdfIngestionManifest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfManifestLoader {
    private static final String MANIFEST_LOCATION = "classpath:/rag/pdf-manifest.yml";

    private final ResourceLoader resourceLoader;
    private final YAMLMapper yamlMapper;

    public PdfManifestLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
        this.yamlMapper = new YAMLMapper();
    }

    public PdfIngestionManifest load() {
        Resource resource = resourceLoader.getResource(MANIFEST_LOCATION);

        if (!resource.exists()) {
            return new PdfIngestionManifest(false, java.util.List.of());
        }

        try (var inputStream = resource.getInputStream()) {
            PdfIngestionManifest manifest = yamlMapper.readValue(inputStream, PdfIngestionManifest.class);
            if (manifest == null) {
                return new PdfIngestionManifest(false, java.util.List.of());
            }
            return manifest;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load PDF RAG manifest: " + MANIFEST_LOCATION, e);
        }
    }
}