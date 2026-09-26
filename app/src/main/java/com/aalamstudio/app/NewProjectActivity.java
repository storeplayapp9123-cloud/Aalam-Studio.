package com.aalamstudio.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NewProjectActivity extends AppCompatActivity {

    public static final String EXTRA_PROJECT_TYPE = "project_type";

    private String projectType = "App";
    private String selectedOrientation = "Portrait";
    private String selectedGameType = "2D";
    private String selectedGameOrientation = "Portrait";
    private String selectedPlatform = "Android";
    private String selectedAppLanguage = "Java";
    private String selectedGameLanguage = "Java";

    private Set<String> selectedCustomPlatforms = new LinkedHashSet<>();
    private Set<String> customLanguages = new LinkedHashSet<>();
    private Set<String> appCustomLanguages = new LinkedHashSet<>();
    private Set<String> gameCustomPlatformLanguages = new LinkedHashSet<>();

    private final String[] PLATFORMS = {"Android", "Windows", "macOS", "Linux", "iOS"};
    private final String[] ANDROID_GAME_LANGUAGES = {
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

    private LinearLayout platformAndroid, platformWindows, platformMac, platformLinux, platformIos, platformCustom;
    private LinearLayout[] platformCards;
    private LinearLayout customPlatformContainer, customPlatformCheckboxContainer;

    private LinearLayout appLanguageContainer, appLanguageGrid;
    private LinearLayout[] appLangCards;
    private LinearLayout appLanguageCustomContainer, appLanguageCustomCheckboxContainer;

    private LinearLayout gameLanguageGrid;
    private LinearLayout[] gameLangCards;
    private LinearLayout customLanguageContainer, customLanguageCheckboxContainer;
    private LinearLayout gameLanguageCustomPlatformContainer, gameLanguageCustomPlatformCheckboxContainer;

    private LinearLayout panelAppOptions, panelGameOptions;
    private TextView locationPath, previewName, previewDetail;
    private EditText inputProjectName, inputPackageName;

    private interface CardClick {
        void onClick(LinearLayout card, String label);
    }

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

        platformAndroid = findViewById(R.id.platformAndroid);
        platformWindows = findViewById(R.id.platformWindows);
        platformMac = findViewById(R.id.platformMac);
        platformLinux = findViewById(R.id.platformLinux);
        platformIos = findViewById(R.id.platformIos);
        platformCustom = findViewById(R.id.platformCustom);
        platformCards = new LinearLayout[]{platformAndroid, platformWindows, platformMac, platformLinux, platformIos, platformCustom};

        customPlatformContainer = findViewById(R.id.customPlatformContainer);
        customPlatformCheckboxContainer = findViewById(R.id.customPlatformCheckboxContainer);

        appLanguageContainer = findViewById(R.id.appLanguageContainer);
        appLanguageGrid = findViewById(R.id.appLanguageGrid);
        appLanguageCustomContainer = findViewById(R.id.appLanguageCustomContainer);
        appLanguageCustomCheckboxContainer = findViewById(R.id.appLanguageCustomCheckboxContainer);

        gameLanguageGrid = findViewById(R.id.gameLanguageGrid);
        customLanguageContainer = findViewById(R.id.customLanguageContainer);
        customLanguageCheckboxContainer = findViewById(R.id.customLanguageCheckboxContainer);
        gameLanguageCustomPlatformContainer = findViewById(R.id.gameLanguageCustomPlatformContainer);
        gameLanguageCustomPlatformCheckboxContainer = findViewById(R.id.gameLanguageCustomPlatformCheckboxContainer);

        platformAndroid.setOnClickListener(v -> selectPlatform(platformAndroid, "Android"));
        platformWindows.setOnClickListener(v -> selectPlatform(platformWindows, "Windows"));
        platformMac.setOnClickListener(v -> selectPlatform(platformMac, "macOS"));
        platformLinux.setOnClickListener(v -> selectPlatform(platformLinux, "Linux"));
        platformIos.setOnClickListener(v -> selectPlatform(platformIos, "iOS"));
        platformCustom.setOnClickListener(v -> selectPlatform(platformCustom, "Custom"));

        setupCustomPlatformCheckboxes();
        setupAndroidCustomLanguageCheckboxes();

        selectPlatform(platformAndroid, "Android");

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

            String platformText = selectedPlatform.equals("Custom") ? customPlatformsText() : selectedPlatform;

            String detail;
            if (projectType.equals("Game")) {
                String orientation = selectedGameType.equals("3D") ? "Landscape" : selectedGameOrientation;
                String lang = resolveGameLanguageText();
                detail = selectedGameType + " - " + orientation + " - " + platformText + " - " + lang;
            } else {
                String lang = resolveAppLanguageText();
                detail = selectedOrientation + " - " + platformText + " - " + lang;
            }

            String finalLanguage = projectType.equals("Game") ? resolveGameLanguageText() : resolveAppLanguageText();
            Project project = new Project(name, pkg, projectType, detail, finalLanguage, platformText, System.currentTimeMillis());
            ProjectStore.addProject(this, project);

            Toast.makeText(this, projectType + " '" + name + "' created!", Toast.LENGTH_LONG).show();

            setResult(RESULT_OK);
            finish();
        });

        selectProjectType(initialType.equals("Game") ? typeGame : typeApp, initialType);
    }

    // ===== Platform selection =====

    private void selectPlatform(LinearLayout selected, String platform) {
        for (LinearLayout card : platformCards) card.setSelected(card == selected);
        selectedPlatform = platform;

        boolean isCustom = platform.equals("Custom");
        customPlatformContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);

        appLanguageContainer.setVisibility(isCustom ? View.GONE : View.VISIBLE);
        appLanguageCustomContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);

        gameLanguageGrid.setVisibility(isCustom ? View.GONE : View.VISIBLE);
        customLanguageContainer.setVisibility(View.GONE);
        gameLanguageCustomPlatformContainer.setVisibility(isCustom ? View.VISIBLE : View.GONE);

        if (!isCustom) {
            buildAppLanguageGrid();
            buildGameLanguageGrid();
        } else {
            rebuildCustomPlatformLanguageLists();
        }

        updatePreview();
    }

    private void setupCustomPlatformCheckboxes() {
        for (String platform : PLATFORMS) {
            CheckBox cb = new CheckBox(this);
            cb.setText(platform);
            cb.setTextColor(0xFFCCCCCC);
            cb.setTextSize(10);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedCustomPlatforms.add(platform);
                else selectedCustomPlatforms.remove(platform);
                rebuildCustomPlatformLanguageLists();
                updatePreview();
            });
            customPlatformCheckboxContainer.addView(cb);
        }
    }

    private String customPlatformsText() {
        if (selectedCustomPlatforms.isEmpty()) return "Custom (no platform selected)";
        return String.join(" + ", new ArrayList<>(selectedCustomPlatforms));
    }

    private void rebuildCustomPlatformLanguageLists() {
        appLanguageCustomCheckboxContainer.removeAllViews();
        appCustomLanguages.clear();
        Set<String> appUnion = new LinkedHashSet<>();
        for (String platform : selectedCustomPlatforms) appUnion.addAll(getAppLanguages(platform));
        for (String lang : appUnion) {
            CheckBox cb = new CheckBox(this);
            cb.setText(lang);
            cb.setTextColor(0xFFCCCCCC);
            cb.setTextSize(10);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) appCustomLanguages.add(lang);
                else appCustomLanguages.remove(lang);
                updatePreview();
            });
            appLanguageCustomCheckboxContainer.addView(cb);
        }

        gameLanguageCustomPlatformCheckboxContainer.removeAllViews();
        gameCustomPlatformLanguages.clear();
        Set<String> gameUnion = new LinkedHashSet<>();
        for (String platform : selectedCustomPlatforms) gameUnion.addAll(getGameLanguages(platform));
        for (String lang : gameUnion) {
            CheckBox cb = new CheckBox(this);
            cb.setText(lang);
            cb.setTextColor(0xFFCCCCCC);
            cb.setTextSize(10);
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) gameCustomPlatformLanguages.add(lang);
                else gameCustomPlatformLanguages.remove(lang);
                updatePreview();
            });
            gameLanguageCustomPlatformCheckboxContainer.addView(cb);
        }
    }

    private String resolveAppLanguageText() {
        if (selectedPlatform.equals("Custom")) {
            if (appCustomLanguages.isEmpty()) return "Custom (none selected)";
            return String.join(" + ", new ArrayList<>(appCustomLanguages));
        }
        return selectedAppLanguage;
    }

    private String resolveGameLanguageText() {
        if (selectedPlatform.equals("Custom")) {
            if (gameCustomPlatformLanguages.isEmpty()) return "Custom (none selected)";
            return String.join(" + ", new ArrayList<>(gameCustomPlatformLanguages));
        }
        return selectedGameLanguage.equals("Custom") ? customLanguagesText() : selectedGameLanguage;
    }

    // ===== Per-platform language lists =====

    private List<String> getAppLanguages(String platform) {
        switch (platform) {
            case "Windows": return Arrays.asList("C#", "C++", "Java", "Python");
            case "macOS": return Arrays.asList("Swift", "Objective-C", "C++");
            case "Linux": return Arrays.asList("C", "Python", "C++", "Java");
            case "iOS": return Arrays.asList("Swift", "Objective-C", "Dart");
            default: return Arrays.asList("Java", "Kotlin", "Dual");
        }
    }

    private List<String> getGameLanguages(String platform) {
        switch (platform) {
            case "Windows": return Arrays.asList("C++", "C#", "Python");
            case "macOS": return Arrays.asList("Swift", "C++", "C#");
            case "Linux": return Arrays.asList("C++", "C#", "Python");
            case "iOS": return Arrays.asList("Swift", "C#", "Dart");
            default: return Arrays.asList(ANDROID_GAME_LANGUAGES);
        }
    }

    // ===== App language grid (single platform) =====

    private void buildAppLanguageGrid() {
        appLanguageGrid.removeAllViews();
        List<String> langs = getAppLanguages(selectedPlatform);

        appLangCards = new LinearLayout[langs.size()];
        LinearLayout row = null;
        for (int i = 0; i < langs.size(); i++) {
            if (i % 3 == 0) {
                row = newGridRow();
                appLanguageGrid.addView(row);
            }
            String lang = langs.get(i);
            LinearLayout card = buildLangCard(lang, (c, l) -> {
                selectAppLanguage(c, l);
                updatePreview();
            });
            row.addView(card);
            appLangCards[i] = card;
        }

        selectAppLanguage(appLangCards[0], langs.get(0));
    }

    private void selectAppLanguage(LinearLayout selected, String lang) {
        if (appLangCards != null) {
            for (LinearLayout card : appLangCards) card.setSelected(card == selected);
        }
        selectedAppLanguage = lang;
    }

    // ===== Game language grid (single platform) =====

    private void buildGameLanguageGrid() {
        gameLanguageGrid.removeAllViews();
        customLanguageContainer.setVisibility(View.GONE);

        List<String> langs = getGameLanguages(selectedPlatform);
        boolean showCustomCard = selectedPlatform.equals("Android");
        String defaultLang = selectedPlatform.equals("Android") ? "Java" : langs.get(0);

        int total = langs.size() + (showCustomCard ? 1 : 0);
        gameLangCards = new LinearLayout[total];

        LinearLayout row = null;
        int defaultIndex = 0;
        for (int i = 0; i < langs.size(); i++) {
            if (i % 3 == 0) {
                row = newGridRow();
                gameLanguageGrid.addView(row);
            }
            String lang = langs.get(i);
            if (lang.equals(defaultLang)) defaultIndex = i;

            LinearLayout card = buildLangCard(lang, (c, l) -> {
                selectGameLanguage(c, l);
                customLanguageContainer.setVisibility(View.GONE);
                updatePreview();
            });
            row.addView(card);
            gameLangCards[i] = card;
        }

        if (showCustomCard) {
            LinearLayout customRow = newGridRow();
            gameLanguageGrid.addView(customRow);
            LinearLayout customCard = buildLangCard("Custom", (c, l) -> {
                selectGameLanguage(c, l);
                customLanguageContainer.setVisibility(View.VISIBLE);
                updatePreview();
            });
            customRow.addView(customCard);
            gameLangCards[langs.size()] = customCard;
        }

        selectGameLanguage(gameLangCards[defaultIndex], defaultLang);
    }

    private void selectGameLanguage(LinearLayout selected, String lang) {
        if (gameLangCards != null) {
            for (LinearLayout card : gameLangCards) {
                if (card != null) card.setSelected(card == selected);
            }
        }
        selectedGameLanguage = lang;
    }

    private void setupAndroidCustomLanguageCheckboxes() {
        for (String lang : ANDROID_GAME_LANGUAGES) {
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

    // ===== Shared card builder =====

    private LinearLayout newGridRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(6);
        row.setLayoutParams(rowParams);
        return row;
    }

    private LinearLayout buildLangCard(String label, CardClick clickHandler) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1f);
        params.setMarginEnd(dp(6));
        card.setLayoutParams(params);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundResource(R.drawable.bg_type_card);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(10);
        card.addView(text);

        card.setOnClickListener(v -> clickHandler.onClick(card, label));

        return card;
    }

    // ===== Project type / orientation / etc =====

    private void selectProjectType(LinearLayout selected, String type) {
        for (LinearLayout card : typeCards) card.setSelected(card == selected);
        projectType = type;

        if (type.equals("Game")) {
            panelAppOptions.setVisibility(View.GONE);
            panelGameOptions.setVisibility(View.VISIBLE);
        } else {
            panelAppOptions.setVisibility(View.VISIBLE);
            panelGameOptions.setVisibility(View.GONE);
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

        String platformText = selectedPlatform.equals("Custom") ? customPlatformsText() : selectedPlatform;

        String detail;
        if (projectType.equals("Game")) {
            String orientation = selectedGameType.equals("3D") ? "Landscape" : selectedGameOrientation;
            String lang = resolveGameLanguageText();
            detail = projectType + " • " + selectedGameType + " • " + orientation + " • " + platformText + " • " + lang;
        } else {
            String lang = resolveAppLanguageText();
            detail = projectType + " • " + selectedOrientation + " • " + platformText + " • " + lang;
        }
        previewDetail.setText(detail);
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
