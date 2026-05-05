package com.xingwuyou.travelagent.chat.rag.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
