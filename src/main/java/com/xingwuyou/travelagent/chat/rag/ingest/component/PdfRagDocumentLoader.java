package com.xingwuyou.travelagent.chat.rag.ingest.component;

import com.xingwuyou.travelagent.chat.rag.ingest.RagIngestionProperties;
import com.xingwuyou.travelagent.chat.rag.ingest.dto.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PdfRagDocumentLoader {
    private final PdfManifestLoader manifestLoader;
    private static final int PDF_CLASSIFY_WINDOW_PAGES = 3;
    private static final int MAX_CLASSIFIER_CHARS = 6000;
    private static final int MIN_CLASSIFIER_CHARS = 120;
    private static final Logger log = LoggerFactory.getLogger(PdfRagDocumentLoader.class);

    private final PdfSectionClassifier sectionClassifier;

    public PdfRagDocumentLoader(PdfManifestLoader manifestLoader,
                                PdfSectionClassifier sectionClassifier) {
        this.manifestLoader = manifestLoader;
        this.sectionClassifier=sectionClassifier;
    }
    private record PdfPageText(int pageNumber, String text) {}

    private record PdfPageWindow(int pageStart, int pageEnd, String text) {}

    public List<RagRawDocument> loadConfiguredPdfs() {
        PdfIngestionManifest manifest = manifestLoader.load();

        if (!manifest.enabled()) {
            return List.of();
        }

        return manifest.safeSources().stream()
                .map(this::loadPdf)
                .filter(document -> StringUtils.hasText(document.content()))
                .toList();
    }

    private RagRawDocument loadPdf(PdfIngestionManifest.PdfSource source) {
        File file = new File(source.path());
        if (!file.exists()) {
            throw new IllegalStateException("Configured RAG PDF does not exist: " + source.path());
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            List<PdfPageText> pages = readPages(document);
            if (pages.isEmpty()) {
                throw new IllegalStateException("No readable text extracted from RAG PDF: " + source.path());
            }

            StringBuilder raw = new StringBuilder();

            for (PdfPageWindow window : toWindows(pages)) {
                PdfSectionClassification classification = classifyWindow(source, window);

                for (PdfSectionMetadata section : safeSections(classification)) {
                    //需要加日志
                    if (!section.suitableForRag()) {
                        log.debug(
                                "Skip PDF section for RAG. source={}, pages={}-{}, title={}, reason={}",
                                source.sourceName(),
                                section.pageStart(),
                                section.pageEnd(),
                                section.sectionTitle(),
                                section.rejectReason()
                        );
                        continue;
                    }

                    PdfSectionMetadata normalizedSection = normalizeSection(section, window);
                    String sectionContent = trustedSectionContent(normalizedSection, window);

                    raw.append(renderSection(source, normalizedSection, sectionContent));
                    raw.append("\n");
                }
            }

            if (!StringUtils.hasText(raw)) {
                throw new IllegalStateException(
                        "No suitable PDF RAG sections extracted from: " + source.path()
                                + ". Check PDF classifier model/API balance/prompt/schema."
                );
            }

            return new RagRawDocument(file.getName(), raw.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read RAG PDF: " + source.path(), e);
        }
    }

    //防止LLM编造section content
    private String trustedSectionContent(PdfSectionMetadata section, PdfPageWindow window) {
        String content = section.content();

        if (!StringUtils.hasText(content) || content.length() < MIN_CLASSIFIER_CHARS) {
            return window.text();
        }

        String normalizedWindow = normalizeForContainment(window.text());
        String normalizedContent = normalizeForContainment(content);

        if (!normalizedWindow.contains(normalizedContent)) {
            return window.text();
        }

        return content;
    }

    private String normalizeForContainment(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isWhitespace(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private PdfSectionMetadata normalizeSection(PdfSectionMetadata section, PdfPageWindow window) {
        int pageStart = clampPage(section.pageStart(), window);
        int pageEnd = clampPage(section.pageEnd(), window);

        if (pageEnd < pageStart) {
            pageEnd = pageStart;
        }

        PdfKnowledgeCategory category = section.category() == null
                ? PdfKnowledgeCategory.OTHER_TRAVEL_INFO
                : section.category();

        String topic = StringUtils.hasText(section.topic())
                ? section.topic()
                : category.label();

        String title = StringUtils.hasText(section.sectionTitle())
                ? section.sectionTitle()
                : topic;

        return new PdfSectionMetadata(
                category,
                topic,
                title,
                nullSafe(section.sectionSummary()),
                pageStart,
                pageEnd,
                section.suitableForRag(),
                nullSafe(section.rejectReason()),
                section.content()
        );
    }

    private int clampPage(int page, PdfPageWindow window) {
        if (page < window.pageStart()) {
            return window.pageStart();
        }
        if (page > window.pageEnd()) {
            return window.pageEnd();
        }
        return page;
    }

    private List<PdfPageWindow> toWindows(List<PdfPageText> pages) {
        List<PdfPageWindow> windows = new ArrayList<>();

        for (int i = 0; i < pages.size(); i += PDF_CLASSIFY_WINDOW_PAGES) {
            List<PdfPageText> slice = pages.subList(
                    i,
                    Math.min(i + PDF_CLASSIFY_WINDOW_PAGES, pages.size())
            );

            int pageStart = slice.get(0).pageNumber();
            int pageEnd = slice.get(slice.size() - 1).pageNumber();

            String text = slice.stream()
                    .map(page -> "第 " + page.pageNumber() + " 页\n" + page.text())
                    .collect(Collectors.joining("\n\n"));

            windows.add(new PdfPageWindow(pageStart, pageEnd, text));
        }

        return windows;
    }

    private List<PdfPageText> readPages(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        List<PdfPageText> pages = new ArrayList<>();

        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);

            String text = cleanText(stripper.getText(document));
            if (StringUtils.hasText(text)) {
                pages.add(new PdfPageText(page, text));
            }
        }

        return pages;
    }

    private PdfSectionClassification classifyWindow(
            PdfIngestionManifest.PdfSource source,
            PdfPageWindow window
    ) {
        String text = window.text();

        if (!StringUtils.hasText(text) || text.length() < MIN_CLASSIFIER_CHARS) {
            log.warn("Skip PDF classification because window text is too short. source={}, pages={}-{}",
                    source.sourceName(), window.pageStart(), window.pageEnd());
            return fallbackClassification(source, window, "window text is too short");
        }

        String clippedText = text.length() > MAX_CLASSIFIER_CHARS
                ? text.substring(0, MAX_CLASSIFIER_CHARS)
                : text;

        try {
            PdfSectionClassification result = sectionClassifier.classify(
                    source.city(),
                    source.sourceName(),
                    source.category(),
                    window.pageStart(),
                    window.pageEnd(),
                    clippedText
            );

            if (result == null || result.sections() == null || result.sections().isEmpty()) {
                log.warn("PDF classification returned empty sections. source={}, pages={}-{}",
                        source.sourceName(), window.pageStart(), window.pageEnd());
                return fallbackClassification(source, window, "empty classification");
            }

            log.info("PDF classification succeeded. source={}, pages={}-{}, sections={}",
                    source.sourceName(), window.pageStart(), window.pageEnd(), result.sections().size());

            return result;
        } catch (Exception ex) {
            log.warn("PDF classification failed. source={}, pages={}-{}, reason={}",
                    source.sourceName(), window.pageStart(), window.pageEnd(), ex.getMessage(), ex);
            return fallbackClassification(source, window, ex.getMessage());
        }
    }

    private PdfSectionClassification fallbackClassification(
            PdfIngestionManifest.PdfSource source,
            PdfPageWindow window,
            String reason
    ) {
        String title = source.sourceName() + " 第 " + window.pageStart() + "-" + window.pageEnd() + " 页";
        String rejectReason = "PDF section classification failed: " + nullSafe(reason);

        PdfSectionMetadata fallback = new PdfSectionMetadata(
                PdfKnowledgeCategory.OTHER_TRAVEL_INFO,
                "PDF分类失败",
                title,
                rejectReason,
                window.pageStart(),
                window.pageEnd(),
                false,
                rejectReason,
                window.text()
        );

        return new PdfSectionClassification(List.of(fallback));
    }

    private String renderSection(
            PdfIngestionManifest.PdfSource source,
            PdfSectionMetadata section,
            String text
    ) {
        return """
        ---
        city: "%s"
        category: "%s"
        topic: "%s"
        sectionTitle: "%s"
        sectionSummary: "%s"
        sourceTopic: "%s"
        source: "%s"
        sourceId: "%s"
        sourceType: "PDF"
        pageStart: "%d"
        pageEnd: "%d"
        verifiedAt: "%s"
        confidenceLevel: "%s"
        ---
        %s
        """.formatted(
                yaml(source.city()),
                categoryLabel(section.category()),
                yaml(section.topic()),
                yaml(section.sectionTitle()),
                yaml(section.sectionSummary()),
                yaml(source.category()),
                yaml(source.sourceName()),
                yaml(source.id()),
                section.pageStart(),
                section.pageEnd(),
                yaml(source.verifiedAt()),
                yaml(source.confidenceLevel()),
                text
        );
    }

    private List<PdfSectionMetadata> safeSections(PdfSectionClassification classification) {
        if (classification == null || classification.sections() == null) {
            return List.of();
        }
        return classification.sections();
    }

    private String yaml(String value) {
        return nullSafe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .trim();
    }

    private String categoryLabel(PdfKnowledgeCategory category) {
        return category == null
                ? PdfKnowledgeCategory.OTHER_TRAVEL_INFO.label()
                : category.label();
    }

    private String cleanText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }

        return raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(line -> !line.matches("^\\d+$"))
                .filter(line -> !line.matches("^第\\s*\\d+\\s*页.*$"))
                .collect(Collectors.joining("\n"));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}