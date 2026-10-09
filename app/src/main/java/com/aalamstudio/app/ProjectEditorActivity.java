package com.aalamstudio.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ProjectEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PROJECT_TYPE = "project_type";
    public static final String EXTRA_PROJECT_LANGUAGE = "project_language";
    public static final String EXTRA_PROJECT_PLATFORM = "project_platform";
    public static final String EXTRA_PROJECT_PACKAGE = "project_package";

    private static final String COMPILER_PACKAGE = "com.aalamstudio.compiler";

    private String projectName = "My Project";
    private String projectType = "App";
    private String projectLanguage = "Java";
    private String projectPlatform = "Android";
    private String projectPackage = "com.example.app";

    private EditText codeEditorText;
    private TextView openFileTabLabel;

    private Map<String, String> fileContents = new LinkedHashMap<>();
    private Map<String, TextView> fileItemViews = new LinkedHashMap<>();
    private TextView selectedFileItem;
    private String currentFileKey = null;
    private String defaultOpenKey = null;

    private SharedPreferences codePrefs;

    private LinearLayout fileTreePanel;
    private ScrollView fileTreeScroll;
    private TextView toggleFileTreePanel;
    private boolean fileTreeExpanded = true;

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
        buildFileTree();

        if (defaultOpenKey != null) {
            openFile(defaultOpenKey);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentFile();
    }

    // ===== Error checking =====

    private void checkErrorsInCurrentFile() {
        if (currentFileKey == null) return;

        LinearLayout buildLogPanel = findViewById(R.id.buildLogPanel);
        TextView buildLogText = findViewById(R.id.buildLogText);
        TextView btnToggleBuildPanel = findViewById(R.id.btnToggleBuildPanel);

        buildLogPanel.setVisibility(View.VISIBLE);
        btnToggleBuildPanel.setText("⌃");

        String content = codeEditorText.getText().toString();
        String fileName = currentFileKey.contains("::") ? currentFileKey.split("::", 2)[1] : currentFileKey;

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

    private void exportAsc(TextView buildLogText) {
        try {
            JSONObject manifest = new JSONObject();
            manifest.put("name", projectName);
            manifest.put("package", projectPackage);
            manifest.put("type", projectType);
            manifest.put("language", projectLanguage);
            manifest.put("platform", projectPlatform);
            manifest.put("createdBy", "Aalam Studio");

            JSONArray filesArray = new JSONArray();
            for (String key : fileContents.keySet()) {
                filesArray.put(key.replace("::", "/"));
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
            zos.write(manifest.toString(2).getBytes());
            zos.closeEntry();

            for (Map.Entry<String, String> entry : fileContents.entrySet()) {
                String path = "src/" + entry.getKey().replace("::", "/");
                ZipEntry fileEntry = new ZipEntry(path);
                zos.putNextEntry(fileEntry);
                zos.write(entry.getValue().getBytes());
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

    // ===== File tree building (per platform, collapsible) =====

    private void buildFileTree() {
        LinearLayout fileTreeContainer = findViewById(R.id.fileTreeContainer);
        fileTreeContainer.removeAllViews();
        fileContents.clear();
        fileItemViews.clear();
        defaultOpenKey = null;

        List<String> platforms = splitList(projectPlatform);
        if (platforms.isEmpty()) platforms.add("Android");

        List<String> chosenLanguages = splitList(projectLanguage);

        for (String platform : platforms) {
            List<String> languagesForPlatform = intersectLanguages(platform, chosenLanguages);
            if (languagesForPlatform.isEmpty()) {
                languagesForPlatform = new ArrayList<>();
                languagesForPlatform.add(defaultLanguageFor(platform));
            }
            addPlatformSection(fileTreeContainer, platform, languagesForPlatform);
        }
    }

    private void addPlatformSection(LinearLayout parent, String platform, List<String> languages) {
        TextView header = new TextView(this);
        header.setText("▸ " + platform);
        header.setTextColor(0xFFD4AF37);
        header.setTextSize(11);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(dp(2), dp(8), dp(2), dp(4));
        parent.addView(header);

        LinearLayout sectionContainer = new LinearLayout(this);
        sectionContainer.setOrientation(LinearLayout.VERTICAL);
        sectionContainer.setVisibility(View.GONE);
        parent.addView(sectionContainer);

        header.setOnClickListener(v -> {
            boolean expanded = sectionContainer.getVisibility() == View.VISIBLE;
            sectionContainer.setVisibility(expanded ? View.GONE : View.VISIBLE);
            header.setText((expanded ? "▸ " : "▾ ") + platform);
        });

        populatePlatformFiles(sectionContainer, platform, languages);
    }

    private void populatePlatformFiles(LinearLayout container, String platform, List<String> languages) {
        boolean isAndroid = platform.equals("Android");

        if (projectType.equals("Game")) {
            addFolderLabel(container, "  🎮 game");

            String manifestKey = platform + "::GameManifest.xml";
            fileContents.put(manifestKey, loadOrDefault(manifestKey,
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<game-manifest>\n" +
                    "    <name>" + projectName + "</name>\n" +
                    "    <platform>" + platform + "</platform>\n" +
                    "    <engine>Aalam Game Engine</engine>\n" +
                    "</game-manifest>"));
            addFileItem(container, manifestKey, "GameManifest.xml", "    📄 ");

            addFolderLabel(container, "    📁 scripts");
            for (String lang : languages) {
                String fileName = "GameMain." + getExtension(lang);
                String key = platform + "::" + fileName;
                fileContents.put(key, loadOrDefault(key, buildGameMainContent(lang)));
                addFileItem(container, key, fileName, "      📄 ");
                if (defaultOpenKey == null) defaultOpenKey = key;
            }

            addFolderLabel(container, "    📁 config");
            String configKey = platform + "::game_config.json";
            fileContents.put(configKey, loadOrDefault(configKey,
                    "{\n" +
                    "  \"gameName\": \"" + projectName + "\",\n" +
                    "  \"engine\": \"Aalam Game Engine\",\n" +
                    "  \"platform\": \"" + platform + "\",\n" +
                    "  \"language\": \"" + String.join(" + ", languages) + "\"\n" +
                    "}"));
            addFileItem(container, configKey, "game_config.json", "      📄 ");

        } else {
            addFolderLabel(container, "  📁 app");

            if (isAndroid) {
                addFolderLabel(container, "    📁 manifests");
                String manifestKey = platform + "::AndroidManifest.xml";
                fileContents.put(manifestKey, loadOrDefault(manifestKey,
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
                        "</manifest>"));
                addFileItem(container, manifestKey, "AndroidManifest.xml", "      📄 ");
            } else {
                addFolderLabel(container, "    📁 config");
                String configKey = platform + "::app_config.json";
                fileContents.put(configKey, loadOrDefault(configKey,
                        "{\n" +
                        "  \"appName\": \"" + projectName + "\",\n" +
                        "  \"platform\": \"" + platform + "\",\n" +
                        "  \"language\": \"" + String.join(" + ", languages) + "\"\n" +
                        "}"));
                addFileItem(container, configKey, "app_config.json", "      📄 ");
            }

            addFolderLabel(container, "    📁 src");
            for (String lang : languages) {
                String fileName = (isAndroid ? "MainActivity." : "Main.") + getExtension(lang);
                String key = platform + "::" + fileName;
                fileContents.put(key, loadOrDefault(key, buildAppMainContent(lang, isAndroid)));
                addFileItem(container, key, fileName, "      📄 ");
                if (defaultOpenKey == null) defaultOpenKey = key;
            }

            if (isAndroid) {
                addFolderLabel(container, "    📁 res/layout");
                String layoutKey = platform + "::activity_main.xml";
                fileContents.put(layoutKey, loadOrDefault(layoutKey,
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\"\n" +
                        "    android:gravity=\"center\">\n\n" +
                        "    <TextView\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:text=\"Hello World!\" />\n\n" +
                        "</LinearLayout>"));
                addFileItem(container, layoutKey, "activity_main.xml", "      📄 ");
            }
        }
    }

    // ===== Content generators =====

    private String buildAppMainContent(String lang, boolean isAndroid) {
        if (isAndroid && lang.equals("Kotlin")) {
            return "package com.example.app\n\n" +
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
            return "package com.example.app;\n\n" +
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

    // ===== Save / load per-file code =====

    private String loadOrDefault(String key, String defaultContent) {
        String saved = codePrefs.getString(key, null);
        return saved != null ? saved : defaultContent;
    }

    private void saveCurrentFile() {
        if (currentFileKey == null || codeEditorText == null) return;
        String content = codeEditorText.getText().toString();
        fileContents.put(currentFileKey, content);
        codePrefs.edit().putString(currentFileKey, content).apply();
    }

    // ===== File tree item helpers =====

    private void addFolderLabel(LinearLayout container, String text) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(0xFFCCCCCC);
        item.setTextSize(10);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));
        container.addView(item);
    }

    private void addFileItem(LinearLayout container, String key, String fileName, String prefix) {
        TextView item = new TextView(this);
        item.setText(prefix + fileName);
        item.setTextColor(0xFFCCCCCC);
        item.setTextSize(10);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));
        item.setOnClickListener(v -> openFile(key));

        fileItemViews.put(key, item);
        container.addView(item);
    }

    private void openFile(String key) {
        String content = fileContents.get(key);
        if (content == null) return;

        saveCurrentFile();

        currentFileKey = key;
        codeEditorText.setText(content);

        String[] parts = key.split("::", 2);
        String platform = parts[0];
        String fileName = parts.length > 1 ? parts[1] : key;
        openFileTabLabel.setText(platform + " / " + fileName);

        if (selectedFileItem != null) {
            selectedFileItem.setTextColor(0xFFCCCCCC);
            selectedFileItem.setBackgroundColor(0x00000000);
        }

        TextView newSelected = fileItemViews.get(key);
        if (newSelected != null) {
            newSelected.setTextColor(0xFFD4AF37);
            newSelected.setBackgroundColor(0x22D4AF37);
            selectedFileItem = newSelected;
        }
    }

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
