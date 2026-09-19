package com.aalamstudio.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class NewProjectActivity extends AppCompatActivity {

    private LinearLayout typeApp, typeGame, type3D, typeTemplate;
    private LinearLayout orientPortrait, orientLandscape;
    private LinearLayout[] typeCards;
    private LinearLayout[] orientCards;
    private String selectedType = "App";
    private String selectedOrientation = "Portrait";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_project);

        EditText inputProjectName = findViewById(R.id.inputProjectName);
        EditText inputPackageName = findViewById(R.id.inputPackageName);
        Button btnCancel = findViewById(R.id.btnCancel);
        Button btnCreateProject = findViewById(R.id.btnCreateProject);

        typeApp = findViewById(R.id.typeApp);
        typeGame = findViewById(R.id.typeGame);
        type3D = findViewById(R.id.type3D);
        typeTemplate = findViewById(R.id.typeTemplate);
        typeCards = new LinearLayout[]{typeApp, typeGame, type3D, typeTemplate};

        orientPortrait = findViewById(R.id.orientPortrait);
        orientLandscape = findViewById(R.id.orientLandscape);
        orientCards = new LinearLayout[]{orientPortrait, orientLandscape};

        typeApp.setOnClickListener(v -> selectType(typeApp, "App"));
        typeGame.setOnClickListener(v -> selectType(typeGame, "Game"));
        type3D.setOnClickListener(v -> selectType(type3D, "3D Experience"));
        typeTemplate.setOnClickListener(v -> selectType(typeTemplate, "Template"));

        orientPortrait.setOnClickListener(v -> selectOrientation(orientPortrait, "Portrait"));
        orientLandscape.setOnClickListener(v -> selectOrientation(orientLandscape, "Landscape"));

        selectType(typeApp, "App");
        selectOrientation(orientPortrait, "Portrait");

        btnCancel.setOnClickListener(v -> finish());

        btnCreateProject.setOnClickListener(v -> {
            String name = inputProjectName.getText().toString().trim();
            String pkg = inputPackageName.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "Project name is required", Toast.LENGTH_SHORT).show();
                return;
            }
            if (pkg.isEmpty()) {
                Toast.makeText(this, "Package name is required", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this,
                    selectedType + " '" + name + "' (" + selectedOrientation + ") created!",
                    Toast.LENGTH_LONG).show();
            finish();
        });
    }

    private void selectType(LinearLayout selected, String type) {
        for (LinearLayout card : typeCards) {
            card.setSelected(card == selected);
        }
        selectedType = type;
    }

    private void selectOrientation(LinearLayout selected, String orientation) {
        for (LinearLayout card : orientCards) {
            card.setSelected(card == selected);
        }
        selectedOrientation = orientation;
    }
}
