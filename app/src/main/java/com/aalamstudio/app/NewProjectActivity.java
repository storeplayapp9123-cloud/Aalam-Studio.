package com.aalamstudio.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class NewProjectActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_TYPE = "project_type";

    private String projectType = "App";
    private String selectedOrientation = "Portrait";
    private String selectedGameType = "2D";

    private LinearLayout orientPortrait, orientLandscape;
    private LinearLayout game2D, game3D;
    private LinearLayout[] orientCards;
    private LinearLayout[] gameTypeCards;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_project);

        projectType = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        if (projectType == null) projectType = "App";

        TextView screenTitle = findViewById(R.id.screenTitle);
        LinearLayout panelAppOptions = findViewById(R.id.panelAppOptions);
        LinearLayout panelGameOptions = findViewById(R.id.panelGameOptions);

        EditText inputProjectName = findViewById(R.id.inputProjectName);
        EditText inputPackageName = findViewById(R.id.inputPackageName);
        Button btnCancel = findViewById(R.id.btnCancel);
        Button btnCreateProject = findViewById(R.id.btnCreateProject);

        if (projectType.equals("Game")) {
            screenTitle.setText("New Game Project");
            panelAppOptions.setVisibility(View.GONE);
            panelGameOptions.setVisibility(View.VISIBLE);
        } else {
            screenTitle.setText("New App Project");
            panelAppOptions.setVisibility(View.VISIBLE);
            panelGameOptions.setVisibility(View.GONE);
        }

        orientPortrait = findViewById(R.id.orientPortrait);
        orientLandscape = findViewById(R.id.orientLandscape);
        orientCards = new LinearLayout[]{orientPortrait, orientLandscape};

        game2D = findViewById(R.id.game2D);
        game3D = findViewById(R.id.game3D);
        gameTypeCards = new LinearLayout[]{game2D, game3D};

        orientPortrait.setOnClickListener(v -> selectOrientation(orientPortrait, "Portrait"));
        orientLandscape.setOnClickListener(v -> selectOrientation(orientLandscape, "Landscape"));
        selectOrientation(orientPortrait, "Portrait");

        game2D.setOnClickListener(v -> selectGameType(game2D, "2D"));
        game3D.setOnClickListener(v -> selectGameType(game3D, "3D"));
        selectGameType(game2D, "2D");

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

            String detail = projectType.equals("Game") ? selectedGameType : selectedOrientation;

            Project project = new Project(name, pkg, projectType, detail, System.currentTimeMillis());
            ProjectStore.addProject(this, project);

            Toast.makeText(this, projectType + " '" + name + "' created!", Toast.LENGTH_LONG).show();

            Intent result = new Intent();
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void selectOrientation(LinearLayout selected, String orientation) {
        for (LinearLayout card : orientCards) card.setSelected(card == selected);
        selectedOrientation = orientation;
    }

    private void selectGameType(LinearLayout selected, String type) {
        for (LinearLayout card : gameTypeCards) card.setSelected(card == selected);
        selectedGameType = type;
    }
}
