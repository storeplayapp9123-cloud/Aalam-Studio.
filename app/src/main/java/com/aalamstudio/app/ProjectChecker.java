package com.aalamstudio.app;

import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the whole project (every file) when the user taps Run.
 * errors   -> Run fails (preview stays locked)
 * warnings -> shown in the build log, Run still succeeds
 */
public class ProjectChecker {

    public static class Result {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public int fileCount;
    }

    private static final Set<String> CLIKE = new HashSet<>(Arrays.asList(
            "java", "kt", "c", "cpp", "cc", "h", "hpp", "cs"));
    private static final Set<String> READ = new HashSet<>(Arrays.asList(
            "java", "kt", "c", "cpp", "cc", "h", "hpp", "cs", "xml", "json"));

    // ================= entry points =================

    /** Checks every file of the project. */
    public static Result check(File root, String projectType) {
        Result r = new Result();
        List<File> files = new ArrayList<>();
        collect(root, files);
        r.fileCount = files.size();

        Map<File, String> texts = new LinkedHashMap<>();
        Map<File, String> cleaned = new LinkedHashMap<>();

        for (File f : files) {
            String ext = ext(f.getName());
            if (!READ.contains(ext) || f.length() > 1024 * 1024) continue;
            String content;
            try {
                content = read(f);
            } catch (IOException e) {
                r.errors.add(f.getName() + ": file padh nahi payi");
                continue;
            }
            texts.put(f, content);
            String c = checkText(f.getName(), content, r.errors, r.warnings);
            if (c != null) cleaned.put(f, c);
            if (ext.equals("java") && c != null) packageFolderWarning(f, c, r.warnings);
        }

        boolean hasManifest = false;
        for (File f : files) if (f.getName().equals("AndroidManifest.xml")) hasManifest = true;
        if (!"Game".equals(projectType) && hasManifest) {
            androidChecks(files, texts, cleaned, r);
        }

        dedupe(r.errors);
        dedupe(r.warnings);
        return r;
    }

    /**
     * Checks one file's text. Returns the "cleaned" text (comments and strings blanked)
     * for C-like files, otherwise null.
     */
    public static String checkText(String name, String content, List<String> errors, List<String> warnings) {
        String ext = ext(name);
        String cleaned = null;

        if (CLIKE.contains(ext)) {
            cleaned = lex(name, content, errors);
            if (ext.equals("java") && !content.trim().isEmpty()
                    && !Pattern.compile("\\b(class|interface|enum|record)\\b").matcher(cleaned).find()) {
                errors.add(name + ": koi class / interface / enum nahi mili");
            }
            if (ext.equals("java") || ext.equals("c") || ext.equals("cpp")
                    || ext.equals("cc") || ext.equals("cs")) {
                missingSemicolons(name, cleaned, warnings);
            }
        } else if (ext.equals("xml")) {
            xmlCheck(name, content, errors);
        } else if (ext.equals("json")) {
            jsonCheck(name, content, errors);
        }
        return cleaned;
    }

    // ================= lexer (brackets, strings, comments) =================

    private static String lex(String name, String src, List<String> errors) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int line = 1;
        int i = 0;
        Deque<int[]> stack = new ArrayDeque<>();

        while (i < n) {
            char c = src.charAt(i);

            if (c == '\n') { line++; out.append('\n'); i++; continue; }

            // line comment
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') { out.append(' '); i++; }
                continue;
            }

