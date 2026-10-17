package com.aalamstudio.app;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.*;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Live Preview + Component Tree for Aalam Studio.
 * Usage:  panel.setXml(editorText);   // call on every text change (debounced inside)
 *         panel.showMessage("...");   // for non-XML files
 *         panel.setListener(line -> editor.goToLine(line));
 */
public class LivePreviewPanel extends LinearLayout {

    public interface Listener { void onNodeClicked(int line); }

    private static final int GOLD = 0xFFD4AF37;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static class Node {
        String tag;
        int line;
        Node parent;
        Map<String, String> attrs = new LinkedHashMap<>();
        List<Node> kids = new ArrayList<>();
    }

    /** One phone frame: bezel + the content area where the layout is drawn. */
    private static class Frame {
        FrameLayout bezel;
        FrameLayout content;
        TextView title;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final TextView errorBar;
    private final TextView orientBtn;
    private final LinearLayout frameHolder;
    private final LinearLayout treeBox;
    private Frame frame;
    private Listener listener;
    private String pending = "";
    private String lastXml = null;   // last good layout (re-drawn on rotate / full screen)
    private String lastMsg = null;
    private String title = "My Application";
    private boolean landscape = false;
    private boolean userOverride = false;

    private final Runnable renderTask = new Runnable() {
        @Override public void run() { render(pending); }
    };

