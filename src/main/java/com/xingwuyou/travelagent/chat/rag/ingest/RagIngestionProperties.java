package com.xingwuyou.travelagent.chat.rag.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

//入库配置类，用于读取 application.yml 里的 travel.rag.ingestion.* 配置
//将它们映射为强类型的 Java 属性供 RagIngestionService 使用
@Component
@ConfigurationProperties(prefix = "travel.rag.ingestion")
public class RagIngestionProperties {
    //是否开启启动时检查并入库
    private boolean enabled = true;
    //入库批次的命名
    private String namespace = "bootstrap-rag-v1";
    //处理流水线版本，用于hash的计算
    private String pipelineVersion = "rag-bootstrap-v1";
    //当检测到hash变化时，是否清理旧数据并重建
    private boolean autoRebuild = true;
    private Pdf pdf = new Pdf();

    //这是对应的setter和getter方法
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPipelineVersion() {
        return pipelineVersion;
    }

    public void setPipelineVersion(String pipelineVersion) {
        this.pipelineVersion = pipelineVersion;
    }

    public boolean isAutoRebuild() {
        return autoRebuild;
    }

    public void setAutoRebuild(boolean autoRebuild) {
        this.autoRebuild = autoRebuild;
    }
    public Pdf getPdf() {
        return pdf;
    }

    public void setPdf(Pdf pdf) {
        this.pdf = pdf;
    }

    public static class Pdf {
        private boolean enabled = false;
        private List<PdfSource> sources = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<PdfSource> getSources() {
            return sources;
        }

        public void setSources(List<PdfSource> sources) {
            this.sources = sources;
        }
    }

    public static class PdfSource {
        private String city;
        private String category;
        private String sourceName;
        private String path;
        private String verifiedAt;
        private String confidenceLevel = "medium";

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = sourceName;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getVerifiedAt() {
            return verifiedAt;
        }

        public void setVerifiedAt(String verifiedAt) {
            this.verifiedAt = verifiedAt;
        }

        public String getConfidenceLevel() {
            return confidenceLevel;
        }

        public void setConfidenceLevel(String confidenceLevel) {
            this.confidenceLevel = confidenceLevel;
        }
    }
}
