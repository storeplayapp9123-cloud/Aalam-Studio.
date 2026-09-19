package com.aalamstudio.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ScrollView panelHome;
    private LinearLayout panelPlaceholder;
    private TextView placeholderTitle;
    private LinearLayout[] navItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        panelHome = findViewById(R.id.panelHome);
        panelPlaceholder = findViewById(R.id.panelPlaceholder);
        placeholderTitle = findViewById(R.id.placeholderTitle);

        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navProjects = findViewById(R.id.navProjects);
        LinearLayout navTemplates = findViewById(R.id.navTemplates);
        LinearLayout navAssets = findViewById(R.id.navAssets);
        LinearLayout navTutorials = findViewById(R.id.navTutorials);
        LinearLayout navCommunity = findViewById(R.id.navCommunity);
        LinearLayout navTools = findViewById(R.id.navTools);
        LinearLayout navSettings = findViewById(R.id.navSettings);

        navItems = new LinearLayout[]{
                navHome, navProjects, navTemplates, navAssets,
                navTutorials, navCommunity, navTools, navSettings
        };

        navHome.setOnClickListener(v -> selectNav(navHome, () -> showPanel(panelHome, null)));
        navProjects.setOnClickListener(v -> selectNav(navProjects, () -> showPanel(panelPlaceholder, "Projects")));
        navTemplates.setOnClickListener(v -> selectNav(navTemplates, () -> showPanel(panelPlaceholder, "Templates")));
        navAssets.setOnClickListener(v -> selectNav(navAssets, () -> showPanel(panelPlaceholder, "Assets")));
        navTutorials.setOnClickListener(v -> selectNav(navTutorials, () -> showPanel(panelPlaceholder, "Tutorials")));
        navCommunity.setOnClickListener(v -> selectNav(navCommunity, () -> showPanel(panelPlaceholder, "Community")));
        navTools.setOnClickListener(v -> selectNav(navTools, () -> showPanel(panelPlaceholder, "Tools")));
        navSettings.setOnClickListener(v -> selectNav(navSettings, () -> showPanel(panelPlaceholder, "Settings")));

        selectNav(navHome, () -> showPanel(panelHome, null));

        Button btnCreateApp = findViewById(R.id.btnCreateApp);
        Button btnCreateGame = findViewById(R.id.btnCreateGame);

        btnCreateApp.setOnClickListener(v -> {
            Intent intent = new Intent(this, NewProjectActivity.class);
            intent.putExtra(NewProjectActivity.EXTRA_PROJECT_TYPE, "App");
            startActivity(intent);
        });

        btnCreateGame.setOnClickListener(v -> {
            Intent intent = new Intent(this, NewProjectActivity.class);
            intent.putExtra(NewProjectActivity.EXTRA_PROJECT_TYPE, "Game");
            startActivity(intent);
        });
    }

    private interface PanelAction {
        void run();
    }

    private void selectNav(LinearLayout item, PanelAction action) {
        for (LinearLayout nav : navItems) {
            nav.setSelected(nav == item);
        }
        action.run();
    }

    private void showPanel(View panelToShow, String placeholderText) {
        panelHome.setVisibility(View.GONE);
        panelPlaceholder.setVisibility(View.GONE);

        if (panelToShow == panelHome) {
            panelHome.setVisibility(View.VISIBLE);
        } else {
            placeholderTitle.setText(placeholderText);
            panelPlaceholder.setVisibility(View.VISIBLE);
        }
    }
}
