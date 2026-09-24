package com.aalamstudio.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FrameLayout panelHome;
    private FrameLayout panelProjects;
    private LinearLayout panelPlaceholder;
    private TextView placeholderTitle;
    private LinearLayout[] navItems;

    private ScrollView sidebarScroll;
    private LinearLayout navListContainer;
    private TextView toggleSidebar;
    private TextView sidebarTitle;
    private boolean sidebarExpanded = true;

    private LinearLayout homeContent;

    private float scaleFactor = 1.0f;
    private float translateX = 0f, translateY = 0f;
    private float lastTouchX, lastTouchY;
    private ScaleGestureDetector scaleDetector;

    private TextView statTotalProjects, statGames, statApps, statAssets;
    private LinearLayout recentProjectsContainer;
    private TextView emptyProjectsText;

    private LinearLayout projectsListContainer;
    private TextView emptyProjectsListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        panelHome = findViewById(R.id.panelHome);
        panelProjects = findViewById(R.id.panelProjects);
        panelPlaceholder = findViewById(R.id.panelPlaceholder);
        placeholderTitle = findViewById(R.id.placeholderTitle);
        homeContent = findViewById(R.id.homeContent);

        statTotalProjects = findViewById(R.id.statTotalProjects);
        statGames = findViewById(R.id.statGames);
        statApps = findViewById(R.id.statApps);
        statAssets = findViewById(R.id.statAssets);
        recentProjectsContainer = findViewById(R.id.recentProjectsContainer);
        emptyProjectsText = findViewById(R.id.emptyProjectsText);

        projectsListContainer = findViewById(R.id.projectsListContainer);
        emptyProjectsListText = findViewById(R.id.emptyProjectsListText);

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
        sidebarTitle = findViewById(R.id.sidebarTitle);

        toggleSidebar.setOnClickListener(v -> {
            sidebarExpanded = !sidebarExpanded;
            navListContainer.setVisibility(sidebarExpanded ? View.VISIBLE : View.GONE);
            sidebarTitle.setVisibility(sidebarExpanded ? View.VISIBLE : View.GONE);
            toggleSidebar.setText(sidebarExpanded ? "▾" : "▸");

            ViewGroup.LayoutParams params = sidebarScroll.getLayoutParams();
            params.width = dp(sidebarExpanded ? 180 : 50);
            sidebarScroll.setLayoutParams(params);
        });

        navHome.setOnClickListener(v -> selectNav(navHome, () -> showPanel(panelHome, null)));
        navProjects.setOnClickListener(v -> selectNav(navProjects, () -> {
            showPanel(panelProjects, null);
            refreshProjectsList();
        }));
        navTemplates.setOnClickListener(v -> selectNav(navTemplates, () -> showPanel(panelPlaceholder, "Templates")));
        navAssets.setOnClickListener(v -> selectNav(navAssets, () -> showPanel(panelPlaceholder, "Assets")));
        navTutorials.setOnClickListener(v -> selectNav(navTutorials, () -> showPanel(panelPlaceholder, "Tutorials")));
        navCommunity.setOnClickListener(v -> selectNav(navCommunity, () -> showPanel(panelPlaceholder, "Community")));
        navTools.setOnClickListener(v -> selectNav(navTools, () -> showPanel(panelPlaceholder, "Tools")));
        navSettings.setOnClickListener(v -> selectNav(navSettings, () -> showPanel(panelPlaceholder, "Settings")));

        selectNav(navHome, () -> showPanel(panelHome, null));

        Button btnCreateApp = findViewById(R.id.btnCreateApp);
        Button btnCreateGame = findViewById(R.id.btnCreateGame);
        Button btnNewProject = findViewById(R.id.btnNewProject);

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

        btnNewProject.setOnClickListener(v -> {
            Intent intent = new Intent(this, NewProjectActivity.class);
            intent.putExtra(NewProjectActivity.EXTRA_PROJECT_TYPE, "App");
            startActivity(intent);
        });

        setupPinchAndPan();
    }

    private void setupPinchAndPan() {
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float prevScale = scaleFactor;
                        scaleFactor *= detector.getScaleFactor();
                        scaleFactor = Math.max(0.6f, Math.min(scaleFactor, 3.0f));

                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        float ratio = scaleFactor / prevScale;

                        translateX = focusX - (focusX - translateX) * ratio;
                        translateY = focusY - (focusY - translateY) * ratio;

                        applyTransform();
                        return true;
                    }
                });

        panelHome.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress()) {
                        float x = event.getX();
                        float y = event.getY();
                        translateX += (x - lastTouchX);
                        translateY += (y - lastTouchY);
                        applyTransform();
                        lastTouchX = x;
                        lastTouchY = y;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
            }
            return true;
        });
    }

    private void applyTransform() {
        homeContent.setScaleX(scaleFactor);
        homeContent.setScaleY(scaleFactor);
        homeContent.setTranslationX(translateX);
        homeContent.setTranslationY(translateY);
        homeContent.setPivotX(0);
        homeContent.setPivotY(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
        if (panelProjects.getVisibility() == View.VISIBLE) {
            refreshProjectsList();
        }
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

    private void refreshProjectsList() {
        List<Project> all = ProjectStore.getRecentProjects(this, 1000);

        projectsListContainer.removeAllViews();

        if (all.isEmpty()) {
            projectsListContainer.addView(emptyProjectsListText);
        } else {
            for (Project p : all) {
                projectsListContainer.addView(buildFullProjectRow(p));
            }
        }
    }

    private View buildFullProjectRow(Project p) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProjectEditorActivity.class);
            intent.putExtra(ProjectEditorActivity.EXTRA_PROJECT_NAME, p.name);
            intent.putExtra(ProjectEditorActivity.EXTRA_PROJECT_TYPE, p.type);
            intent.putExtra(ProjectEditorActivity.EXTRA_PROJECT_LANGUAGE, p.language);
            startActivity(intent);
         });

        LinearLayout info = new LinearLayout(this);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(infoParams);
        info.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(p.name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(14);
        name.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView meta = new TextView(this);
        meta.setText(p.type + " • " + p.detail + " • " + timeAgo(p.timestamp));
        meta.setTextColor(0xFF999999);
        meta.setTextSize(11);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(2);
        meta.setLayoutParams(metaParams);

        TextView pkg = new TextView(this);
        pkg.setText(p.packageName);
        pkg.setTextColor(0xFF666666);
        pkg.setTextSize(10);
        LinearLayout.LayoutParams pkgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pkgParams.topMargin = dp(2);
        pkg.setLayoutParams(pkgParams);

        info.addView(name);
        info.addView(meta);
        info.addView(pkg);

        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("Delete");
        deleteBtn.setTextColor(0xFFFF6B6B);
        deleteBtn.setTextSize(12);
        deleteBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        deleteBtn.setOnClickListener(v -> {
            ProjectStore.deleteProject(this, p.timestamp);
            refreshProjectsList();
            refreshDashboard();
        });

        row.addView(info);
        row.addView(deleteBtn);

        return row;
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
        panelProjects.setVisibility(View.GONE);
        panelPlaceholder.setVisibility(View.GONE);

        if (panelToShow == panelHome) {
            panelHome.setVisibility(View.VISIBLE);
        } else if (panelToShow == panelProjects) {
            panelProjects.setVisibility(View.VISIBLE);
        } else {
            placeholderTitle.setText(placeholderText);
            panelPlaceholder.setVisibility(View.VISIBLE);
        }
    }
}
