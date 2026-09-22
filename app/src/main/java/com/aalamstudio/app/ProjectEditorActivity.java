package com.aalamstudio.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ProjectEditorActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_NAME = "project_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_editor);

        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        if (projectName == null) projectName = "My Project";

        TextView editorProjectName = findViewById(R.id.editorProjectName);
        editorProjectName.setText(projectName);

        TextView btnCloseEditor = findViewById(R.id.btnCloseEditor);
        btnCloseEditor.setOnClickListener(v -> finish());

        Button btnRun = findViewById(R.id.btnRun);
        Button btnBuildApk = findViewById(R.id.btnBuildApk);
        TextView buildLogText = findViewById(R.id.buildLogText);

        btnRun.setOnClickListener(v ->
                Toast.makeText(this, "Run - coming soon", Toast.LENGTH_SHORT).show());

        btnBuildApk.setOnClickListener(v -> {
            buildLogText.setText("Aalam Compiler not connected yet.\nBuild APK will work once the compiler module is ready.");
            Toast.makeText(this, "Build APK - coming soon", Toast.LENGTH_SHORT).show();
        });

        setupFileTree();
        setupTabs();
    }

    private void setupFileTree() {
        LinearLayout fileTreeContainer = findViewById(R.id.fileTreeContainer);
        String[] files = {
                "📁 app",
                "  📁 manifests",
                "    📄 AndroidManifest.xml",
                "  📁 java",
                "    📄 MainActivity.java",
                "  📁 res",
                "    📁 layout",
                "      📄 activity_main.xml",
                "    📁 values",
                "      📄 colors.xml",
                "      📄 strings.xml"
        };

        for (String fileName : files) {
            TextView item = new TextView(this);
            item.setText(fileName);
            item.setTextColor(0xFFCCCCCC);
            item.setTextSize(10);
            item.setPadding(dp(2), dp(4), dp(2), dp(4));
            fileTreeContainer.addView(item);
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
