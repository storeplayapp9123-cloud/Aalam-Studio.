package com.aalamstudio.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class NewProjectActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_TYPE = "project_type";

    private String projectType = "App";
    private String selectedOrientation = "Portrait";
    private String selectedGameType = "2D";
    private String selectedGameOrientation = "Portrait";
    private String selectedLanguage = "Java";
    private String selectedGameLanguage = "Java";
    private Set<String> customLanguages = new LinkedHashSet<>();

    private final String[] GAME_LANGUAGES = {
            "C#", "C++", "C", "Java", "Kotlin", "Swift",
            "Lua", "JavaScript", "Python", "Go", "HLSL / GLSL"
    };

    private LinearLayout typeGame, typeApp, type3D, typeTemplate;
    private LinearLayout[] typeCards;

    private LinearLayout orientPortrait, orientLandscape;
    private LinearLayout game2D, game3D;
    private LinearLayout gameOrientPortrait, gameOrientLandscape;
    private LinearLayout gameOrientationContainer;
    private TextView gameOrientationLockedText;
    private LinearLayout[] orientCards;
    private LinearLayout[] gameTypeCards;
    private LinearLayout[] gameOrientCards;

    private LinearLayout appLanguageContainer;
    private LinearLayout langJava, langKotlin, langDual;
    private LinearLayout[] langCards;

    private LinearLayout gameLanguageGrid;
    private LinearLayout[] gameLanguageCards;
    private LinearLayout customLanguageContainer;
    private LinearLayout customLanguageCheckboxContainer;

    private LinearLayout panelAppOptions, panelGameOptions;
    private TextView locationPath, previewName, previewDetail;
    private EditText inputProjectName, inputPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_project);

        String initialType = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        if (initialType == null) initialType = "App";
        projectType = initialType;

        panelAppOptions = findViewById(R.id.panelAppOptions);
        panelGameOptions = findViewById(R.id.panelGameOptions);
        inputProjectName = findViewById(R.id.inputProjectName);
        inputPackageName = findViewById(R.id.inputPackageName);
        locationPath = findViewById(R.id.locationPath);
        previewName = findViewById(R.id.previewName);
        previewDetail = findViewById(R.id.previewDetail);
        appLanguageContainer = findViewById(R.id.appLanguageContainer);

        Button btnCancel = findViewById(R.id.btnCancel);
        Button btnCreateProject = findViewById(R.id.btnCreateProject);

        typeGame = findViewById(R.id.typeGame);
        typeApp = findViewById(R.id.typeApp);
        type3D = findViewById(R.id.type3D);
        typeTemplate = findViewById(R.id.typeTemplate);
        typeCards = new LinearLayout[]{typeGame, typeApp, type3D, typeTemplate};

        typeGame.setOnClickListener(v -> selectProjectType(typeGame, "Game"));
        typeApp.setOnClickListener(v -> selectProjectType(typeApp, "App"));
        type3D.setOnClickListener(v -> selectProjectType(type3D, "3D Experience"));
        typeTemplate.setOnClickListener(v -> selectProjectType(typeTemplate, "Template"));

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

        langJava = findViewById(R.id.langJava);
        langKotlin = findViewById(R.id.langKotlin);
        langDual = findViewById(R.id.langDual);
        langCards = new LinearLayout[]{langJava, langKotlin, langDual};

        langJava.setOnClickListener(v -> selectLanguage(langJava, "Java"));
        langKotlin.setOnClickListener(v -> selectLanguage(langKotlin, "Kotlin"));
        langDual.setOnClickListener(v -> selectLanguage(langDual, "Dual"));
        selectLanguage(langJava, "Java");

        orientPortrait.setOnClickListener(v -> {
            selectOrientation(orientPortrait, "Portrait");
            updatePreview();
        });
        orientLandscape.setOnClickListener(v -> {
            selectOrientation(orientLandscape, "Landscape");
            updatePreview();
        });
        selectOrientation(orientPortrait, "Portrait");

        game2D.setOnClickListener(v -> {
            selectGameType(game2D, "2D");
            updatePreview();
        });
        game3D.setOnClickListener(v -> {
            selectGameType(game3D, "3D");
            updatePreview();
        });
        selectGameType(game2D, "2D");

        gameOrientPortrait.setOnClickListener(v -> {
            selectGameOrientation(gameOrientPortrait, "Portrait");
            updatePreview();
        });
        gameOrientLandscape.setOnClickListener(v -> {
            selectGameOrientation(gameOrientLandscape, "Landscape");
            updatePreview();
        });
        selectGameOrientation(gameOrientPortrait, "Portrait");

        gameLanguageGrid = findViewById(R.id.gameLanguageGrid);
        customLanguageContainer = findViewById(R.id.customLanguageContainer);
        customLanguageCheckboxContainer = findViewById(R.id.customLanguageCheckboxContainer);
        setupGameLanguageGrid();

        inputProjectName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLocationPath();
                updatePreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

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
                String lang = selectedGameLanguage.equals("Custom") ? customLanguagesText() : selectedGameLanguage;
                detail = selectedGameType + " - " + orientation + " - " + lang;
            } else {
                detail = selectedOrientation + " - " + selectedLanguage;
            }

            Project project = new Project(name, pkg, projectType, detail, System.currentTimeMillis());
            ProjectStore.addProject(this, project);

            Toast.makeText(this, projectType + " '" + name + "' created!", Toast.LENGTH_LONG).show();

            setResult(RESULT_OK);
            finish();
        });

        selectProjectType(initialType.equals("Game") ? typeGame : typeApp, initialType);
    }

    private void setupGameLanguageGrid() {
        gameLanguageCards = new LinearLayout[GAME_LANGUAGES.length + 1];

        LinearLayout row = null;
        for (int i = 0; i < GAME_LANGUAGES.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(6);
                row.setLayoutParams(rowParams);
                gameLanguageGrid.addView(row);
            }

            String lang = GAME_LANGUAGES[i];
            LinearLayout card = buildLangCard(lang);
            row.addView(card);
            gameLanguageCards[i] = card;
        }

        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams customRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gameLanguageGrid.addView(customRow);

        LinearLayout customCard = buildLangCard("Custom");
        customRow.addView(customCard);
        gameLanguageCards[GAME_LANGUAGES.length] = customCard;

        setupCustomCheckboxes();

        selectGameLanguage(gameLanguageCards[3], "Java");
    }

    private LinearLayout buildLangCard(String label) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1f);
        params.setMarginEnd(dp(6));
        card.setLayoutParams(params);
        card.setGravity(android.view.Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_type_card);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(10);
        card.addView(text);

        card.setOnClickListener(v -> {
            if (label.equals("Custom")) {
                selectGameLanguage(card, "Custom");
                customLanguageContainer.setVisibility(View.VISIBLE);
            } else {
                selectGameLanguage(card, label);
                customLanguageContainer.setVisibility(View.GONE);
            }
            updatePreview();
        });

        return card;
    }

    private void setupCustomCheckboxes() {
        for (String lang : GAME_LANGUAGES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(lang);
            cb.setTextColor(0xFFCCCCCC);
            cb.setTextSize(10);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) customLanguages.add(lang);
                else customLanguages.remove(lang);
                updatePreview();
            });
            customLanguageCheckboxContainer.addView(cb);
        }
    }

    private String customLanguagesText() {
        if (customLanguages.isEmpty()) return "Custom (none selected)";
        return String.join(" + ", new ArrayList<>(customLanguages));
    }

    private void selectGameLanguage(LinearLayout selected, String lang) {
        for (LinearLayout card : gameLanguageCards) {
            if (card != null) card.setSelected(card == selected);
        }
        selectedGameLanguage = lang;
    }

    private void selectProjectType(LinearLayout selected, String type) {
        for (LinearLayout card : typeCards) card.setSelected(card == selected);
        projectType = type;

        if (type.equals("Game")) {
            panelAppOptions.setVisibility(View.GONE);
            panelGameOptions.setVisibility(View.VISIBLE);
            appLanguageContainer.setVisibility(View.GONE);
        } else {
            panelAppOptions.setVisibility(View.VISIBLE);
            panelGameOptions.setVisibility(View.GONE);
            appLanguageContainer.setVisibility(View.VISIBLE);
        }

        updateLocationPath();
        updatePreview();
    }

    private void updateLocationPath() {
        String name = inputProjectName.getText().toString().trim();
        String safeName = name.isEmpty() ? "your-project-name" : name.replace(" ", "_");
        String folder = projectType.equals("Game") ? "Games" : "Apps";
        locationPath.setText("/AalamStudio/Projects/" + folder + "/" + safeName);
    }

    private void updatePreview() {
        String name = inputProjectName.getText().toString().trim();
        previewName.setText(name.isEmpty() ? "My Project" : name);

        String detail;
        if (projectType.equals("Game")) {
            String orientation = selectedGameType.equals("3D") ? "Landscape" : selectedGameOrientation;
            String lang = selectedGameLanguage.equals("Custom") ? customLanguagesText() : selectedGameLanguage;
            detail = projectType + " • " + selectedGameType + " • " + orientation + " • " + lang;
        } else {
            detail = projectType + " • " + selectedOrientation + " • " + selectedLanguage;
        }
        previewDetail.setText(detail);
    }

    private void selectLanguage(LinearLayout selected, String lang) {
        for (LinearLayout card : langCards) card.setSelected(card == selected);
        selectedLanguage = lang;
        updatePreview();
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

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