    public LivePreviewPanel(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setPadding(px(12), px(12), px(12), px(12));

        // header: title + rotate button
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = label("Live Preview");
        head.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        orientBtn = new TextView(c);
        orientBtn.setTextColor(GOLD);
        orientBtn.setTextSize(13);
        orientBtn.setTypeface(null, Typeface.BOLD);
        orientBtn.setPadding(px(8), px(4), px(4), px(4));
        orientBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                userOverride = true;
                applyOrientation(!landscape);
            }
        });
        head.addView(orientBtn);
        addView(head);

        errorBar = new TextView(c);
        errorBar.setTextColor(0xFFFF6B6B);
        errorBar.setTextSize(11);
        errorBar.setVisibility(GONE);
        addView(errorBar);

        frameHolder = new LinearLayout(c);
        frameHolder.setOrientation(VERTICAL);
        frameHolder.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(MATCH, WRAP);
        fl.topMargin = px(8);
        fl.bottomMargin = px(12);
        addView(frameHolder, fl);

        addView(label("Component Tree"));
        treeBox = new LinearLayout(c);
        treeBox.setOrientation(VERTICAL);
        addView(treeBox, new LinearLayout.LayoutParams(MATCH, WRAP));

        applyOrientation(false);
    }

    // ---------- phone frame ----------

    /**
     * Builds a phone. The phone frame is ALWAYS portrait (360x740 dp).
     * land == false: the app fills the screen upright.
     * land == true : the app is laid out as a landscape app (692x360 dp) and rotated
     *                inside the portrait screen, like a landscape-locked app on a phone held upright.
     * Status bar and home bar are system UI and always stay portrait.
     */
    private Frame makeFrame(int innerW, boolean land, final Runnable onTap, String barTitle) {
        Context c = getContext();
        final int vw = px(360);
        final int vh = px(740);
        final float scale = (float) innerW / vw;
        final int innerH = Math.round(vh * scale);
        Frame f = new Frame();

        FrameLayout bezel = new FrameLayout(c);
        GradientDrawable bz = new GradientDrawable();
        bz.setColor(0xFF0B0B0B);
        bz.setCornerRadius(Math.max(px(12), Math.round(innerW * 0.17f)));
        bz.setStroke(px(2), 0xFF3A3A3A);
        bezel.setBackground(bz);
        int bp = Math.max(px(4), Math.round(innerW * 0.04f));
        bezel.setPadding(bp, bp, bp, bp);

        FrameLayout screenClip = new FrameLayout(c);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.WHITE);
        sbg.setCornerRadius(Math.max(px(8), Math.round(innerW * 0.12f)));
        screenClip.setBackground(sbg);
        screenClip.setClipToOutline(true);
        bezel.addView(screenClip, new FrameLayout.LayoutParams(innerW, innerH));

        LinearLayout virt = new LinearLayout(c);
        virt.setOrientation(VERTICAL);
        virt.setPivotX(0);
        virt.setPivotY(0);
        virt.setScaleX(scale);
        virt.setScaleY(scale);
        screenClip.addView(virt, new FrameLayout.LayoutParams(vw, vh));

        // status bar (system UI, always portrait)
        LinearLayout status = new LinearLayout(c);
        status.setOrientation(HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setBackgroundColor(0xFF4527A0);
        status.setPadding(px(14), 0, px(14), 0);
        TextView clock = new TextView(c);
        clock.setText("11:00");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(12);
        status.addView(clock, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView icons = new TextView(c);
        icons.setText("\u25BE \u25E2 \u25AE");
        icons.setTextColor(Color.WHITE);
        icons.setTextSize(11);
        status.addView(icons);
        virt.addView(status, new LinearLayout.LayoutParams(MATCH, px(26)));

        // app area: upright app, or the landscape app rotated 90 degrees inside it
        FrameLayout appArea = new FrameLayout(c);
        appArea.setBackgroundColor(Color.WHITE);
        appArea.setClipChildren(true);
        virt.addView(appArea, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout app = new LinearLayout(c);
        app.setOrientation(VERTICAL);
        FrameLayout.LayoutParams ap;
        if (land) {
            ap = new FrameLayout.LayoutParams(px(740 - 26 - 22), px(360)); // 692 x 360 dp landscape app
            ap.gravity = Gravity.CENTER;
            app.setRotation(90);
        } else {
            ap = new FrameLayout.LayoutParams(MATCH, MATCH);
        }
        appArea.addView(app, ap);

        // app bar
        TextView bar = new TextView(c);
        bar.setText(barTitle);
        bar.setTextColor(Color.WHITE);
        bar.setTextSize(20);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF5E35B1);
        bar.setPadding(px(16), 0, px(16), 0);
        app.addView(bar, new LinearLayout.LayoutParams(MATCH, px(land ? 48 : 56)));

        // content area: the rendered layout goes here
        FrameLayout content = new FrameLayout(c);
        content.setBackgroundColor(Color.WHITE);
        content.setClipChildren(true);
        app.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // home indicator (system UI, always portrait)
        FrameLayout nav = new FrameLayout(c);
        nav.setBackgroundColor(Color.WHITE);
        View homeBar = new View(c);
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xFF222222);
        hb.setCornerRadius(px(3));
        homeBar.setBackground(hb);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(px(110), px(4));
        hp.gravity = Gravity.CENTER;
        nav.addView(homeBar, hp);
        virt.addView(nav, new LinearLayout.LayoutParams(MATCH, px(22)));

        // camera punch-hole (always top centre)
        View cam = new View(c);
        GradientDrawable cg = new GradientDrawable();
        cg.setShape(GradientDrawable.OVAL);
        cg.setColor(0xFF000000);
        cg.setStroke(px(1), 0xFF444444);
        cam.setBackground(cg);
        int cs = Math.max(px(4), Math.round(innerW * 0.05f));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(cs, cs);
        cp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cp.topMargin = px(3);
        screenClip.addView(cam, cp);

        // transparent shield so a tap anywhere on the screen opens the big preview
        if (onTap != null) {
            View shield = new View(c);
            shield.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onTap.run(); }
            });
            screenClip.addView(shield, new FrameLayout.LayoutParams(MATCH, MATCH));
        }

        f.bezel = bezel;
        f.content = content;
        f.title = bar;
        return f;
    }

    private void applyOrientation(boolean land) {
        landscape = land;
        orientBtn.setText(land ? "\u27F3 Landscape" : "\u27F3 Portrait");
        frameHolder.removeAllViews();
        frame = makeFrame(px(140), land, new Runnable() {
            @Override public void run() { showFull(); }
        }, title);
        frameHolder.addView(frame.bezel);
        if (lastXml != null) {
            try {
                Node root = parse(lastXml);
                if (root != null) frame.content.addView(build(root, frame.content));
            } catch (Exception ignored) { }
        } else if (lastMsg != null) {
            drawMessage(frame.content, lastMsg);
        }
    }

    /** Big preview: same layout in a full screen dialog. Tap anywhere to close. */
    private void showFull() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float ratio = 360f / 740f;   // phone is always portrait (width / height)
        int maxW = Math.round(dm.widthPixels * 0.94f) - px(16);
        int maxH = Math.round(dm.heightPixels * 0.90f) - px(16);
        int innerW = Math.min(maxW, Math.round(maxH * ratio));

        final android.app.Dialog d = new android.app.Dialog(getContext(),
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(getContext());
        root.setBackgroundColor(0xF2000000);
        Frame big = makeFrame(innerW, landscape, null, title);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP);
        lp.gravity = Gravity.CENTER;
        root.addView(big.bezel, lp);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        d.setContentView(root);

        try {
            if (lastXml != null) {
                Node r = parse(lastXml);
                if (r != null) big.content.addView(build(r, big.content));
            } else if (lastMsg != null) {
                drawMessage(big.content, lastMsg);
            }
        } catch (Exception ignored) { }
        d.show();
    }

    private void drawMessage(FrameLayout where, String msg) {
        TextView t = new TextView(getContext());
        t.setText(msg);
        t.setTextColor(0xFF666666);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), px(12), px(12), px(12));
        where.addView(t, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ---------- public API ----------

    public void setListener(Listener l) { listener = l; }

    /** Text shown in the purple app bar of the phone frame. */
    public void setAppTitle(String t) {
        title = t == null ? "" : t;
        frame.title.setText(title);
    }

    /** Auto orientation (e.g. manifest says landscape). Ignored once the user taps the rotate button. */
    public void setAutoLandscape(boolean land) {
        if (!userOverride && land != landscape) applyOrientation(land);
    }

    /** Debounced: safe to call on every keystroke. */
    public void setXml(String xml) {
        pending = xml == null ? "" : xml;
        handler.removeCallbacks(renderTask);
        handler.postDelayed(renderTask, 300);
    }

    public void showMessage(String msg) {
        handler.removeCallbacks(renderTask);
        errorBar.setVisibility(GONE);
        frame.content.removeAllViews();
        treeBox.removeAllViews();
        lastXml = null;
        lastMsg = msg;
        drawMessage(frame.content, msg);
    }


    // ---------- runner (Preview tab / Run button) ----------

    public boolean isLandscape() { return landscape; }

    /**
     * A phone (always portrait) running the layout, as large as possible inside availW x availH px.
     * UI only: typing, checkboxes, buttons work. Java logic is not executed.
     */
    public View buildRunnerView(String xml, String appTitle, boolean land, int availW, int availH) {
        float ratio = 360f / 740f;                                   // width / height of the phone
        int innerW = Math.round(Math.min(availW / 1.10f, availH / (1f / ratio + 0.10f)));
        Frame f = makeFrame(innerW, land, null, appTitle);
        try {
            Node r = parse(xml);
            if (r != null) f.content.addView(build(r, f.content));
        } catch (Exception e) {
            drawMessage(f.content, "Layout error: " + e.getMessage());
        }
        return f.bezel;
    }

    /**
     * Fallback when AppPreviewActivity is not in the manifest: full screen dialog,
     * no toolbar. The phone's Back button closes it.
     */
    public void showRunner(String xml, String appTitle, boolean land, final Runnable onClose) {
        Context c = getContext();
        final android.app.Dialog d = new android.app.Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xFF0F0F0F);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        View phone = buildRunnerView(xml, appTitle, land, dm.widthPixels, dm.heightPixels);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP);
        lp.gravity = Gravity.CENTER;
        root.addView(phone, lp);

        d.setContentView(root);
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface x) {
                if (onClose != null) onClose.run();
            }
        });
        d.show();
    }

    // ---------- render ----------

    private void render(String xml) {
        if (xml.trim().isEmpty()) { showMessage("Empty file"); return; }
        Node root;
        View built;
        try {
            root = parse(xml);
            if (root == null) throw new Exception("No root element");
            built = build(root, frame.content);
        } catch (Exception e) {
            // keep the last good preview, just show the error
            errorBar.setText("\u26A0 " + e.getMessage());
            errorBar.setVisibility(VISIBLE);
            return;
        }
        errorBar.setVisibility(GONE);
        lastXml = xml;
        lastMsg = null;
        frame.content.removeAllViews();
        frame.content.addView(built);
        treeBox.removeAllViews();
        addTree(root, 0);
    }

    private Node parse(String xml) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new StringReader(xml));
        Node root = null, cur = null;
        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                Node n = new Node();
                String tag = p.getName();
                n.tag = tag.substring(tag.lastIndexOf('.') + 1);
                n.line = p.getLineNumber();
                for (int i = 0; i < p.getAttributeCount(); i++) {
                    String k = p.getAttributeName(i);
                    int colon = k.indexOf(':');
                    if (colon >= 0) k = k.substring(colon + 1);
                    n.attrs.put(k, p.getAttributeValue(i));
                }
                n.parent = cur;
                if (cur == null) root = n; else cur.kids.add(n);
                cur = n;
            } else if (ev == XmlPullParser.END_TAG && cur != null) {
                cur = cur.parent;
            }
            ev = p.next();
        }
        return root;
    }

    private View build(Node n, ViewGroup parent) {
        Context c = getContext();
        View v;
        switch (n.tag) {
            case "LinearLayout": {
                LinearLayout l = new LinearLayout(c);
                l.setOrientation("vertical".equals(a(n, "orientation")) ? VERTICAL : HORIZONTAL);
                String g = a(n, "gravity");
                if (g != null) l.setGravity(gravity(g));
                v = l;
                break;
            }
            case "FrameLayout": case "RelativeLayout":
            case "ConstraintLayout": case "CoordinatorLayout":
                v = new FrameLayout(c);
                break;
            case "ScrollView": case "HorizontalScrollView":
                v = new ScrollView(c);
                break;
            case "TextView": v = new TextView(c); break;
            case "Button": v = new Button(c); break;
            case "EditText": v = new EditText(c); break;
            case "CheckBox": v = new CheckBox(c); break;
            case "Switch": v = new Switch(c); break;
            case "ImageView": {
                TextView t = new TextView(c);
                t.setText("IMG");
                t.setGravity(Gravity.CENTER);
                t.setBackgroundColor(0xFFCCCCCC);
                v = t;
                break;
            }
            default: {
                TextView t = new TextView(c);
                t.setText("<" + n.tag + ">");
                t.setBackgroundColor(0x22000000);
                v = t;
            }
        }

        if (v instanceof TextView) applyText((TextView) v, n);

        String vis = a(n, "visibility");
        if ("gone".equals(vis)) v.setVisibility(View.GONE);
        else if ("invisible".equals(vis)) v.setVisibility(View.INVISIBLE);
        if ("false".equals(a(n, "enabled"))) v.setEnabled(false);
        if (v instanceof CompoundButton && "true".equals(a(n, "checked"))) {
            ((CompoundButton) v).setChecked(true);
        }
        if (v instanceof EditText) {
            String it = a(n, "inputType");
            if (it != null && it.contains("textPassword")) {
                ((EditText) v).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            } else if (it != null && it.contains("number")) {
                ((EditText) v).setInputType(InputType.TYPE_CLASS_NUMBER);
            }
        }

        String bgc = a(n, "background");
        if (bgc != null && bgc.startsWith("#")) v.setBackgroundColor(color(bgc, Color.TRANSPARENT));

        v.setPadding(
                pad(n, "paddingLeft", "paddingStart"),
                pad(n, "paddingTop", "paddingTop"),
                pad(n, "paddingRight", "paddingEnd"),
                pad(n, "paddingBottom", "paddingBottom"));

        v.setLayoutParams(params(n, parent));

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (Node k : n.kids) vg.addView(build(k, vg));
        }
        return v;
    }

    private void applyText(TextView t, Node n) {
        String s = a(n, "text");
        if (s != null) t.setText(s);
        String h = a(n, "hint");
        if (h != null) t.setHint(h);
        t.setTextColor(color(a(n, "textColor"), 0xFF222222));
        String ts = a(n, "textSize");
        if (ts != null) t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(ts));
        if ("bold".equals(a(n, "textStyle"))) t.setTypeface(null, Typeface.BOLD);
        String g = a(n, "gravity");
        if (g != null) t.setGravity(gravity(g));
    }

    private ViewGroup.LayoutParams params(Node n, ViewGroup parent) {
        int w = size(a(n, "layout_width"));
        int h = size(a(n, "layout_height"));
        int g = gravity(a(n, "layout_gravity"));
        ViewGroup.MarginLayoutParams lp;
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
            String wt = a(n, "layout_weight");
            if (wt != null) {
                try { l.weight = Float.parseFloat(wt); } catch (Exception ignored) { }
            }
            if (g != 0) l.gravity = g;
            lp = l;
        } else {
            FrameLayout.LayoutParams f = new FrameLayout.LayoutParams(w, h);
            if (g != 0) f.gravity = g;
            lp = f;
        }
        lp.setMargins(
                margin(n, "layout_marginLeft", "layout_marginStart"),
                margin(n, "layout_marginTop", "layout_marginTop"),
                margin(n, "layout_marginRight", "layout_marginEnd"),
                margin(n, "layout_marginBottom", "layout_marginBottom"));
        return lp;
    }

    private void addTree(Node n, int depth) {
        TextView row = new TextView(getContext());
        String id = a(n, "id");
        String label = n.tag;
        if (id != null) label += " - " + id.substring(id.lastIndexOf('/') + 1);
        row.setText(label);
        row.setTextSize(13);
        row.setTextColor(depth == 0 ? Color.WHITE : 0xFFAAAAAA);
        row.setPadding(px(depth * 12), px(5), 0, px(5));
        final int line = n.line;
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (listener != null) listener.onNodeClicked(line);
            }
        });
        treeBox.addView(row);
        for (Node k : n.kids) addTree(k, depth + 1);
    }

    // ---------- helpers ----------

    private String a(Node n, String key) { return n.attrs.get(key); }

    private int px(int dp) { return Math.round(dp * density); }

    private int dp(String s) {
        if (s == null) return 0;
        try {
            s = s.trim();
            float mul = s.endsWith("px") ? 1f : density;
            return Math.round(Float.parseFloat(s.replaceAll("[^0-9.\\-]", "")) * mul);
        } catch (Exception e) { return 0; }
    }

    private int size(String v) {
        if (v == null) return WRAP;
        if (v.equals("match_parent") || v.equals("fill_parent")) return MATCH;
        if (v.equals("wrap_content")) return WRAP;
        return dp(v);
    }

    private int margin(Node n, String k1, String k2) {
        String s = a(n, k1);
        if (s == null) s = a(n, k2);
        if (s == null) s = a(n, "layout_margin");
        return dp(s);
    }

    private int pad(Node n, String k1, String k2) {
        String s = a(n, k1);
        if (s == null) s = a(n, k2);
        if (s == null) s = a(n, "padding");
        return dp(s);
    }

    private int color(String s, int def) {
        if (s != null && s.startsWith("#")) {
            try { return Color.parseColor(s); } catch (Exception ignored) { }
        }
        return def;
    }

    private int gravity(String s) {
        if (s == null) return 0;
        int g = 0;
        for (String part : s.split("\\|")) {
            switch (part.trim()) {
                case "center": g |= Gravity.CENTER; break;
                case "center_horizontal": g |= Gravity.CENTER_HORIZONTAL; break;
                case "center_vertical": g |= Gravity.CENTER_VERTICAL; break;
                case "left": case "start": g |= Gravity.START; break;
                case "right": case "end": g |= Gravity.END; break;
                case "top": g |= Gravity.TOP; break;
                case "bottom": g |= Gravity.BOTTOM; break;
                default: break;
            }
        }
        return g;
    }

    private TextView label(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextColor(GOLD);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        return t;
    }
}
