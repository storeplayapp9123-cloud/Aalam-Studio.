package com.aalamstudio.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProjectStore {

    private static final String PREFS_NAME = "aalam_projects";
    private static final String KEY_PROJECTS = "projects_json";

    public static void addProject(Context context, Project project) {
        List<Project> projects = getAllProjects(context);
        projects.add(project);
        saveAll(context, projects);
    }

    public static void deleteProject(Context context, long timestamp) {
        List<Project> projects = getAllProjects(context);
        List<Project> updated = new ArrayList<>();
        for (Project p : projects) {
            if (p.timestamp != timestamp) {
                updated.add(p);
            }
        }
        saveAll(context, updated);
    }

    public static List<Project> getAllProjects(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_PROJECTS, "[]");
        List<Project> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                result.add(new Project(
                        obj.getString("name"),
                        obj.getString("packageName"),
                        obj.getString("type"),
                        obj.getString("detail"),
                        obj.optString("language", "Java"),
                        obj.getLong("timestamp")
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static List<Project> getRecentProjects(Context context, int limit) {
        List<Project> all = getAllProjects(context);
        Collections.sort(all, (a, b) -> Long.compare(b.timestamp, a.timestamp));
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public static int countByType(Context context, String type) {
        int count = 0;
        for (Project p : getAllProjects(context)) {
            if (p.type.equals(type)) count++;
        }
        return count;
    }

    private static void saveAll(Context context, List<Project> projects) {
        JSONArray array = new JSONArray();
        try {
            for (Project p : projects) {
                JSONObject obj = new JSONObject();
                obj.put("name", p.name);
                obj.put("packageName", p.packageName);
                obj.put("type", p.type);
                obj.put("detail", p.detail);
                obj.put("language", p.language);
                obj.put("timestamp", p.timestamp);
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PROJECTS, array.toString()).apply();
    }
}
