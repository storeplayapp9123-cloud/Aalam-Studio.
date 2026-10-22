package com.aalamstudio.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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
    private String projectOrientation = "portrait";

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

    private boolean runOk = false;        // true only after a successful Run (project check)
    private boolean previewLaunched = false; // AppPreviewActivity is open
    private boolean loadingFile = false;  // true while openFile() sets the editor text
    private TextView tabCodeView, tabPreviewView;
    private TextView[] allTabs;

    private ViewGroup previewHolder;       // the right column that holds the preview
    private int holderWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
    private float holderWeight = 0f;

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
        projectOrientation = getSharedPreferences("aalam_project_meta", MODE_PRIVATE)
                .getString(projectName + "::orientation", "portrait");

        TextView editorProjectName = findViewById(R.id.editorProjectName);
        editorProjectName.setText(projectName);

        codeEditorText = findViewById(R.id.codeEditorText);
        codeEditorText = CodeEditText.replace(codeEditorText); // line numbers + colored code
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

        btnRun.setOnClickListener(v -> runProjectCheck());

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
    protected void onResume() {
        super.onResume();
        if (previewLaunched) {              // came back from the preview (Back button)
            previewLaunched = false;
            if (tabCodeView != null) selectTab(tabCodeView);
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
            migrateAndroidLayout();  // old flat projects -> app/src/main/... structure
            ensureAndroidDirs();     // res/values (strings + styles), res/drawable
            ensureManifestPackage(); // old manifests without package= get it added
            if (ensureLauncherIcons()) ensureManifestIcon(); // icons banne par manifest me icon line
            ensureGradleFiles();     // build.gradle, settings.gradle ... (agar nahi hain)
        }
    }

    /** res/drawable and res/values (strings.xml + styles.xml) like a normal Android project. */
    private void ensureAndroidDirs() {
        File res = new File(projectRoot, "Android/app/src/main/res");
        if (!res.isDirectory()) return;
        new File(res, "drawable").mkdirs();
        File values = new File(res, "values");
        values.mkdirs();

        File strings = new File(values, "strings.xml");
        if (!strings.exists()) {
            String safe = projectName.replace("&", "&amp;").replace("<", "&lt;");
            try {
                writeText(strings,
                        "<resources>\n    <string name=\"app_name\">" + safe + "</string>\n</resources>\n");
            } catch (IOException ignored) { }
        }

        File styles = new File(values, "styles.xml");
        if (!styles.exists()) {
            try {
                writeText(styles,
                        "<resources>\n" +
                        "    <style name=\"AppTheme\" parent=\"Theme.AppCompat.Light.DarkActionBar\">\n" +
                        "        <item name=\"colorPrimary\">#5E35B1</item>\n" +
                        "        <item name=\"colorPrimaryDark\">#4527A0</item>\n" +
                        "        <item name=\"colorAccent\">#D4AF37</item>\n" +
                        "    </style>\n" +
                        "</resources>\n");
            } catch (IOException ignored) { }
        }
    }

    /** Old projects: if <manifest> has no package= attribute, add it (aapt2 needs it). */
    private void ensureManifestPackage() {
        if (!projectPackage.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) return;
        File m = new File(projectRoot, "Android/app/src/main/AndroidManifest.xml");
        if (!m.isFile()) return;
        try {
            String x = readText(m);
            if (x.contains("package=")) return;
            int p = x.indexOf("<manifest");
            if (p < 0) return;
            x = x.substring(0, p + 9) + " package=\"" + projectPackage + "\"" + x.substring(p + 9);
            writeText(m, x);
        } catch (IOException ignored) { }
    }

    // ===== Launcher icon (mipmap) =====

    /** mipmap-*\/ic_launcher.png banata hai. Kuch naya bana ho to true. */
    private boolean ensureLauncherIcons() {
        File res = new File(projectRoot, "Android/app/src/main/res");
        if (!res.isDirectory()) return false;

        String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        boolean created = false;

        for (int i = 0; i < dirs.length; i++) {
            File dir = new File(res, dirs[i]);
            dir.mkdirs();
            File f = new File(dir, "ic_launcher.png");
            if (f.exists()) continue;

            Bitmap bmp = drawDefaultIcon(sizes[i]);
            try (OutputStream os = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                created = true;
            } catch (IOException ignored) {
            } finally {
                bmp.recycle();
            }
        }
        return created;
    }

    /** Simple default icon: purple rounded square, white circle, gold dot. */
    private Bitmap drawDefaultIcon(int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        float r = s * 0.22f;
        p.setColor(0xFF5E35B1);
        c.drawRoundRect(new RectF(0, 0, s, s), r, r, p);

        p.setColor(0xFFFFFFFF);
        c.drawCircle(s / 2f, s / 2f, s * 0.30f, p);

        p.setColor(0xFFD4AF37);
        c.drawCircle(s / 2f, s / 2f, s * 0.16f, p);
        return b;
    }

    /** Old manifests: <application> me android:icon nahi hai to jod do. */
    private void ensureManifestIcon() {
        File m = new File(projectRoot, "Android/app/src/main/AndroidManifest.xml");
        if (!m.isFile()) return;
        try {
            String x = readText(m);
            if (x.contains("android:icon")) return;
            int p = x.indexOf("<application");
            if (p < 0) return;
            x = x.substring(0, p + 12) + " android:icon=\"@mipmap/ic_launcher\"" + x.substring(p + 12);
            writeText(m, x);
        } catch (IOException ignored) { }
    }

    // ===== Gradle files (project root) =====

    /** Android Studio jaisi root files. Sirf agar nahi hain. Compiler inhe use nahi karta. */
    private void ensureGradleFiles() {
        File mod = new File(projectRoot, "Android");
        if (!new File(mod, "app/src/main").isDirectory()) return;

        writeIfMissing(new File(mod, "settings.gradle"), settingsGradleText());
        writeIfMissing(new File(mod, "build.gradle"), rootBuildGradleText());
        writeIfMissing(new File(mod, "gradle.properties"), gradlePropertiesText());
        writeIfMissing(new File(mod, "local.properties"), localPropertiesText());
        writeIfMissing(new File(mod, "gradle/wrapper/gradle-wrapper.properties"), wrapperPropertiesText());
        writeIfMissing(new File(mod, "app/build.gradle"), appBuildGradleText());
        writeIfMissing(new File(mod, "app/proguard-rules.pro"), proguardText());
    }

    private void writeIfMissing(File f, String text) {
        if (f.exists()) return;
        try {
            f.getParentFile().mkdirs();
            writeText(f, text);
        } catch (IOException ignored) { }
    }

    private String groovyEsc(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String settingsGradleText() {
        return "pluginManagement {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "        gradlePluginPortal()\n" +
                "    }\n" +
                "}\n" +
                "dependencyResolutionManagement {\n" +
                "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "}\n" +
                "rootProject.name = '" + groovyEsc(projectName) + "'\n" +
                "include ':app'\n";
    }

    private String rootBuildGradleText() {
        return "// Top-level build file\n" +
                "plugins {\n" +
                "    id 'com.android.application' version '8.2.2' apply false\n" +
                "}\n";
    }

    private String gradlePropertiesText() {
        return "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n" +
                "android.useAndroidX=true\n" +
                "android.nonTransitiveRClass=true\n";
    }

    private String localPropertiesText() {
        return "## Android SDK ka path (Aalam SDK app se aayega)\n" +
                "# sdk.dir=/path/to/Android/sdk\n";
    }

    private String wrapperPropertiesText() {
        return "distributionBase=GRADLE_USER_HOME\n" +
                "distributionPath=wrapper/dists\n" +
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip\n" +
                "zipStoreBase=GRADLE_USER_HOME\n" +
                "zipStorePath=wrapper/dists\n";
    }

    private String appBuildGradleText() {
        return "// Note: Aalam Compiler Gradle nahi chalata. Ye file project ke structure ke liye hai.\n" +
                "plugins {\n" +
                "    id 'com.android.application'\n" +
                "}\n\n" +
                "android {\n" +
                "    namespace '" + groovyEsc(projectPackage) + "'\n" +
                "    compileSdk 34\n\n" +
                "    defaultConfig {\n" +
                "        applicationId \"" + projectPackage + "\"\n" +
                "        minSdk 24\n" +
                "        targetSdk 34\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "    }\n\n" +
                "    buildTypes {\n" +
                "        release {\n" +
                "            minifyEnabled false\n" +
                "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n" +
                "        }\n" +
                "    }\n\n" +
                "    compileOptions {\n" +
                "        sourceCompatibility JavaVersion.VERSION_1_8\n" +
                "        targetCompatibility JavaVersion.VERSION_1_8\n" +
                "    }\n" +
                "}\n\n" +
                "dependencies {\n" +
                "    implementation 'androidx.appcompat:appcompat:1.6.1'\n" +
                "}\n";
    }

    private String proguardText() {
        return "# Add project specific ProGuard rules here.\n" +
                "# You can control the set of applied configuration files using the\n" +
                "# proguardFiles setting in build.gradle.\n";
    }

    // ===== Seed (new project) =====

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
                    "  \"package\": \"" + projectPackage + "\",\n" +
                    "  \"engine\": \"Aalam Game Engine\",\n" +
                    "  \"platform\": \"" + platform + "\",\n" +
                    "  \"orientation\": \"" + projectOrientation + "\",\n" +
                    "  \"language\": \"" + String.join(" + ", languages) + "\"\n" +
                    "}");
        } else if (isAndroid) {
            String main = "app/src/main";
            String orient = projectOrientation.equals("landscape") ? "sensorLandscape" : "portrait";

            seed(platform, main, "AndroidManifest.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "    package=\"" + projectPackage + "\">\n\n" +
                    "    <application\n" +
                    "        android:icon=\"@mipmap/ic_launcher\"\n" +
                    "        android:label=\"@string/app_name\"\n" +
                    "        android:theme=\"@style/AppTheme\">\n" +
                    "        <activity\n" +
                    "            android:name=\".MainActivity\"\n" +
                    "            android:exported=\"true\"\n" +
                    "            android:screenOrientation=\"" + orient + "\">\n" +
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

            ensureAndroidDirs();     // strings.xml + styles.xml
            ensureLauncherIcons();   // mipmap icons
            ensureGradleFiles();     // build.gradle, settings.gradle ...
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

        // hide / show the preview column with the arrow (like the Project tree)
        previewHolder = holder;
        ViewGroup.LayoutParams hlp = holder.getLayoutParams();
        if (hlp != null) {
            holderWidth = hlp.width;
            if (hlp instanceof LinearLayout.LayoutParams) holderWeight = ((LinearLayout.LayoutParams) hlp).weight;
        }
        preview.setOnCollapseToggle(() -> togglePreviewPanel());
    }

    private void togglePreviewPanel() {
        if (preview == null || previewHolder == null) return;
        boolean collapse = !preview.isCollapsed();
        preview.setCollapsed(collapse);

        ViewGroup.LayoutParams lp = previewHolder.getLayoutParams();
        if (lp == null) return;
        if (collapse) {
            lp.width = dp(44);
            if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) lp).weight = 0f;
        } else {
            lp.width = holderWidth;
            if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) lp).weight = holderWeight;
        }
        previewHolder.setLayoutParams(lp);
    }

    private void setupEditorWatcher() {
        codeEditorText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) {
                if (!loadingFile) runOk = false; // code changed -> run again
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
                        if (codeEditorText instanceof CodeEditText) {
                            ((CodeEditText) codeEditorText).setFileName(currentFile.getName());
                        }
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
                runOk = false; // files added / renamed / deleted -> run again
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
        } else if (n.endsWith(".png")) {
            text = "🖼 " + n;
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
        if (codeEditorText instanceof CodeEditText) {
            ((CodeEditText) codeEditorText).setFileName(f.getName());
        }
        loadingFile = true;
        codeEditorText.setText(content);
        loadingFile = false;
        openFileTabLabel.setText(relativePath(f));
        highlightCurrent();
        updatePreviewFor(f, content);
        if (preview != null) preview.setAutoLandscape(detectLandscape(content));
    }

    /** True if the project/activity is landscape (manifest screenOrientation or setRequestedOrientation). */
    private boolean detectLandscape(String currentContent) {
        if (projectType.equals("Game")) return false;
        if (currentContent.contains("SCREEN_ORIENTATION_LANDSCAPE")
                || currentContent.contains("SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
                || currentContent.contains("SCREEN_ORIENTATION_REVERSE_LANDSCAPE")
                || currentContent.contains("SCREEN_ORIENTATION_USER_LANDSCAPE")) {
            return true;
        }
        File m = findFileByName(projectRoot, "AndroidManifest.xml");
        if (m != null) {
            try {
                String x = readText(m);
                if (x.matches("(?s).*screenOrientation\\s*=\\s*\"(sensorLandscape|landscape|reverseLandscape|userLandscape)\".*")) {
                    return true;
                }
            } catch (IOException ignored) { }
        }
        return false;
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

    /** "Check" button: checks only the open file. */
    private void checkErrorsInCurrentFile() {
        if (currentFile == null) return;

        LinearLayout buildLogPanel = findViewById(R.id.buildLogPanel);
        TextView buildLogText = findViewById(R.id.buildLogText);
        TextView btnToggleBuildPanel = findViewById(R.id.btnToggleBuildPanel);

        buildLogPanel.setVisibility(View.VISIBLE);
        btnToggleBuildPanel.setText("⌃");

        String fileName = currentFile.getName();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ProjectChecker.checkText(fileName, codeEditorText.getText().toString(), errors, warnings);

        StringBuilder sb = new StringBuilder();
        sb.append("Checking ").append(fileName).append(" ...\n\n");
        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("✅ No issues found.");
        } else {
            appendIssues(sb, errors, warnings);
        }
        buildLogText.setText(sb.toString());
    }

    private void appendIssues(StringBuilder sb, List<String> errors, List<String> warnings) {
        if (!errors.isEmpty()) {
            sb.append("❌ ").append(errors.size()).append(" error(s):\n");
            for (String e : errors) sb.append("• ").append(e).append("\n");
            sb.append("\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("⚠ ").append(warnings.size()).append(" warning(s):\n");
            for (String w : warnings) sb.append("• ").append(w).append("\n");
        }
    }

    // ===== Run button: check the WHOLE project, then unlock Preview =====

    private void runProjectCheck() {
        saveCurrentFile();

        LinearLayout buildLogPanel = findViewById(R.id.buildLogPanel);
        TextView buildLogText = findViewById(R.id.buildLogText);
        TextView btnToggleBuildPanel = findViewById(R.id.btnToggleBuildPanel);
        buildLogPanel.setVisibility(View.VISIBLE);
        btnToggleBuildPanel.setText("⌃");

        ProjectChecker.Result r = ProjectChecker.check(projectRoot, projectType);
        runOk = r.errors.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("▶ Run: ").append(projectName).append("\n");
        sb.append(r.fileCount).append(" files check hui\n\n");

        if (runOk) {
            sb.append("✅ Run successful");
            if (!r.warnings.isEmpty()) sb.append(" (").append(r.warnings.size()).append(" warning)");
            sb.append("\n\n");
            if (!r.warnings.isEmpty()) {
                appendIssues(sb, r.errors, r.warnings);
                sb.append("\n");
            }
            sb.append("👉 Ab Preview tab dabao aur app chalao.");
            Toast.makeText(this, "Run successful ✅ - ab Preview me chalao", Toast.LENGTH_SHORT).show();
        } else {
            sb.append("❌ Run failed\n\n");
            appendIssues(sb, r.errors, r.warnings);
            sb.append("\nErrors theek karke dobara ▶ Run dabao.");
            Toast.makeText(this, "Run failed - " + r.errors.size() + " error", Toast.LENGTH_SHORT).show();
        }
        buildLogText.setText(sb.toString());
    }

    /** Preview tab: only works after a successful Run. */
    private void openPreviewTab() {
        if (!runOk) {
            LinearLayout buildLogPanel = findViewById(R.id.buildLogPanel);
            TextView buildLogText = findViewById(R.id.buildLogText);
            TextView btnToggleBuildPanel = findViewById(R.id.btnToggleBuildPanel);
            buildLogPanel.setVisibility(View.VISIBLE);
            btnToggleBuildPanel.setText("⌃");
            buildLogText.setText("⚠ Pehle ▶ Run dabao.\n\nRun me poore project ke code aur files check hote hain. "
                    + "Build log me \"Run successful\" aane ke baad Preview me app chalega.\n"
                    + "(Code ya files badalne ke baad dobara Run karna hoga.)");
            Toast.makeText(this, "Pehle ▶ Run karo", Toast.LENGTH_SHORT).show();
            selectTab(tabCodeView);
            return;
        }
        startPreviewApp();
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
                return "package " + projectPackage + ";\n\n" +
                        "import android.graphics.Canvas;\n" +
                        "import android.graphics.Color;\n" +
                        "import android.graphics.Paint;\n" +
                        "import com.aalam.engine.GameEngine;\n\n" +
                        "public class GameMain extends GameEngine {\n" +
                        "    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);\n" +
                        "    private float x, y;\n" +
                        "    private float vx = 280f, vy = 360f;\n" +
                        "    private final float r = 40f;\n\n" +
                        "    @Override\n" +
                        "    public void onGameStart() {\n" +
                        "        loadScene(\"MainScene\");\n" +
                        "        x = getWidth() / 2f;\n" +
                        "        y = getHeight() / 2f;\n" +
                        "        paint.setTextSize(48f);\n" +
                        "    }\n\n" +
                        "    @Override\n" +
                        "    public void onUpdate(float dt) {\n" +
                        "        x += vx * dt;\n" +
                        "        y += vy * dt;\n" +
                        "        if (x < r || x > getWidth() - r) vx = -vx;\n" +
                        "        if (y < r || y > getHeight() - r) vy = -vy;\n" +
                        "    }\n\n" +
                        "    @Override\n" +
                        "    public void onDraw(Canvas canvas) {\n" +
                        "        canvas.drawColor(Color.rgb(20, 20, 30));\n" +
                        "        paint.setColor(Color.rgb(212, 175, 55));\n" +
                        "        canvas.drawCircle(x, y, r, paint);\n" +
                        "        paint.setColor(Color.WHITE);\n" +
                        "        canvas.drawText(\"Aalam Game\", 40f, 80f, paint);\n" +
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
        tabCodeView = findViewById(R.id.tabCode);
        TextView tabSplit = findViewById(R.id.tabSplit);
        tabPreviewView = findViewById(R.id.tabPreview);

        allTabs = new TextView[]{tabDesign, tabCodeView, tabSplit, tabPreviewView};

        for (TextView tab : allTabs) {
            tab.setOnClickListener(v -> {
                selectTab(tab);
                if (tab == tabPreviewView) {
                    openPreviewTab();
                } else if (tab != tabCodeView) {
                    Toast.makeText(this, tab.getText() + " view - coming soon", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void selectTab(TextView tab) {
        for (TextView t : allTabs) {
            t.setBackgroundResource(R.drawable.bg_type_card);
            t.setTextColor(0xFFCCCCCC);
        }
        tab.setBackgroundColor(0xFFD4AF37);
        tab.setTextColor(0xFF000000);
    }

    // ===== Run / Preview tab: run the app UI inside Studio, no compile =====

    private void startPreviewApp() {
        saveCurrentFile();

        if (projectType.equals("Game")) {
            Toast.makeText(this, "Game run Aalam Game Engine ke saath aayega", Toast.LENGTH_SHORT).show();
            selectTab(tabCodeView);
            return;
        }
        if (preview == null) {
            Toast.makeText(this, "Preview panel nahi mila", Toast.LENGTH_SHORT).show();
            selectTab(tabCodeView);
            return;
        }

        File layout = resolveLaunchLayout();
        if (layout == null) {
            Toast.makeText(this, "Koi layout XML nahi mili (res/layout me banao)", Toast.LENGTH_LONG).show();
            selectTab(tabCodeView);
            return;
        }

        String xml;
        try {
            xml = readText(layout);
        } catch (IOException e) {
            Toast.makeText(this, "Layout nahi padh payi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            selectTab(tabCodeView);
            return;
        }

        String label = resolveAppLabel();
        TextView log = findViewById(R.id.buildLogText);
        log.setText("▶ Running " + label + "\nLayout: " + layout.getName()
                + "\n(UI chalta hai. Java logic abhi nahi chalta.)");

        selectTab(tabPreviewView);
        previewLaunched = true;
        try {
            Intent i = new Intent(this, AppPreviewActivity.class);
            i.putExtra(AppPreviewActivity.EXTRA_LAYOUT_PATH, layout.getAbsolutePath());
            i.putExtra(AppPreviewActivity.EXTRA_TITLE, label);
            i.putExtra(AppPreviewActivity.EXTRA_LANDSCAPE, preview.isLandscape());
            startActivity(i);
        } catch (android.content.ActivityNotFoundException e) {
            previewLaunched = false;
            Toast.makeText(this, "Full screen ke liye AndroidManifest me AppPreviewActivity add karo",
                    Toast.LENGTH_LONG).show();
            preview.showRunner(xml, label, preview.isLandscape(), () -> selectTab(tabCodeView));
        }
    }

    /** Launcher activity (manifest) -> setContentView(R.layout.x) -> that layout file. */
    private File resolveLaunchLayout() {
        File manifest = findFileByName(projectRoot, "AndroidManifest.xml");
        if (manifest != null) {
            try {
                String x = readText(manifest);
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("<activity\\b([^>]*)>(.*?)</activity>", java.util.regex.Pattern.DOTALL)
                        .matcher(x);
                while (m.find()) {
                    if (!m.group(2).contains("android.intent.category.LAUNCHER")) continue;
                    java.util.regex.Matcher nm = java.util.regex.Pattern
                            .compile("android:name\\s*=\\s*\"([^\"]+)\"").matcher(m.group(1));
                    if (!nm.find()) continue;
                    String cls = nm.group(1);
                    cls = cls.substring(cls.lastIndexOf('.') + 1);
                    File src = findFileByName(projectRoot, cls + ".java");
                    if (src == null) src = findFileByName(projectRoot, cls + ".kt");
                    if (src == null) continue;
                    java.util.regex.Matcher lm = java.util.regex.Pattern
                            .compile("R\\.layout\\.([A-Za-z0-9_]+)").matcher(readText(src));
                    if (lm.find()) {
                        File lay = findFileByName(projectRoot, lm.group(1) + ".xml");
                        if (lay != null) return lay;
                    }
                }
            } catch (IOException ignored) { }
        }
        if (currentFile != null && isLayoutXml(currentFile)) return currentFile;
        return findFirstLayout(projectRoot);
    }

    private File findFirstLayout(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        Arrays.sort(kids);
        for (File f : kids) {
            if (!f.isDirectory()) continue;
            if (f.getName().equals("layout")) {
                File[] xmls = f.listFiles();
                if (xmls != null) {
                    Arrays.sort(xmls);
                    for (File x : xmls) if (x.isFile() && x.getName().endsWith(".xml")) return x;
                }
            } else {
                File r = findFirstLayout(f);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** android:label of <application> (resolves @string/xxx from strings.xml). */
    private String resolveAppLabel() {
        File manifest = findFileByName(projectRoot, "AndroidManifest.xml");
        if (manifest == null) return projectName;
        try {
            String x = readText(manifest);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<application\\b[^>]*android:label\\s*=\\s*\"([^\"]+)\"").matcher(x);
            if (m.find()) {
                String l = m.group(1);
                if (!l.startsWith("@string/")) return l;
                File sf = findFileByName(projectRoot, "strings.xml");
                if (sf != null) {
                    java.util.regex.Matcher sm = java.util.regex.Pattern
                            .compile("<string\\s+name\\s*=\\s*\"" + java.util.regex.Pattern.quote(l.substring(8))
                                    + "\"[^>]*>(.*?)</string>", java.util.regex.Pattern.DOTALL)
                            .matcher(readText(sf));
                    if (sm.find()) return sm.group(1).trim();
                }
            }
        } catch (IOException ignored) { }
        return projectName;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
