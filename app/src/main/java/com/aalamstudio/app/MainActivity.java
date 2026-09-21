package com.aalamstudio.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ScrollView panelHome;
    private LinearLayout panelPlaceholder;
    private TextView placeholderTitle;
    private LinearLayout[] navItems;

    private ScrollView sidebarScroll;
    private LinearLayout navListContainer;
    private TextView toggleSidebar;
    private boolean sidebarExpanded = true;

    private LinearLayout homeContent;
    private float currentScale = 1.0f;

    private TextView statTotalProjects, statGames, statApps, statAssets;
    private LinearLayout recentProjectsContainer;
    private TextView emptyProjectsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        panelHome = findViewById(R.id.panelHome);
        panelPlaceholder = findViewById(R.id.panelPlaceholder);
        placeholderTitle = findViewById(R.id.placeholderTitle);
        homeContent = findViewById(R.id.homeContent);

        statTotalProjects = findViewById(R.id.statTotalProjects);
        statGames = findViewById(R.id.statGames);
        statApps = findViewById(R.id.statApps);
        statAssets = findViewById(R.id.statAssets);
        recentProjectsContainer = findViewById(R.id.recentProjectsContainer);
        emptyProjectsText = findViewById(R.id.emptyProjectsText);

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

        sidebarScroll = findViewById(R.id.sidebarScroll);
        navListContainer = findViewById(R.id.navListContainer);
        toggleSidebar = findViewById(R.id.toggleSidebar);

        toggleSidebar.setOnClickListener(v -> {
            sidebarExpanded = !sidebarExpanded;
            navListContainer.setVisibility(sidebarExpanded ? View.VISIBLE : View.GONE);
            toggleSidebar.setText(sidebarExpanded ? "▾" : "▸");

            ViewGroup.LayoutParams params = sidebarScroll.getLayoutParams();
            params.width = dp(sidebarExpanded ? 180 : 60);
            sidebarScroll.setLayoutParams(params);
        });

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

        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        currentScale *= detector.getScaleFactor();
                        currentScale = Math.max(0.6f, Math.min(currentScale, 2.5f));
                        applyZoom();
                        return true;
                    }
                });

        panelHome.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            return false;
        });
    }

    private void applyZoom() {
        homeContent.setScaleX(currentScale);
        homeContent.setScaleY(currentScale);
        homeContent.setPivotX(0);
        homeContent.setPivotY(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void refreshDashboard() {
        int totalApps = ProjectStore.countByType(this, "App");
        int totalGames = ProjectStore.countByType(this, "Game");
        int total = totalApps + totalGames;

        statTotalProjects.setText(String.valueOf(total));
        statGames.setText(String.valueOf(totalGames));
        statApps.setText(String.valueOf(totalApps));
        statAssets.setText("0");

        List<Project> recent = ProjectStore.getRecentProjects(this, 6);

        recentProjectsContainer.removeAllViews();

        if (recent.isEmpty()) {
            recentProjectsContainer.addView(emptyProjectsText);
        } else {
            for (Project p : recent) {
                recentProjectsContainer.addView(buildProjectCard(p));
            }
        }
    }

    private View buildProjectCard(Project p) {
        LinearLayout card = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(12));
        card.setLayoutParams(params);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView name = new TextView(this);
        name.setText(p.name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(14);
        name.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView type = new TextView(this);
        type.setText(p.type + " Project");
        type.setTextColor(0xFF999999);
        type.setTextSize(11);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        typeParams.topMargin = dp(2);
        type.setLayoutParams(typeParams);

        TextView updated = new TextView(this);
        updated.setText(timeAgo(p.timestamp));
        updated.setTextColor(0xFF999999);
        updated.setTextSize(11);

        card.addView(name);
        card.addView(type);
        card.addView(updated);

        return card;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String timeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / (60 * 1000);
        long hours = minutes / 60;
        long days = hours / 24;

        if (minutes < 1) return "Just now";
        if (minutes < 60) return "Updated " + minutes + "m ago";
        if (hours < 24) return "Updated " + hours + "h ago";
        return "Updated " + days + "d ago";
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
