package com.aalamstudio.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private String selectedGameOrientation = "Portrait";

    private LinearLayout orientPortrait, orientLandscape;
    private LinearLayout game2D, game3D;
    private LinearLayout gameOrientPortrait, gameOrientLandscape;
    private LinearLayout gameOrientationContainer;
    private TextView gameOrientationLockedText;
    private LinearLayout[] orientCards;
    private LinearLayout[] gameTypeCards;
    private LinearLayout[] gameOrientCards;

    private TextView locationPath;

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
        locationPath = findViewById(R.id.locationPath);

        if (projectType.equals("Game")) {
            screenTitle.setText("New Game Project");
            panelAppOptions.setVisibility(View.GONE);
            panelGameOptions.setVisibility(View.VISIBLE);
            updateLocationPath("Games", "");
        } else {
            screenTitle.setText("New App Project");
            panelAppOptions.setVisibility(View.VISIBLE);
            panelGameOptions.setVisibility(View.GONE);
            updateLocationPath("Apps", "");
        }

        inputProjectName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String folder = projectType.equals("Game") ? "Games" : "Apps";
                updateLocationPath(folder, s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        orientPortrait = findViewById(R.id.orientPortrait);
        orientLandscape = findViewById(R.id.orientLandscape);
        orientCards = new LinearLayout[]{orientPortrait, orientLandscape};

        game2D = findViewById(R.id.game2D);
        game3D = findViewById(R.id.game3D);
        gameTypeCards = new LinearLayout[]{game2D, game3D};

        gameOrientPortrait = findViewById(R.id.gameOrientPortrait);
        gameOrientLandscape = findViewById(R.id.gameOrientLandscape);
        gameOrientCards = new LinearLayout[]{gameOrientPortrait, gameOrientLandscape};
        gameOrientationContainer = findViewById(R.id.gameOrientationContainer);
        gameOrientationLockedText = findViewById(R.id.gameOrientationLockedText);

        orientPortrait.setOnClickListener(v -> selectOrientation(orientPortrait, "Portrait"));
        orientLandscape.setOnClickListener(v -> selectOrientation(orientLandscape, "Landscape"));
        selectOrientation(orientPortrait, "Portrait");

        game2D.setOnClickListener(v -> selectGameType(game2D, "2D"));
        game3D.setOnClickListener(v -> selectGameType(game3D, "3D"));
        selectGameType(game2D, "2D");

        gameOrientPortrait.setOnClickListener(v -> selectGameOrientation(gameOrientPortrait, "Portrait"));
        gameOrientLandscape.setOnClickListener(v -> selectGameOrientation(gameOrientLandscape, "Landscape"));
        selectGameOrientation(gameOrientPortrait, "Portrait");

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

            String detail;
            if (projectType.equals("Game")) {
                String orientation = selectedGameType.equals("3D") ? "Landscape" : selectedGameOrientation;
                detail = selectedGameType + " - " + orientation;
            } else {
                detail = selectedOrientation;
            }

            Project project = new Project(name, pkg, projectType, detail, System.currentTimeMillis());
            ProjectStore.addProject(this, project);

            Toast.makeText(this, projectType + " '" + name + "' created!", Toast.LENGTH_LONG).show();

            setResult(RESULT_OK);
            finish();
        });
    }

    private void updateLocationPath(String folder, String projectName) {
        String safeName = projectName.trim().isEmpty()
                ? "your-project-name"
                : projectName.trim().replace(" ", "_");
        locationPath.setText("/AalamStudio/Projects/" + folder + "/" + safeName);
    }

    private void selectOrientation(LinearLayout selected, String orientation) {
        for (LinearLayout card : orientCards) card.setSelected(card == selected);
        selectedOrientation = orientation;
    }

    private void selectGameType(LinearLayout selected, String type) {
        for (LinearLayout card : gameTypeCards) card.setSelected(card == selected);
        selectedGameType = type;

        if (type.equals("3D")) {
            gameOrientationContainer.setVisibility(View.GONE);
            gameOrientationLockedText.setVisibility(View.VISIBLE);
        } else {
            gameOrientationContainer.setVisibility(View.VISIBLE);
            gameOrientationLockedText.setVisibility(View.GONE);
        }
    }

    private void selectGameOrientation(LinearLayout selected, String orientation) {
        for (LinearLayout card : gameOrientCards) card.setSelected(card == selected);
        selectedGameOrientation = orientation;
    }
}
