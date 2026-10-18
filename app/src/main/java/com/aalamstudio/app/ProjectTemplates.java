package com.aalamstudio.app;

public class ProjectTemplates {

    // Sirf a-z, 0-9, _ aur . allowed. Space hataya jata hai.
    public static String sanitizePackage(String raw) {
        String s = raw.toLowerCase().replaceAll("[^a-z0-9_.]", "");
        s = s.replaceAll("\\.{2,}", ".").replaceAll("^\\.|\\.$", "");
        if (s.isEmpty()) s = "app";
        if (!s.contains(".")) s = "com.aalamstudio." + s;
        // har part letter se shuru ho
        StringBuilder out = new StringBuilder();
        for (String part : s.split("\\.")) {
            if (part.isEmpty()) continue;
            if (!Character.isLetter(part.charAt(0))) part = "a" + part;
            if (out.length() > 0) out.append('.');
            out.append(part);
        }
        return out.toString();
    }

    public static String androidManifest(String pkg) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
             + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
             + "    package=\"" + pkg + "\">\n\n"
             + "    <application\n"
             + "        android:label=\"@string/app_name\">\n"
             + "        <activity\n"
             + "            android:name=\".MainActivity\"\n"
             + "            android:exported=\"true\">\n"
             + "            <intent-filter>\n"
             + "                <action android:name=\"android.intent.action.MAIN\" />\n"
             + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
             + "            </intent-filter>\n"
             + "        </activity>\n"
             + "    </application>\n\n"
             + "</manifest>\n";
    }
}
