package com.aalamstudio.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Full screen "run" of the project's launcher layout inside a phone frame.
 * The phone frame is always portrait. If the app is landscape it is drawn rotated inside it.
 * No toolbar: the phone's Back button closes the preview.
 *
 * AndroidManifest.xml needs (inside <application>):
 *   <activity android:name=".AppPreviewActivity"
 *       android:screenOrientation="portrait"
 *       android:configChanges="orientation|screenSize|keyboardHidden"
 *       android:theme="@android:style/Theme.DeviceDefault.NoActionBar" />
 */
public class AppPreviewActivity extends Activity {

    public static final String EXTRA_LAYOUT_PATH = "layout_path";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_LANDSCAPE = "landscape";

    private FrameLayout root;
    private String xml = "";
    private String title = "My Application";
    private boolean landscape = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        String path = getIntent().getStringExtra(EXTRA_LAYOUT_PATH);
        String t = getIntent().getStringExtra(EXTRA_TITLE);
        if (t != null) title = t;
        landscape = getIntent().getBooleanExtra(EXTRA_LANDSCAPE, false);

        try {
            if (path == null) throw new IOException("layout path nahi mila");
            xml = read(new File(path));
        } catch (IOException e) {
            Toast.makeText(this, "Layout nahi khuli: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F0F0F);
        setContentView(root);

        // build once the real screen size is known
        root.post(new Runnable() {
            @Override public void run() { showPhone(); }
        });
    }

    private void showPhone() {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) {
            w = getResources().getDisplayMetrics().widthPixels;
            h = getResources().getDisplayMetrics().heightPixels;
        }
        LivePreviewPanel helper = new LivePreviewPanel(this);
        View phone = helper.buildRunnerView(xml, title, landscape, w, h);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        root.removeAllViews();
        root.addView(phone, lp);
    }

    private static String read(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), "UTF-8");
        }
    }
}
