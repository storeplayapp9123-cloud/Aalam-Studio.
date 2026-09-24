package com.aalamstudio.app;

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
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_NAME = "project_name";
    public static final String EXTRA_PROJECT_TYPE = "project_type";

    private EditText codeEditorText;
    private TextView openFileTabLabel;
    private Map<String, TextView> fileItemViews = new LinkedHashMap<>();
    private Map<String, String> fileContents = new LinkedHashMap<>();
    private TextView selectedFileItem;
    private String projectType = "App";
    private String defaultOpenFile = "MainActivity.java";

    private LinearLayout fileTreePanel;
    private ScrollView fileTreeScroll;
    private TextView toggleFileTree;
    private boolean fileTreeExpanded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_editor);

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        if (projectName == null) projectName = "My Project";

        String type = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        if (type != null) projectType = type;

        TextView editorProjectName = findViewById(R.id.editorProjectName);
        editorProjectName.setText(projectName);

        codeEditorText = findViewById(R.id.codeEditorText);
        openFileTabLabel = findViewById(R.id.openFileTabLabel);

        TextView btnCloseEditor = findViewById(R.id.btnCloseEditor);
        btnCloseEditor.setOnClickListener(v -> finish());

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
            buildLogText.setText("Aalam Compiler not connected yet.\nBuild APK will work once the compiler module is ready.");
            Toast.makeText(this, "Build APK - coming soon", Toast.LENGTH_SHORT).show();
        });

        fileTreePanel = findViewById(R.id.fileTreePanel);
        fileTreeScroll = findViewById(R.id.fileTreeScroll);
        toggleFileTree = findViewById(R.id.toggleFileTree);

        toggleFileTree.setOnClickListener(v -> {
            fileTreeExpanded = !fileTreeExpanded;
            fileTreeScroll.setVisibility(fileTreeExpanded ? View.VISIBLE : View.GONE);
            toggleFileTree.setText(fileTreeExpanded ? "▾" : "▸");

            ViewGroup.LayoutParams params = fileTreePanel.getLayoutParams();
            params.width = dp(fileTreeExpanded ? 140 : 40);
            fileTreePanel.setLayoutParams(params);
        });

        if (projectType.equals("Game")) {
            setupGameFileContents();
            setupGameFileTree();
            defaultOpenFile = "GameMain.java";
        } else {
            setupAppFileContents();
            setupAppFileTree();
            defaultOpenFile = "MainActivity.java";
        }

        setupTabs();
        openFile(defaultOpenFile);
    }

    // ===== APP PROJECT FILES =====

    private void setupAppFileContents() {
        fileContents.put("AndroidManifest.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@style/Theme.App\">\n\n" +
                "        <activity\n" +
                "            android:name=\".MainActivity\"\n" +
                "            android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n\n" +
                "    </application>\n" +
                "</manifest>");

        fileContents.put("MainActivity.java",
                "package com.example.app;\n\n" +
                "import android.os.Bundle;\n" +
                "import androidx.appcompat.app.AppCompatActivity;\n\n" +
                "public class MainActivity extends AppCompatActivity {\n" +
                "    @Override\n" +
                "    protected void onCreate(Bundle savedInstanceState) {\n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        setContentView(R.layout.activity_main);\n" +
                "    }\n" +
                "}");

        fileContents.put("activity_main.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:orientation=\"vertical\"\n" +
                "    android:gravity=\"center\">\n\n" +
                "    <TextView\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Hello World!\"\n" +
                "        android:textSize=\"20sp\" />\n\n" +
                "</LinearLayout>");

        fileContents.put("colors.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <color name=\"purple_500\">#FF6200EE</color>\n" +
                "    <color name=\"black\">#FF000000</color>\n" +
                "    <color name=\"white\">#FFFFFFFF</color>\n" +
                "</resources>");

        fileContents.put("strings.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">My App</string>\n" +
                "</resources>");
    }

    private void setupAppFileTree() {
        LinearLayout fileTreeContainer = findViewById(R.id.fileTreeContainer);

        addFolderLabel(fileTreeContainer, "📁 app");
        addFolderLabel(fileTreeContainer, "  📁 manifests");
        addFileItem(fileTreeContainer, "AndroidManifest.xml", "    📄 ");
        addFolderLabel(fileTreeContainer, "  📁 java");
        addFileItem(fileTreeContainer, "MainActivity.java", "    📄 ");
        addFolderLabel(fileTreeContainer, "  📁 res");
        addFolderLabel(fileTreeContainer, "    📁 layout");
        addFileItem(fileTreeContainer, "activity_main.xml", "      📄 ");
        addFolderLabel(fileTreeContainer, "    📁 values");
        addFileItem(fileTreeContainer, "colors.xml", "      📄 ");
        addFileItem(fileTreeContainer, "strings.xml", "      📄 ");
    }

    // ===== GAME PROJECT FILES =====

    private void setupGameFileContents() {
        fileContents.put("GameManifest.xml",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<game-manifest>\n" +
                "    <name>My Game</name>\n" +
                "    <version>1.0</version>\n" +
                "    <orientation>landscape</orientation>\n" +
                "    <engine>Aalam Game Engine</engine>\n" +
                "</game-manifest>");

        fileContents.put("GameMain.java",
                "package com.example.game;\n\n" +
                "import com.aalam.engine.GameEngine;\n" +
                "import com.aalam.engine.Scene;\n\n" +
                "public class GameMain extends GameEngine {\n\n" +
                "    @Override\n" +
                "    public void onGameStart() {\n" +
                "        loadScene(\"MainScene\");\n" +
                "    }\n\n" +
                "    @Override\n" +
                "    public void onUpdate(float deltaTime) {\n" +
                "        // game loop logic\n" +
                "    }\n" +
                "}");

        fileContents.put("Player.java",
                "package com.example.game.entities;\n\n" +
                "import com.aalam.engine.Entity;\n\n" +
                "public class Player extends Entity {\n\n" +
                "    private int health = 100;\n" +
                "    private float speed = 5.0f;\n\n" +
                "    public void move(float x, float y) {\n" +
                "        // movement logic\n" +
                "    }\n\n" +
                "    public void takeDamage(int amount) {\n" +
                "        health -= amount;\n" +
                "    }\n" +
                "}");

        fileContents.put("MainScene.scene",
                "// Aalam Game Engine - Scene File\n" +
                "scene \"MainScene\" {\n" +
                "    background: \"sky_bg.png\"\n" +
                "    gravity: 9.8\n\n" +
                "    spawn Player at (0, 0)\n" +
                "    spawn Enemy at (100, 0)\n" +
                "    spawn Platform at (0, -50)\n" +
                "}");

        fileContents.put("game_config.json",
                "{\n" +
                "  \"gameName\": \"My Game\",\n" +
                "  \"engine\": \"Aalam Game Engine\",\n" +
                "  \"renderPipeline\": \"2D\",\n" +
                "  \"targetFps\": 60,\n" +
                "  \"orientation\": \"landscape\"\n" +
                "}");
    }

    private void setupGameFileTree() {
        LinearLayout fileTreeContainer = findViewById(R.id.fileTreeContainer);

        addFolderLabel(fileTreeContainer, "🎮 game");
        addFileItem(fileTreeContainer, "GameManifest.xml", "  📄 ");
        addFolderLabel(fileTreeContainer, "  📁 scripts");
        addFileItem(fileTreeContainer, "GameMain.java", "    📄 ");
        addFolderLabel(fileTreeContainer, "  📁 entities");
        addFileItem(fileTreeContainer, "Player.java", "    📄 ");
        addFolderLabel(fileTreeContainer, "  📁 scenes");
        addFileItem(fileTreeContainer, "MainScene.scene", "    📄 ");
        addFolderLabel(fileTreeContainer, "  📁 config");
        addFileItem(fileTreeContainer, "game_config.json", "    📄 ");
    }

    // ===== SHARED HELPERS =====

    private void addFolderLabel(LinearLayout container, String text) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(0xFFCCCCCC);
        item.setTextSize(10);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));
        container.addView(item);
    }

    private void addFileItem(LinearLayout container, String fileName, String prefix) {
        TextView item = new TextView(this);
        item.setText(prefix + fileName);
        item.setTextColor(0xFFCCCCCC);
        item.setTextSize(10);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));
        item.setOnClickListener(v -> openFile(fileName));

        fileItemViews.put(fileName, item);
        container.addView(item);
    }

    private void openFile(String fileName) {
        String content = fileContents.get(fileName);
        if (content == null) return;

        codeEditorText.setText(content);
        openFileTabLabel.setText(fileName);

        if (selectedFileItem != null) {
            selectedFileItem.setTextColor(0xFFCCCCCC);
            selectedFileItem.setBackgroundColor(0x00000000);
        }

        TextView newSelected = fileItemViews.get(fileName);
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