            // block comment
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int startLine = line;
                out.append("  ");
                i += 2;
                boolean closed = false;
                while (i < n) {
                    if (src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/') {
                        out.append("  ");
                        i += 2;
                        closed = true;
                        break;
                    }
                    if (src.charAt(i) == '\n') { line++; out.append('\n'); } else out.append(' ');
                    i++;
                }
                if (!closed) errors.add(name + ": line " + startLine + " par /* comment band nahi hua");
                continue;
            }

            // text block """ ... """
            if (c == '"' && src.startsWith("\"\"\"", i)) {
                int startLine = line;
                out.append("   ");
                i += 3;
                boolean closed = false;
                while (i < n) {
                    if (src.startsWith("\"\"\"", i) && src.charAt(i - 1) != '\\') {
                        out.append("   ");
                        i += 3;
                        closed = true;
                        break;
                    }
                    if (src.charAt(i) == '\n') { line++; out.append('\n'); } else out.append(' ');
                    i++;
                }
                if (!closed) errors.add(name + ": line " + startLine + " par \"\"\" text block band nahi hua");
                continue;
            }

            // string / char literal
            if (c == '"' || c == '\'') {
                char q = c;
                int startLine = line;
                out.append(' ');
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = src.charAt(i);
                    if (d == '\\' && i + 1 < n && src.charAt(i + 1) != '\n') {
                        out.append("  ");
                        i += 2;
                        continue;
                    }
                    if (d == q) { out.append(' '); i++; closed = true; break; }
                    if (d == '\n') break;
                    out.append(' ');
                    i++;
                }
                if (!closed) {
                    errors.add(name + ": line " + startLine + " par "
                            + (q == '"' ? "string (\")" : "character (')") + " band nahi hua");
                }
                continue;
            }

            if (c == '{' || c == '(' || c == '[') {
                stack.push(new int[]{c, line});
            } else if (c == '}' || c == ')' || c == ']') {
                char open = c == '}' ? '{' : (c == ')' ? '(' : '[');
                if (stack.isEmpty()) {
                    errors.add(name + ": line " + line + " par extra '" + c + "' hai");
                } else {
                    int[] top = stack.pop();
                    if (top[0] != open) {
                        errors.add(name + ": line " + line + " ka '" + c + "' line " + top[1]
                                + " ke '" + (char) top[0] + "' se match nahi karta");
                    }
                }
            }
            out.append(c);
            i++;
        }

        while (!stack.isEmpty()) {
            int[] t = stack.pop();
            errors.add(name + ": line " + t[1] + " par '" + (char) t[0] + "' band nahi hua");
        }
        return out.toString();
    }

    // ================= ';' heuristic (warnings only) =================

    private static void missingSemicolons(String name, String cleaned, List<String> warnings) {
        String[] lines = cleaned.split("\n", -1);
        Pattern decl = Pattern.compile(
                "^(?:(?:final|static|private|public|protected)\\s+)*[\\w<>\\[\\],.?]+\\s+\\w+\\s*=\\s*[^;{}]*[\\w)\\]]$");
        Pattern jump = Pattern.compile("^(?:return|throw)\\b[^;{}]*[\\w)\\]]$|^(?:break|continue)$");
        int paren = 0;
        int found = 0;

        for (int i = 0; i < lines.length && found < 5; i++) {
            String t = lines[i].trim();
            int startParen = paren;
            for (int k = 0; k < t.length(); k++) {
                char ch = t.charAt(k);
                if (ch == '(') paren++;
                else if (ch == ')') paren--;
            }
            if (t.isEmpty() || startParen != 0 || paren != 0) continue;
            if (!decl.matcher(t).matches() && !jump.matcher(t).matches()) continue;

            // statement may continue on the next line (.foo(), + x, ? a : b ...)
            String next = "";
            for (int j = i + 1; j < lines.length; j++) {
                String nt = lines[j].trim();
                if (!nt.isEmpty()) { next = nt; break; }
            }
            if (!next.isEmpty() && ".+-*/?:&|),".indexOf(next.charAt(0)) >= 0) continue;

            warnings.add(name + ": line " + (i + 1) + " par ';' chhoot gaya shayad -> \"" + trunc(t) + "\"");
            found++;
        }
    }

    // ================= XML / JSON =================

    private static void xmlCheck(String name, String content, List<String> errors) {
        if (content.trim().isEmpty()) {
            errors.add(name + ": file khali hai");
            return;
        }
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(content));
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) ev = p.next();
        } catch (XmlPullParserException e) {
            String m = e.getMessage() == null ? "XML galat hai" : e.getMessage();
            int k = m.indexOf(" (position");
            if (k > 0) m = m.substring(0, k);
            errors.add(name + ": line " + e.getLineNumber() + " - " + m);
        } catch (IOException e) {
            errors.add(name + ": XML nahi padh payi");
        }
    }

    private static void jsonCheck(String name, String content, List<String> errors) {
        String t = content.trim();
        if (t.isEmpty()) {
            errors.add(name + ": file khali hai");
            return;
        }
        try {
            if (t.startsWith("{")) new JSONObject(t);
            else if (t.startsWith("[")) new JSONArray(t);
            else errors.add(name + ": JSON { ya [ se shuru hona chahiye");
        } catch (Exception e) {
            errors.add(name + ": JSON galat - " + e.getMessage());
        }
    }

    // ================= Android project checks =================

    private static void packageFolderWarning(File f, String cleaned, List<String> warnings) {
        String path = f.getParent() == null ? "" : f.getParent().replace('\\', '/');
        int i = path.indexOf("/java/");
        if (i < 0) return;
        String expected = path.substring(i + 6).replace('/', '.');
        Matcher m = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;").matcher(cleaned);
        if (m.find() && !m.group(1).equals(expected)) {
            warnings.add(f.getName() + ": package '" + m.group(1) + "' hai par folder '" + expected + "' hai");
        }
    }

    private static void androidChecks(List<File> files, Map<File, String> texts,
                                      Map<File, String> cleaned, Result r) {
        Set<String> layouts = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Set<String> strings = new HashSet<>();
        Set<String> colors = new HashSet<>();
        Set<String> drawables = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        File manifest = null;

        Pattern idP = Pattern.compile("@\\+id/([A-Za-z0-9_]+)");
        Pattern strP = Pattern.compile("<string\\s+name\\s*=\\s*\"([^\"]+)\"");
        Pattern colP = Pattern.compile("<color\\s+name\\s*=\\s*\"([^\"]+)\"");
        Pattern drwP = Pattern.compile("<drawable\\s+name\\s*=\\s*\"([^\"]+)\"");

        for (File f : files) {
            String n = f.getName();
            String base = n.contains(".") ? n.substring(0, n.lastIndexOf('.')) : n;
            String dir = f.getParentFile() == null ? "" : f.getParentFile().getName();

            if (n.equals("AndroidManifest.xml") && manifest == null) manifest = f;
            if (n.endsWith(".java") || n.endsWith(".kt")) classNames.add(base);
            if (dir.startsWith("layout") && n.endsWith(".xml")) layouts.add(base);
            if (dir.startsWith("drawable") || dir.startsWith("mipmap")) drawables.add(base);

            String t = texts.get(f);
            if (t != null && n.endsWith(".xml")) {
                Matcher m = idP.matcher(t);
                while (m.find()) ids.add(m.group(1));
                if (dir.startsWith("values")) {
                    m = strP.matcher(t);
                    while (m.find()) strings.add(m.group(1).replace('.', '_'));
                    m = colP.matcher(t);
                    while (m.find()) colors.add(m.group(1).replace('.', '_'));
                    m = drwP.matcher(t);
                    while (m.find()) drawables.add(m.group(1).replace('.', '_'));
                }
            }
        }

        // --- manifest: components + launcher ---
        String mx = manifest == null ? null : texts.get(manifest);
        if (mx != null) {
            Matcher cm = Pattern.compile("<(activity|service|receiver)\\b([^>]*)>").matcher(mx);
            while (cm.find()) {
                Matcher nm = Pattern.compile("android:name\\s*=\\s*\"([^\"]+)\"").matcher(cm.group(2));
                if (!nm.find()) continue;
                String full = nm.group(1);
                boolean relative = full.startsWith(".") || !full.contains(".");
                if (!relative) continue;
                String simple = full.substring(full.lastIndexOf('.') + 1);
                if (!classNames.contains(simple)) {
                    r.errors.add("AndroidManifest.xml: <" + cm.group(1) + "> '" + full
                            + "' hai par " + simple + ".java nahi mili");
                }
            }
            boolean hasLauncher = false;
            Matcher am = Pattern.compile("<activity\\b[^>]*>(.*?)</activity>", Pattern.DOTALL).matcher(mx);
            while (am.find()) {
                if (am.group(1).contains("android.intent.category.LAUNCHER")) hasLauncher = true;
            }
            if (!hasLauncher) {
                r.errors.add("AndroidManifest.xml: koi LAUNCHER activity nahi hai (app shuru nahi hoga)");
            }
        }

        // --- R.xxx.yyy in Java / Kotlin ---
        Pattern rP = Pattern.compile("(?<![\\w.])R\\.(layout|id|string|drawable|color|mipmap)\\.([A-Za-z0-9_]+)");
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".java") && !n.endsWith(".kt")) continue;
            String c = cleaned.get(f);
            if (c == null) continue;
            Matcher m = rP.matcher(c);
            while (m.find()) {
                String type = m.group(1);
                String res = m.group(2);
                Set<String> set = type.equals("layout") ? layouts
                        : type.equals("id") ? ids
                        : type.equals("string") ? strings
                        : type.equals("color") ? colors
                        : drawables;
                if (!set.contains(res)) {
                    r.errors.add(n + ": R." + type + "." + res + " nahi mili" + hint(type, res));
                }
            }
        }

        // --- @type/name in XML files ---
        Pattern xP = Pattern.compile("@(string|color|drawable|mipmap|layout|id)/([A-Za-z0-9_.]+)");
        for (File f : files) {
            String n = f.getName();
            String t = texts.get(f);
            if (t == null || !n.endsWith(".xml")) continue;
            Matcher m = xP.matcher(t);
            while (m.find()) {
                String type = m.group(1);
                String res = m.group(2).replace('.', '_');
                Set<String> set = type.equals("layout") ? layouts
                        : type.equals("id") ? ids
                        : type.equals("string") ? strings
                        : type.equals("color") ? colors
                        : drawables;
                if (!set.contains(res)) {
                    r.errors.add(n + ": @" + type + "/" + res + " nahi mili" + hint(type, res));
                }
            }
        }
    }

    private static String hint(String type, String res) {
        switch (type) {
            case "layout": return " (res/layout me " + res + ".xml banao)";
            case "string": return " (res/values/strings.xml me add karo)";
            case "color": return " (res/values/colors.xml me add karo)";
            case "id": return " (kisi layout me android:id=\"@+id/" + res + "\" lagao)";
            default: return " (res/" + type + " me file daalo)";
        }
    }

    // ================= helpers =================

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File f : kids) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) collect(f, out);
            } else {
                out.add(f);
            }
        }
    }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private static String read(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        }
    }

    private static String trunc(String s) {
        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }

    private static void dedupe(List<String> list) {
        Set<String> seen = new LinkedHashSet<>(list);
        list.clear();
        list.addAll(seen);
    }
}
