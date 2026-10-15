package com.aalamstudio.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProjectEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PROJECT_TYPE = "project_type";
    public static final String EXTRA_PROJECT_LANGUAGE = "project_language";
    public static final String EXTRA_PROJECT_PLATFORM = "project_platform";
    public static final String EXTRA_PROJECT_PACKAGE = "project_package";

    private static final String COMPILER_PACKAGE = "com.aalamstudio.compiler";

    private static final Set<String> TEXT_EXT = new HashSet<>(Arrays.asList(
            "java", "kt", "xml", "json", "txt", "md", "c", "cpp", "cc", "h", "hpp", "cs",
            "swift", "m", "lua", "js", "py", "go", "dart", "hlsl", "glsl", "gradle",
            "properties", "pro", "html", "css", "yml", "yaml", "sh", "csv"));

    private String projectName = "My Project";
    private String projectType = "App";
    private String projectLanguage = "Java";
    private String projectPlatform = "Android";
    private String projectPackage = "com.example.app";

    private EditText codeEditorText;
    private TextView openFileTabLabel;

    // real project folder on disk + currently open file
    private File projectRoot;
    private File currentFile = null;
    private Set<String> expandedDirs = new HashSet<>();
    private java.util.Map<String, Boolean> vState = new java.util.HashMap<>(); // Android-view folder open/closed
    private java.util.Map<String, TextView> fileItemViews = new java.util.HashMap<>();
    private TextView selectedFileItem;

    private SharedPreferences codePrefs; // only used to migrate old saved code

    private LinearLayout fileTreePanel;
    private ScrollView fileTreeScroll;
    private TextView toggleFileTreePanel;
    private boolean fileTreeExpanded = true;

    private LivePreviewPanel preview; // null if layout has no livePreviewContainer
    private FileContextMenu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_editor);

        String n = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        if (n != null) projectName = n;

        String t = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        if (t != null) projectType = t;

        String lang = getIntent().getStringExtra(EXTRA_PROJECT_LANGUAGE);
        if (lang != null) projectLanguage = lang;

        String plat = getIntent().getStringExtra(EXTRA_PROJECT_PLATFORM);
        if (plat != null) projectPlatform = plat;

        String pkg = getIntent().getStringExtra(EXTRA_PROJECT_PACKAGE);
        if (pkg != null) projectPackage = pkg;

        codePrefs = getSharedPreferences("aalam_code_" + projectName, MODE_PRIVATE);

        TextView editorProjectName = findViewById(R.id.editorProjectName);
        editorProjectName.setText(projectName);

        codeEditorText = findViewById(R.id.codeEditorText);
        openFileTabLabel = findViewById(R.id.openFileTabLabel);

        TextView btnCloseEditor = findViewById(R.id.btnCloseEditor);
        btnCloseEditor.setOnClickListener(v -> finish());

        TextView btnSaveFile = findViewById(R.id.btnSaveFile);
        btnSaveFile.setOnClickListener(v -> {
            saveCurrentFile();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        });

        TextView btnCheckErrors = findViewById(R.id.btnCheckErrors);
        btnCheckErrors.setOnClickListener(v -> {
            saveCurrentFile();
            checkErrorsInCurrentFile();
        });

        Button btnRun = findViewById(R.id.btnRun);
        Button btnBuildApk = findViewById(R.id.btnBuildApk);
        TextView buildLogText = findViewById(R.id.buildLogText);

        LinearLayout buildLogPanel = findViewById(R.id.buildLogPanel);
        TextView btnToggleBuildPanel = findViewById(R.id.btnToggleBuildPanel);

        btnToggleBuildPanel.setOnClickListener(v -> {
            boolean isVisible = buildLogPanel.getVisibility() == View.VISIBLE;
            buildLogPanel.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            btnToggleBuildPanel.setText(isVisible ? "⌄" : "⌃");
        });

        btnRun.setOnClickListener(v ->
                Toast.makeText(this, "Run - coming soon", Toast.LENGTH_SHORT).show());

        btnBuildApk.setOnClickListener(v -> {
            saveCurrentFile();
            buildLogText.setText("Packaging project into .asc file...");
            exportAsc(buildLogText);
        });

        fileTreePanel = findViewById(R.id.fileTreePanel);
        fileTreeScroll = findViewById(R.id.fileTreeScroll);
        toggleFileTreePanel = findViewById(R.id.toggleFileTreePanel);

        toggleFileTreePanel.setOnClickListener(v -> {
            fileTreeExpanded = !fileTreeExpanded;
            fileTreeScroll.setVisibility(fileTreeExpanded ? View.VISIBLE : View.GONE);
            toggleFileTreePanel.setText(fileTreeExpanded ? "▾" : "▸");

            ViewGroup.LayoutParams params = fileTreePanel.getLayoutParams();
            params.width = dp(fileTreeExpanded ? 150 : 40);
            fileTreePanel.setLayoutParams(params);
        });

        setupTabs();
        setupProjectFolder();
        setupLivePreview();
        setupContextMenu();
        setupEditorWatcher();

        File first = findDefaultFile(projectRoot);
        if (first != null) {
            expandAncestors(first);
        }
        refreshFileTree();
        if (first != null) {
            openFile(first);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentFile();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (menu != null && menu.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ===== Project folder (real files on disk) =====

    private void setupProjectFolder() {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        projectRoot = new File(base, "projects/" + projectName.replaceAll("[^a-zA-Z0-9._-]+", "_"));

        if (!projectRoot.exists()) {
            projectRoot.mkdirs();
            seedProject(); // first time: create default files (old saved code is migrated)
        } else {
            migrateAndroidLayout(); // old flat projects -> app/src/main/... structure
            ensureAndroidDirs();
        }
    }

    /** res/drawable and res/values (with strings.xml) like a normal Android project. */
    private void ensureAndroidDirs() {
        File res = new File(projectRoot, "Android/app/src/main/res");
        if (!res.isDirectory()) return;
        new File(res, "drawable").mkdirs();
        File values = new File(res, "values");
        if (!values.exists()) {
            values.mkdirs();
            String safe = projectName.replace("&", "&amp;").replace("<", "&lt;");
            try {
                writeText(new File(values, "strings.xml"),
                        "<resources>\n    <string name=\"app_name\">" + safe + "</string>\n</resources>\n");
            } catch (IOException ignored) { }
        }
    }

    private String pkgPath() {
        return projectPackage.replace('.', '/');
    }

    /** Moves old flat Android files into the Android Studio style folders. */
    private void migrateAndroidLayout() {
        File a = new File(projectRoot, "Android");
        if (!a.isDirectory() || new File(a, "app").exists()) return;
        File main = new File(a, "app/src/main");
        File[] kids = a.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (!f.isFile()) continue;
            String n = f.getName();
            File destDir;
            if (n.equals("AndroidManifest.xml")) destDir = main;
            else if (n.endsWith(".java") || n.endsWith(".kt")) destDir = new File(main, "java/" + pkgPath());
            else if (n.endsWith(".xml")) destDir = new File(main, "res/layout");
            else continue;
            destDir.mkdirs();
            f.renameTo(new File(destDir, n));
        }
    }

    private void seedProject() {
        List<String> platforms = splitList(projectPlatform);
        if (platforms.isEmpty()) platforms.add("Android");

        List<String> chosenLanguages = splitList(projectLanguage);

        for (String platform : platforms) {
            List<String> languages = intersectLanguages(platform, chosenLanguages);
            if (languages.isEmpty()) {
                languages = new ArrayList<>();
                languages.add(defaultLanguageFor(platform));
            }
            seedPlatform(platform, languages);
        }
    }

    private void seed(String platform, String relDir, String fileName, String content) {
        String text = codePrefs.getString(platform + "::" + fileName, content);
        File dir = relDir.isEmpty() ? new File(projectRoot, platform) : new File(new File(projectRoot, platform), relDir);
        File f = new File(dir, fileName);
        try {
            dir.mkdirs();
            writeText(f, text);
        } catch (IOException e) {
            Toast.makeText(this, "File nahi bani: " + fileName, Toast.LENGTH_SHORT).show();
        }
    }

    private void seedPlatform(String platform, List<String> languages) {
        boolean isAndroid = platform.equals("Android");

        if (projectType.equals("Game")) {
            seed(platform, "", "GameManifest.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<game-manifest>\n" +
                    "    <name>" + projectName + "</name>\n" +
                    "    <platform>" + platform + "</platform>\n" +
                    "    <engine>Aalam Game Engine</engine>\n" +
                    "</game-manifest>");

            for (String lang : languages) {
                seed(platform, "", "GameMain." + getExtension(lang), buildGameMainContent(lang));
            }

            seed(platform, "", "game_config.json",
                    "{\n" +
                    "  \"gameName\": \"" + projectName + "\",\n" +
                    "  \"engine\": \"Aalam Game Engine\",\n" +
                    "  \"platform\": \"" + platform + "\",\n" +
                    "  \"language\": \"" + String.join(" + ", languages) + "\"\n" +
                    "}");
        } else if (isAndroid) {
            String main = "app/src/main";
            seed(platform, main, "AndroidManifest.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n" +
                    "    <application android:label=\"@string/app_name\">\n" +
                    "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
                    "            <intent-filter>\n" +
                    "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                    "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                    "            </intent-filter>\n" +
                    "        </activity>\n" +
                    "    </application>\n" +
                    "</manifest>");

            for (String lang : languages) {
                seed(platform, main + "/java/" + pkgPath(), "MainActivity." + getExtension(lang),
                        buildAppMainContent(lang, true));
            }

            seed(platform, main + "/res/layout", "activity_main.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    android:layout_width=\"match_parent\"\n" +
                    "    android:layout_height=\"match_parent\"\n" +
                    "    android:gravity=\"center\">\n\n" +
                    "    <TextView\n" +
                    "        android:layout_width=\"wrap_content\"\n" +
                    "        android:layout_height=\"wrap_content\"\n" +
                    "        android:text=\"Hello World!\" />\n\n" +
                    "</LinearLayout>");
            ensureAndroidDirs();
        } else {
            seed(platform, "", "app_config.json",
                    "{\n" +
                    "  \"appName\": \"" + projectName + "\",\n" +
                    "  \"platform\": \"" + platform + "\",\n" +
                    "  \"language\": \"" + String.join(" + ", languages) + "\"\n" +
                    "}");

            for (String lang : languages) {
                seed(platform, "", "Main." + getExtension(lang), buildAppMainContent(lang, false));
            }
        }
    }

    private File findDefaultFile(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        Arrays.sort(kids);
        File firstText = null;
        for (File f : kids) {
            if (f.isDirectory()) {
                File r = findDefaultFile(f);
                if (r != null && isMainFile(r)) return r;
                if (firstText == null) firstText = r;
            } else if (isMainFile(f)) {
                return f;
            } else if (firstText == null && isTextFile(f)) {
                firstText = f;
            }
        }
        return firstText;
    }

    private boolean isMainFile(File f) {
        String n = f.getName();
        return n.startsWith("MainActivity.") || n.startsWith("GameMain.") || n.startsWith("Main.");
    }

    private void expandAncestors(File f) {
        File p = f.getParentFile();
        while (p != null && !p.equals(projectRoot)) {
            expandedDirs.add(p.getAbsolutePath());
            p = p.getParentFile();
        }
    }

    // ===== Live preview =====

    private TextView findTextView(View v, String text) {
        if (v instanceof TextView && text.equals(String.valueOf(((TextView) v).getText()).trim())) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findTextView(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void setupLivePreview() {
        ViewGroup holder = null;

        // 1) explicit container in the layout XML (if you added one)
        int holderId = getResources().getIdentifier("livePreviewContainer", "id", getPackageName());
        if (holderId != 0) holder = (ViewGroup) findViewById(holderId);

        // 2) otherwise take over the existing "Live Preview" column
        if (holder == null) {
            TextView title = findTextView(getWindow().getDecorView(), "Live Preview");
            if (title != null && title.getParent() instanceof ViewGroup) {
                ViewGroup p = (ViewGroup) title.getParent();
                boolean safe = p.findViewById(R.id.codeEditorText) == null
                        && p.findViewById(R.id.fileTreePanel) == null
                        && p.findViewById(R.id.buildLogPanel) == null;
                if (safe) holder = p;
            }
        }

        if (holder == null) {
            Toast.makeText(this, "Live Preview ki jagah nahi mili", Toast.LENGTH_LONG).show();
            return;
        }

        preview = new LivePreviewPanel(this);
        holder.removeAllViews();
        if (holder.getParent() instanceof ScrollView) {
            holder.addView(preview, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            ScrollView sv = new ScrollView(this);
            sv.addView(preview);
            holder.addView(sv, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        preview.setAppTitle(projectName);
        preview.setListener(line -> goToLine(line));
    }

    private void setupEditorWatcher() {
        codeEditorText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (preview != null && currentFile != null && isLayoutXml(currentFile)) {
                    preview.setXml(e.toString());
                }
            }
        });
    }

    private boolean isLayoutXml(File f) {
        String n = f.getName();
        return !projectType.equals("Game")
                && n.endsWith(".xml")
                && !n.equals("AndroidManifest.xml")
                && !n.equals("GameManifest.xml");
    }

    private void updatePreviewFor(File f, String content) {
        if (preview == null) return;
        if (projectType.equals("Game")) {
            preview.showMessage("Game preview Aalam Game Engine ke saath aayega");
            return;
        }
        if (isLayoutXml(f)) {
            preview.setXml(content);
            return;
        }
        // Java/Kotlin Activity: show the layout it loads with setContentView(R.layout.xxx)
        String n = f.getName();
        if (n.endsWith(".java") || n.endsWith(".kt")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("R\\.layout\\.([A-Za-z0-9_]+)").matcher(content);
            if (m.find()) {
                File layout = findFileByName(projectRoot, m.group(1) + ".xml");
                if (layout != null) {
                    try {
                        preview.setXml(readText(layout));
                        return;
                    } catch (IOException ignored) { }
                }
            }
        }
        preview.showMessage("Is file ka preview nahi hai. Layout XML kholo.");
    }

    private File findFileByName(File dir, String name) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isFile() && f.getName().equals(name)) return f;
            if (f.isDirectory()) {
                File r = findFileByName(f, name);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void goToLine(int line) {
        String text = codeEditorText.getText().toString();
        int off = 0;
        int cur = 1;
        while (cur < line && off < text.length()) {
            int nl = text.indexOf('\n', off);
            if (nl < 0) { off = text.length(); break; }
            off = nl + 1;
            cur++;
        }
        codeEditorText.requestFocus();
        codeEditorText.setSelection(Math.min(off, text.length()));
    }

    // ===== Long-press menu =====

    private void setupContextMenu() {
        menu = new FileContextMenu(this, new FileContextMenu.Host() {
            @Override public void onOpenFile(File f) {
                openFile(f);
            }

            @Override public void onRenamed(File oldFile, File newFile) {
                String op = oldFile.getAbsolutePath();
                String np = newFile.getAbsolutePath();
                if (currentFile != null) {
                    String cp = currentFile.getAbsolutePath();
                    if (cp.equals(op) || cp.startsWith(op + "/")) {
                        currentFile = new File(np + cp.substring(op.length()));
                        openFileTabLabel.setText(relativePath(currentFile));
                    }
                }
                Set<String> remapped = new HashSet<>();
                for (String p : expandedDirs) {
                    remapped.add(p.equals(op) || p.startsWith(op + "/")
                            ? np + p.substring(op.length()) : p);
                }
                expandedDirs = remapped;
            }

            @Override public void onDeleted(File f) {
                if (currentFile == null) return;
                String dp = f.getAbsolutePath();
                String cp = currentFile.getAbsolutePath();
                if (cp.equals(dp) || cp.startsWith(dp + "/")) {
                    currentFile = null;
                    codeEditorText.setText("");
                    openFileTabLabel.setText("No file open");
                    if (preview != null) preview.showMessage("Koi file open nahi hai");
                }
            }

            @Override public void onTreeChanged() {
                refreshFileTree();
            }
        });
        menu.setProject(projectRoot, projectPackage);
    }

    private void openMenu(File f) {
        saveCurrentFile();
        menu.show(f);
    }

    // ===== File tree (built from real folders) =====

    private void refreshFileTree() {
        LinearLayout container = findViewById(R.id.fileTreeContainer);
        container.removeAllViews();
        fileItemViews.clear();
        selectedFileItem = null;

        TextView rootRow = new TextView(this);
        rootRow.setText("📁 " + projectName);
        rootRow.setTextColor(0xFFD4AF37);
        rootRow.setTextSize(11);
        rootRow.setTypeface(null, Typeface.BOLD);
        rootRow.setPadding(dp(2), dp(6), dp(2), dp(6));
        rootRow.setOnClickListener(v -> openMenu(projectRoot));
        rootRow.setOnLongClickListener(v -> {
            openMenu(projectRoot);
            return true;
        });
        container.addView(rootRow);

        addProjectContents(container);
        highlightCurrent();
    }

    private void addDirContents(LinearLayout parent, File dir, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        for (File f : kids) {
            if (f.isDirectory()) addDirRow(parent, f, depth);
            else addFileRow(parent, f, depth);
        }
    }

    private void addDirRow(LinearLayout parent, File start, int depth) {
        // compact single-child folder chains: app/src/main, java/hg/uff ...
        File shown = start;
        String label = start.getName();
        while (true) {
            File[] k = shown.listFiles();
            if (k != null && k.length == 1 && k[0].isDirectory()) {
                shown = k[0];
                label += "/" + shown.getName();
            } else {
                break;
            }
        }
        final File dir = shown;
        final boolean open = expandedDirs.contains(dir.getAbsolutePath());
        TextView row = makeRow((open ? "▾ " : "▸ ") + "📁 " + label, depth);
        if (depth == 1) row.setTextColor(0xFFD4AF37);
        row.setOnClickListener(v -> {
            if (open) expandedDirs.remove(dir.getAbsolutePath());
            else expandedDirs.add(dir.getAbsolutePath());
            refreshFileTree();
        });
        row.setOnLongClickListener(v -> {
            openMenu(dir);
            return true;
        });
        parent.addView(row);
        if (open) addDirContents(parent, dir, depth + 1);
    }

    private void addFileRow(LinearLayout parent, File file, int depth) {
        addFileRow(parent, file, depth, false);
    }

    private void addFileRow(LinearLayout parent, File file, int depth, boolean stripExt) {
        String n = file.getName();
        String text;
        if (n.equals("AndroidManifest.xml")) {
            text = "📋 " + n;
        } else if (n.endsWith(".java") || n.endsWith(".kt")) {
            text = "🔵 " + (stripExt ? n.substring(0, n.lastIndexOf('.')) : n);
        } else if (n.endsWith(".xml") && file.getAbsolutePath().contains("/res/")) {
            text = "🔶 " + n;
        } else {
            text = "📄 " + n;
        }
        addFileRowText(parent, file, depth, text);
    }

    private void addFileRowText(LinearLayout parent, File file, int depth, String text) {
        TextView row = makeRow(text, depth);
        row.setOnClickListener(v -> openFile(file));
        row.setOnLongClickListener(v -> {
            openMenu(file);
            return true;
        });
        fileItemViews.put(file.getAbsolutePath(), row);
        parent.addView(row);
    }

    // ===== Android Studio style "Android" view =====

    private boolean vOpen(String key, boolean defaultOpen) {
        Boolean b = vState.get(key);
        return b != null ? b : defaultOpen;
    }

    /** Folder row for the virtual Android view. Returns true if it is open. */
    private boolean addVFolder(LinearLayout parent, String label, String key, int depth,
                               File target, boolean defaultOpen) {
        final boolean open = vOpen(key, defaultOpen);
        final String k = key;
        TextView row = makeRow((open ? "▾ " : "▸ ") + label, depth);
        row.setOnClickListener(v -> {
            vState.put(k, !open);
            refreshFileTree();
        });
        if (target != null && target.isDirectory()) {
            row.setOnLongClickListener(v -> {
                openMenu(target);
                return true;
            });
        }
        parent.addView(row);
        return open;
    }

    private boolean isAndroidModule(File platformDir) {
        return new File(platformDir, "app/src/main").isDirectory();
    }

    private void addProjectContents(LinearLayout parent) {
        File[] kids = projectRoot.listFiles();
        if (kids == null) return;
        Arrays.sort(kids, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        int dirCount = 0;
        for (File f : kids) if (f.isDirectory()) dirCount++;

        for (File f : kids) {
            if (f.isFile()) {
                addFileRow(parent, f, 1);
            } else if (isAndroidModule(f)) {
                if (dirCount > 1) {
                    if (addVFolder(parent, "📁 " + f.getName(), "v:plat:" + f.getAbsolutePath(), 1, f, true)) {
                        addAndroidView(parent, f, 2);
                    }
                } else {
                    addAndroidView(parent, f, 1);
                }
            } else {
                addDirRow(parent, f, 1);
            }
        }
    }

    private void addAndroidView(LinearLayout parent, File pdir, int d) {
        String id = pdir.getAbsolutePath();
        File app = new File(pdir, "app");
        File main = new File(app, "src/main");

        if (addVFolder(parent, "📁 app", "v:app:" + id, d, app, true)) {
            int d2 = d + 1;

            // manifests
            if (addVFolder(parent, "📁 manifests", "v:man:" + id, d2, main, true)) {
                File manifest = new File(main, "AndroidManifest.xml");
                if (manifest.exists()) addFileRow(parent, manifest, d2 + 1);
            }

            // java (+ test source sets)
            File javaRoot = new File(main, "java");
            if (addVFolder(parent, "📁 java", "v:java:" + id, d2, javaRoot, true)) {
                addPackages(parent, javaRoot, d2 + 1, "", true);
                addPackages(parent, new File(app, "src/androidTest/java"), d2 + 1, " (androidTest)", false);
                addPackages(parent, new File(app, "src/test/java"), d2 + 1, " (test)", false);
            }

            // res
            File res = new File(main, "res");
            if (addVFolder(parent, "📁 res", "v:res:" + id, d2, res, true)) {
                File[] rk = res.listFiles();
                if (rk != null) {
                    Arrays.sort(rk, (a, b) -> {
                        if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                        return a.getName().compareToIgnoreCase(b.getName());
                    });
                    for (File r : rk) {
                        if (r.isDirectory()) {
                            boolean open = addVFolder(parent, "📁 " + r.getName(),
                                    "v:resd:" + r.getAbsolutePath(), d2 + 1, r, r.getName().equals("layout"));
                            if (open) addDirContents(parent, r, d2 + 2);
                        } else {
                            addFileRow(parent, r, d2 + 1);
                        }
                    }
                }
            }
        }

        addGradleScripts(parent, pdir, d);
    }

    private void collectPackages(File root, File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        boolean hasFiles = false, hasDirs = false;
        for (File k : kids) {
            if (k.isDirectory()) hasDirs = true; else hasFiles = true;
        }
        if (!dir.equals(root) && (hasFiles || !hasDirs)) out.add(dir);
        for (File k : kids) {
            if (k.isDirectory()) collectPackages(root, k, out);
        }
    }

    private void addPackages(LinearLayout parent, File root, int depth, String suffix, boolean defaultOpen) {
        if (!root.isDirectory()) return;

        File[] direct = root.listFiles();
        if (direct != null) {
            Arrays.sort(direct);
            for (File f : direct) if (f.isFile()) addFileRow(parent, f, depth, true);
        }

        List<File> packages = new ArrayList<>();
        collectPackages(root, root, packages);
        for (File p : packages) {
            String name = p.getAbsolutePath().substring(root.getAbsolutePath().length() + 1).replace('/', '.');
            if (addVFolder(parent, "📦 " + name + suffix, "v:pkg:" + p.getAbsolutePath(), depth, p, defaultOpen)) {
                File[] fs = p.listFiles();
                if (fs == null) continue;
                Arrays.sort(fs);
                for (File f : fs) if (f.isFile()) addFileRow(parent, f, depth + 1, true);
            }
        }
    }

    private void addGradleScripts(LinearLayout parent, File pdir, int d) {
        String[][] items = {
                {"build.gradle", "Project: " + projectName},
                {"app/build.gradle", "Module: app"},
                {"gradle/wrapper/gradle-wrapper.properties", ""},
                {"app/proguard-rules.pro", "ProGuard Rules for \"app\""},
                {"gradle.properties", "Project Properties"},
                {"settings.gradle", "Project Settings"},
                {"local.properties", "SDK Location"}};
        List<String[]> found = new ArrayList<>();
        for (String[] it : items) {
            if (new File(pdir, it[0]).isFile()) found.add(it);
        }
        if (found.isEmpty()) return;
        if (addVFolder(parent, "🐘 Gradle Scripts", "v:gradle:" + pdir.getAbsolutePath(), d, null, true)) {
            for (String[] it : found) {
                File f = new File(pdir, it[0]);
                String label = "⚙ " + f.getName() + (it[1].isEmpty() ? "" : " (" + it[1] + ")");
                addFileRowText(parent, f, d + 1, label);
            }
        }
    }

    private TextView makeRow(String text, int depth) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(0xFFCCCCCC);
        item.setTextSize(10);
        item.setPadding(dp(2 + depth * 8), dp(5), dp(2), dp(5));
        return item;
    }

    private void highlightCurrent() {
        if (selectedFileItem != null) {
            selectedFileItem.setTextColor(0xFFCCCCCC);
            selectedFileItem.setBackgroundColor(0x00000000);
            selectedFileItem = null;
        }
        if (currentFile == null) return;
        TextView v = fileItemViews.get(currentFile.getAbsolutePath());
        if (v != null) {
            v.setTextColor(0xFFD4AF37);
            v.setBackgroundColor(0x22D4AF37);
            selectedFileItem = v;
        }
    }

    // ===== Open / save =====

    private boolean isTextFile(File f) {
        if (f.length() > 1024 * 1024) return false;
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        if (dot < 0) return true;
        return TEXT_EXT.contains(n.substring(dot + 1).toLowerCase());
    }

    private void openFile(File f) {
        if (f == null || !f.isFile()) return;
        if (!isTextFile(f)) {
            Toast.makeText(this, "Ye file type editor me nahi khulta", Toast.LENGTH_SHORT).show();
            return;
        }

        saveCurrentFile();

        String content;
        try {
            content = readText(f);
        } catch (IOException e) {
            Toast.makeText(this, "File nahi khuli: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        currentFile = f;
        codeEditorText.setText(content);
        openFileTabLabel.setText(relativePath(f));
        highlightCurrent();
        updatePreviewFor(f, content);
    }

    private void saveCurrentFile() {
        if (currentFile == null || codeEditorText == null || !currentFile.exists()) return;
        try {
            writeText(currentFile, codeEditorText.getText().toString());
        } catch (IOException e) {
            Toast.makeText(this, "Save fail: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String relativePath(File f) {
        String root = projectRoot.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (p.startsWith(root + "/")) p = p.substring(root.length() + 1);
        return p.replace("/", " / ");
    }

    // ===== Error checking =====

    private void checkErrorsInCurrentFile() {
        if (currentFile == null) return;

        LinearLayout buildLogPanel = findViewById(R.id.buildLogPanel);
        TextView buildLogText = findViewById(R.id.buildLogText);
        TextView btnToggleBuildPanel = findViewById(R.id.btnToggleBuildPanel);

        buildLogPanel.setVisibility(View.VISIBLE);
        btnToggleBuildPanel.setText("⌃");

        String content = codeEditorText.getText().toString();
        String fileName = currentFile.getName();

        List<String> errors = runBasicChecks(fileName, content);

        StringBuilder sb = new StringBuilder();
        sb.append("Checking ").append(fileName).append(" ...\n\n");

        if (errors.isEmpty()) {
            sb.append("✅ No issues found.");
        } else {
            sb.append("⚠ ").append(errors.size()).append(" issue(s) found:\n\n");
            for (String e : errors) {
                sb.append("• ").append(e).append("\n");
            }
        }

        buildLogText.setText(sb.toString());
    }

    private List<String> runBasicChecks(String fileName, String content) {
        List<String> errors = new ArrayList<>();
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";

        checkBracketBalance(content, errors, '{', '}');
        checkBracketBalance(content, errors, '(', ')');
        checkBracketBalance(content, errors, '[', ']');
        checkUnclosedQuotes(content, errors);

        switch (ext) {
            case "java":
                checkJavaBasics(content, errors);
                break;
            case "xml":
                checkXmlBasics(content, errors);
                break;
            case "json":
                checkJsonBasics(content, errors);
                break;
            case "kt":
                checkKotlinBasics(content, errors);
                break;
        }

        return errors;
    }

    private void checkBracketBalance(String content, List<String> errors, char open, char close) {
        int depth = 0;
        int line = 1;
        boolean inString = false;
        char stringChar = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') line++;

            if (inString) {
                if (c == stringChar && (i == 0 || content.charAt(i - 1) != '\\')) inString = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringChar = c;
                continue;
            }

            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth < 0) {
                    errors.add("Unexpected '" + close + "' near line " + line + " (no matching '" + open + "')");
                    depth = 0;
                }
            }
        }

        if (depth > 0) {
            errors.add("Missing " + depth + " closing '" + close + "' — check your " + open + " blocks");
        }
    }

    private void checkUnclosedQuotes(String content, List<String> errors) {
        boolean inDouble = false;
        boolean inSingle = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            boolean escaped = i > 0 && content.charAt(i - 1) == '\\';
            if (c == '"' && !inSingle && !escaped) inDouble = !inDouble;
            else if (c == '\'' && !inDouble && !escaped) inSingle = !inSingle;
        }

        if (inDouble) errors.add("Unclosed double-quote (\") somewhere in the file");
        if (inSingle) errors.add("Unclosed single-quote (') somewhere in the file");
    }

    private void checkJavaBasics(String content, List<String> errors) {
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
            if (line.startsWith("@") || line.startsWith("import ") || line.startsWith("package ")) continue;

            boolean endsOk = line.endsWith(";") || line.endsWith("{") || line.endsWith("}")
                    || line.endsWith(",") || line.endsWith("(") || line.endsWith(":");

            boolean looksLikeStatement = (line.contains("=") || line.matches(".*\\b(return|break|continue)\\b.*"))
                    && !line.contains("//");

            if (looksLikeStatement && !endsOk) {
                errors.add("Line " + (i + 1) + ": possibly missing ';' → \"" + truncate(line) + "\"");
            }
        }

        if (!content.contains("class ") && !content.contains("interface ") && !content.contains("enum ")) {
            errors.add("No class/interface/enum declaration found in this Java file");
        }
    }

    private void checkKotlinBasics(String content, List<String> errors) {
        if (!content.contains("class ") && !content.contains("fun ") && !content.contains("object ")) {
            errors.add("No class/fun/object declaration found in this Kotlin file");
        }
    }

    private void checkXmlBasics(String content, List<String> errors) {
        if (!content.trim().startsWith("<?xml") && !content.trim().startsWith("<")) {
            errors.add("File doesn't start with an XML declaration or tag");
        }

        int openTags = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("</?[a-zA-Z][^>]*?(/?)>").matcher(content);
        while (m.find()) {
            String tag = m.group();
            boolean selfClosing = tag.endsWith("/>");
            boolean closing = tag.startsWith("</");
            if (!selfClosing) {
                if (closing) openTags--;
                else openTags++;
            }
        }
        if (openTags != 0) {
            errors.add("XML tags don't look balanced (possible missing closing tag)");
        }
    }

    private void checkJsonBasics(String content, List<String> errors) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            errors.add("File is empty");
            return;
        }
        if (!(trimmed.startsWith("{") && trimmed.endsWith("}")) &&
            !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            errors.add("JSON should start/end with matching { } or [ ]");
        }
        try {
            if (trimmed.startsWith("{")) new JSONObject(trimmed);
            else if (trimmed.startsWith("[")) new JSONArray(trimmed);
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
        }
    }

    private String truncate(String s) {
        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }

    // ===== .asc export =====

    private void collectFiles(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File f : kids) {
            if (f.isDirectory()) collectFiles(f, out);
            else out.add(f);
        }
    }

    private String zipPath(File f) {
        String root = projectRoot.getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(root + "/") ? p.substring(root.length() + 1) : f.getName();
    }

    private void exportAsc(TextView buildLogText) {
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("name", projectName);
            manifest.put("package", projectPackage);
            manifest.put("type", projectType);
            manifest.put("language", projectLanguage);

            String platformLower = projectPlatform.toLowerCase().trim();
            manifest.put("platform", platformLower);

            JSONArray platformsArray = new JSONArray();
            for (String p : splitList(projectPlatform)) {
                platformsArray.put(p.toLowerCase().trim());
            }
            if (platformsArray.length() == 0) {
                platformsArray.put(platformLower);
            }
            manifest.put("platforms", platformsArray);

            manifest.put("createdBy", "Aalam Studio");

            List<File> files = new ArrayList<>();
            collectFiles(projectRoot, files);

            JSONArray filesArray = new JSONArray();
            for (File f : files) {
                filesArray.put(zipPath(f));
            }
            manifest.put("files", filesArray);

            File ascDir = new File(getExternalFilesDir(null), "asc");
            if (!ascDir.exists()) ascDir.mkdirs();

            String safePlatform = projectPlatform.replaceAll("[^a-zA-Z0-9]+", "-");
            String safeName = projectName.replaceAll("[^a-zA-Z0-9]+", "-");
            File ascFile = new File(ascDir, safeName + "-" + safePlatform + ".asc");

            FileOutputStream fos = new FileOutputStream(ascFile);
            ZipOutputStream zos = new ZipOutputStream(fos);

            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write(manifest.toString(2).getBytes("UTF-8"));
            zos.closeEntry();

            for (File f : files) {
                ZipEntry fileEntry = new ZipEntry("src/" + zipPath(f));
                zos.putNextEntry(fileEntry);
                zos.write(readBytes(f));
                zos.closeEntry();
            }

            zos.close();
            fos.close();

            buildLogText.setText("Saved: " + ascFile.getName() + "\nOpening Aalam Compiler...");
            openWithAalamCompiler(ascFile);

        } catch (Exception e) {
            buildLogText.setText("Export failed: " + e.getMessage());
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openWithAalamCompiler(File ascFile) {
        Uri ascUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", ascFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(ascUri, "application/octet-stream");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage(COMPILER_PACKAGE);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent chooser = new Intent(Intent.ACTION_VIEW);
            chooser.setDataAndType(ascUri, "application/octet-stream");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Toast.makeText(this, "Aalam Compiler not found — pick an app to open .asc", Toast.LENGTH_LONG).show();
            startActivity(Intent.createChooser(chooser, "Open with Aalam Compiler"));
        }
    }

    // ===== Content generators =====

    private String buildAppMainContent(String lang, boolean isAndroid) {
        if (isAndroid && lang.equals("Kotlin")) {
            return "package " + projectPackage + "\n\n" +
                    "import android.os.Bundle\n" +
                    "import androidx.appcompat.app.AppCompatActivity\n\n" +
                    "class MainActivity : AppCompatActivity() {\n" +
                    "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                    "        super.onCreate(savedInstanceState)\n" +
                    "        setContentView(R.layout.activity_main)\n" +
                    "    }\n" +
                    "}";
        }
        if (isAndroid) {
            return "package " + projectPackage + ";\n\n" +
                    "import android.os.Bundle;\n" +
                    "import androidx.appcompat.app.AppCompatActivity;\n\n" +
                    "public class MainActivity extends AppCompatActivity {\n" +
                    "    @Override\n" +
                    "    protected void onCreate(Bundle savedInstanceState) {\n" +
                    "        super.onCreate(savedInstanceState);\n" +
                    "        setContentView(R.layout.activity_main);\n" +
                    "    }\n" +
                    "}";
        }
        switch (lang) {
            case "Swift":
                return "import Foundation\n\nprint(\"Hello from " + projectName + "\")";
            case "Objective-C":
                return "#import <Foundation/Foundation.h>\n\nint main() {\n    NSLog(@\"Hello from " + projectName + "\");\n    return 0;\n}";
            case "C++":
                return "#include <iostream>\n\nint main() {\n    std::cout << \"Hello from " + projectName + "\" << std::endl;\n    return 0;\n}";
            case "C":
                return "#include <stdio.h>\n\nint main() {\n    printf(\"Hello from " + projectName + "\\n\");\n    return 0;\n}";
            case "C#":
                return "using System;\n\nclass Program {\n    static void Main() {\n        Console.WriteLine(\"Hello from " + projectName + "\");\n    }\n}";
            case "Python":
                return "print(\"Hello from " + projectName + "\")";
            case "Dart":
                return "void main() {\n  print('Hello from " + projectName + "');\n}";
            default:
                return "// " + lang + " entry point for " + projectName;
        }
    }

    private String buildGameMainContent(String lang) {
        switch (lang) {
            case "Kotlin":
                return "package com.example.game\n\n" +
                        "import com.aalam.engine.GameEngine\n\n" +
                        "class GameMain : GameEngine() {\n" +
                        "    override fun onGameStart() {\n" +
                        "        loadScene(\"MainScene\")\n" +
                        "    }\n" +
                        "}";
            case "C++":
                return "#include \"AalamEngine.h\"\n\n" +
                        "class GameMain : public GameEngine {\n" +
                        "public:\n" +
                        "    void onGameStart() override {\n" +
                        "        loadScene(\"MainScene\");\n" +
                        "    }\n" +
                        "};";
            case "C":
                return "#include \"aalam_engine.h\"\n\n" +
                        "void on_game_start() {\n" +
                        "    load_scene(\"MainScene\");\n" +
                        "}";
            case "C#":
                return "using Aalam.Engine;\n\n" +
                        "public class GameMain : GameEngine {\n" +
                        "    public override void OnGameStart() {\n" +
                        "        LoadScene(\"MainScene\");\n" +
                        "    }\n" +
                        "}";
            case "Swift":
                return "import AalamEngine\n\n" +
                        "class GameMain: GameEngine {\n" +
                        "    override func onGameStart() {\n" +
                        "        loadScene(\"MainScene\")\n" +
                        "    }\n" +
                        "}";
            case "Lua":
                return "-- Aalam Game Engine (Lua)\n\n" +
                        "function onGameStart()\n" +
                        "    loadScene(\"MainScene\")\n" +
                        "end";
            case "JavaScript":
                return "function onGameStart() {\n    loadScene(\"MainScene\");\n}";
            case "Python":
                return "def on_game_start():\n    load_scene(\"MainScene\")";
            case "Go":
                return "package main\n\nimport \"aalam/engine\"\n\n" +
                        "func OnGameStart() {\n    engine.LoadScene(\"MainScene\")\n}";
            case "Dart":
                return "void onGameStart() {\n  loadScene('MainScene');\n}";
            default:
                return "package com.example.game;\n\n" +
                        "import com.aalam.engine.GameEngine;\n\n" +
                        "public class GameMain extends GameEngine {\n" +
                        "    @Override\n" +
                        "    public void onGameStart() {\n" +
                        "        loadScene(\"MainScene\");\n" +
                        "    }\n" +
                        "}";
        }
    }

    // ===== Language / platform helpers =====

    private List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return result;
        String[] parts = value.split("\\+");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("Custom") && !trimmed.startsWith("none")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<String> intersectLanguages(String platform, List<String> chosen) {
        List<String> valid = projectType.equals("Game") ? getGameLanguagesFor(platform) : getAppLanguagesFor(platform);
        List<String> result = new ArrayList<>();

        for (String lang : chosen) {
            if (lang.equals("Dual") && platform.equals("Android")) {
                if (!result.contains("Java")) result.add("Java");
                if (!result.contains("Kotlin")) result.add("Kotlin");
            } else if (valid.contains(lang) && !result.contains(lang)) {
                result.add(lang);
            }
        }
        return result;
    }

    private String defaultLanguageFor(String platform) {
        return projectType.equals("Game") ? getGameLanguagesFor(platform).get(0) : getAppLanguagesFor(platform).get(0);
    }

    private List<String> getAppLanguagesFor(String platform) {
        switch (platform) {
            case "Windows": return Arrays.asList("C#", "C++", "Java", "Python");
            case "macOS": return Arrays.asList("Swift", "Objective-C", "C++");
            case "Linux": return Arrays.asList("C", "Python", "C++", "Java");
            case "iOS": return Arrays.asList("Swift", "Objective-C", "Dart");
            default: return Arrays.asList("Java", "Kotlin");
        }
    }

    private List<String> getGameLanguagesFor(String platform) {
        switch (platform) {
            case "Windows": return Arrays.asList("C++", "C#", "Python");
            case "macOS": return Arrays.asList("Swift", "C++", "C#");
            case "Linux": return Arrays.asList("C++", "C#", "Python");
            case "iOS": return Arrays.asList("Swift", "C#", "Dart");
            default: return Arrays.asList("C#", "C++", "C", "Java", "Kotlin", "Swift",
                    "Lua", "JavaScript", "Python", "Go", "HLSL / GLSL");
        }
    }

    private String getExtension(String lang) {
        switch (lang) {
            case "Java": return "java";
            case "Kotlin": return "kt";
            case "C#": return "cs";
            case "C++": return "cpp";
            case "C": return "c";
            case "Swift": return "swift";
            case "Objective-C": return "m";
            case "Lua": return "lua";
            case "JavaScript": return "js";
            case "Python": return "py";
            case "Go": return "go";
            case "Dart": return "dart";
            case "HLSL / GLSL": return "hlsl";
            default: return "txt";
        }
    }

    // ===== IO helpers =====

    private byte[] readBytes(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        }
    }

    private String readText(File f) throws IOException {
        return new String(readBytes(f), "UTF-8");
    }

    private void writeText(File f, String s) throws IOException {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(s.getBytes("UTF-8"));
        }
    }

    // ===== Tabs =====

    private void setupTabs() {
        TextView tabDesign = findViewById(R.id.tabDesign);
        TextView tabCode = findViewById(R.id.tabCode);
        TextView tabSplit = findViewById(R.id.tabSplit);
        TextView tabPreview = findViewById(R.id.tabPreview);

        TextView[] tabs = {tabDesign, tabCode, tabSplit, tabPreview};

        for (TextView tab : tabs) {
            tab.setOnClickListener(v -> {
                for (TextView t : tabs) {
                    t.setBackgroundResource(R.drawable.bg_type_card);
                    t.setTextColor(0xFFCCCCCC);
                }
                tab.setBackgroundColor(0xFFD4AF37);
                tab.setTextColor(0xFF000000);
                Toast.makeText(this, tab.getText() + " view - coming soon", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
