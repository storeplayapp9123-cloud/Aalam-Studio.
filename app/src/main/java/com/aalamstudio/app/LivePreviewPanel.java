package com.aalamstudio.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Gravity;
import android.view.View;
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final FrameLayout phone;
    private final TextView appTitle;
    private final TextView errorBar;
    private final LinearLayout treeBox;
    private Listener listener;
    private String pending = "";

    private final Runnable renderTask = new Runnable() {
        @Override public void run() { render(pending); }
    };

    public LivePreviewPanel(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setPadding(px(12), px(12), px(12), px(12));

        addView(label("Live Preview"));

        errorBar = new TextView(c);
        errorBar.setTextColor(0xFFFF6B6B);
        errorBar.setTextSize(11);
        errorBar.setVisibility(GONE);
        addView(errorBar);

        // ---- phone frame (bezel + status bar + app bar + content + home bar) ----
        final int innerW = px(140);
        final int vw = px(360), vh = px(740);          // virtual phone screen (360x740 dp)
        final float scale = (float) innerW / vw;
        final int innerH = Math.round(vh * scale);

        FrameLayout bezel = new FrameLayout(c);
        GradientDrawable bz = new GradientDrawable();
        bz.setColor(0xFF0B0B0B);
        bz.setCornerRadius(px(24));
        bz.setStroke(px(2), 0xFF3A3A3A);
        bezel.setBackground(bz);
        bezel.setPadding(px(6), px(6), px(6), px(6));

        FrameLayout screenClip = new FrameLayout(c);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.WHITE);
        sbg.setCornerRadius(px(18));
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

        // status bar
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

        // app bar
        appTitle = new TextView(c);
        appTitle.setText("My Application");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(20);
        appTitle.setGravity(Gravity.CENTER_VERTICAL);
        appTitle.setBackgroundColor(0xFF5E35B1);
        appTitle.setPadding(px(16), 0, px(16), 0);
        virt.addView(appTitle, new LinearLayout.LayoutParams(MATCH, px(56)));

        // content area: the rendered layout goes here
        phone = new FrameLayout(c);
        phone.setBackgroundColor(Color.WHITE);
        phone.setClipChildren(true);
        virt.addView(phone, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        // home indicator bar
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

        // camera punch-hole (not scaled)
        View cam = new View(c);
        GradientDrawable cg = new GradientDrawable();
        cg.setShape(GradientDrawable.OVAL);
        cg.setColor(0xFF000000);
        cg.setStroke(px(1), 0xFF444444);
        cam.setBackground(cg);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(7), px(7));
        cp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cp.topMargin = px(3);
        screenClip.addView(cam, cp);

        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(WRAP, WRAP);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.topMargin = px(8);
        pl.bottomMargin = px(12);
        addView(bezel, pl);

        addView(label("Component Tree"));
        treeBox = new LinearLayout(c);
        treeBox.setOrientation(VERTICAL);
        addView(treeBox, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    // ---------- public API ----------

    public void setListener(Listener l) { listener = l; }

    /** Text shown in the purple app bar of the phone frame. */
    public void setAppTitle(String title) { appTitle.setText(title); }

    /** Debounced: safe to call on every keystroke. */
    public void setXml(String xml) {
        pending = xml == null ? "" : xml;
        handler.removeCallbacks(renderTask);
        handler.postDelayed(renderTask, 300);
    }

    public void showMessage(String msg) {
        handler.removeCallbacks(renderTask);
        errorBar.setVisibility(GONE);
        phone.removeAllViews();
        treeBox.removeAllViews();
        TextView t = new TextView(getContext());
        t.setText(msg);
        t.setTextColor(0xFF666666);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), px(12), px(12), px(12));
        phone.addView(t, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ---------- render ----------

    private void render(String xml) {
        if (xml.trim().isEmpty()) { showMessage("Empty file"); return; }
        Node root;
        View built;
        try {
            root = parse(xml);
            if (root == null) throw new Exception("No root element");
            built = build(root, phone);
        } catch (Exception e) {
            // keep the last good preview, just show the error
            errorBar.setText("\u26A0 " + e.getMessage());
            errorBar.setVisibility(VISIBLE);
            return;
        }
        errorBar.setVisibility(GONE);
        phone.removeAllViews();
        phone.addView(built);
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
