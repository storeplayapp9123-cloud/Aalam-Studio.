package com.aalamstudio.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PROJECT_TYPE = "project_type";
    public static final String EXTRA_PROJECT_LANGUAGE = "project_language";
    public static final String EXTRA_PROJECT_PLATFORM = "project_platform";

    private String projectName = "My Project";
    private String projectType = "App";
    private String projectLanguage = "Java";
    private String projectPlatform = "Android";

    private EditText codeEditorText;
    private TextView openFileTabLabel;

    private Map<String, String> fileContents = new LinkedHashMap<>();
    private Map<String, TextView> fileItemViews = new LinkedHashMap<>();
    private TextView selectedFileItem;
    private String currentFileKey = null;
    private String defaultOpenKey = null;

    private SharedPreferences codePrefs;

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
            buildLogText.setText("Aalam Compiler not connected yet.\nBuild APK will work once the compiler module is ready.");
            Toast.makeText(this, "Build APK - coming soon", Toast.LENGTH_SHORT).show();
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
