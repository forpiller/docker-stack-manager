package com.personal.dockermanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("app")
public class AppProperties {

    private String scanRoot = System.getProperty("user.home") + "/IdeaProjects";
    private OsType os = OsType.MAC;
    private int maxScanDepth = 5;

    public String getScanRoot() { return scanRoot; }
    public void setScanRoot(String scanRoot) { this.scanRoot = scanRoot; }

    public OsType getOs() { return os; }
    public void setOs(OsType os) { this.os = os; }

    public int getMaxScanDepth() { return maxScanDepth; }
    public void setMaxScanDepth(int maxScanDepth) { this.maxScanDepth = maxScanDepth; }
}
