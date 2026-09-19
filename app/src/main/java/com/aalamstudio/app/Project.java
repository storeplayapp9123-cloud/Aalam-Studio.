package com.aalamstudio.app;

public class Project {
    public String name;
    public String packageName;
    public String type;      // "App" or "Game"
    public String detail;    // orientation (App) or 2D/3D (Game)
    public long timestamp;

    public Project(String name, String packageName, String type, String detail, long timestamp) {
        this.name = name;
        this.packageName = packageName;
        this.type = type;
        this.detail = detail;
        this.timestamp = timestamp;
    }
}
