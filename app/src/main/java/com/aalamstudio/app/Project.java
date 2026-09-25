package com.aalamstudio.app;

public class Project {
    public String name;
    public String packageName;
    public String type;
    public String detail;
    public String language;
    public String platform;
    public long timestamp;

    public Project(String name, String packageName, String type, String detail, String language, String platform, long timestamp) {
        this.name = name;
        this.packageName = packageName;
        this.type = type;
        this.detail = detail;
        this.language = language;
        this.platform = platform;
        this.timestamp = timestamp;
    }
}
