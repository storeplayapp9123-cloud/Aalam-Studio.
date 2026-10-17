package com.aalamstudio.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.EditText;

/**
 * Code editor: line numbers on the left + colored code (syntax highlighting).
 *
 *   codeEditorText = CodeEditText.replace(codeEditorText);   // once, in onCreate
 *   ((CodeEditText) codeEditorText).setFileName("MainActivity.java");  // before setText(...)
 */
public class CodeEditText extends EditText {

    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gutterPaint = new Paint();
    private final Paint dividerPaint = new Paint();
    private final Rect clip = new Rect();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final float density;
    private int gutterW = 0;
    private int basePadL = 0;
    private String ext = "";

    private final Runnable highlightTask = new Runnable() {
        @Override public void run() { highlightNow(); }
    };

    public CodeEditText(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;

        numPaint.setColor(0xFF6B7078);
        numPaint.setTextAlign(Paint.Align.RIGHT);
        numPaint.setTypeface(Typeface.MONOSPACE);
        gutterPaint.setColor(0xFF121212);
        dividerPaint.setColor(0xFF2A2A2A);

        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c2) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c2) { }
            @Override public void afterTextChanged(Editable e) {
                updateGutter(e);
                handler.removeCallbacks(highlightTask);
                handler.postDelayed(highlightTask, 120);
            }
        });
    }

    /** Sets the language from the file name (call before setText when opening a file). */
    public void setFileName(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        handler.removeCallbacks(highlightTask);
        handler.postDelayed(highlightTask, 0);
    }

    private void highlightNow() {
        Editable e = getText();
        if (e != null) SyntaxHighlighter.highlight(e, ext);
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    /** Width of the number column depends on how many digits the last line number has. */
    private void updateGutter(Editable e) {
        int lines = 1;
        for (int i = 0; i < e.length(); i++) {
            if (e.charAt(i) == '\n') lines++;
        }
        int digits = Math.max(2, String.valueOf(lines).length());
        numPaint.setTextSize(getTextSize() * 0.85f);
        int w = Math.round(numPaint.measureText("0") * digits) + dp(14);
        if (w != gutterW) {
            gutterW = w;
            super.setPadding(basePadL + gutterW + dp(6), getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Layout layout = getLayout();
        if (layout == null || gutterW == 0) return;

        canvas.getClipBounds(clip);
        int sx = getScrollX();

        // number column background + divider
        canvas.drawRect(sx, clip.top, sx + gutterW, clip.bottom, gutterPaint);
        canvas.drawRect(sx + gutterW - 1, clip.top, sx + gutterW, clip.bottom, dividerPaint);

        numPaint.setTextSize(getTextSize() * 0.85f);
        CharSequence text = getText();
        int topOff = getExtendedPaddingTop();
        int count = layout.getLineCount();
        int first = layout.getLineForVertical(Math.max(0, clip.top - topOff));
        int last = Math.min(count - 1, layout.getLineForVertical(Math.max(0, clip.bottom - topOff)));

        // logical line number of the first visible row (long lines wrap into several rows)
        int number = 1;
        for (int i = 1; i <= first; i++) {
            if (text.charAt(layout.getLineStart(i) - 1) == '\n') number++;
        }

        float x = sx + gutterW - dp(8);
        for (int i = first; i <= last; i++) {
            boolean startsLine = i == 0 || text.charAt(layout.getLineStart(i) - 1) == '\n';
            if (i > first && startsLine) number++;
            if (startsLine) {
                canvas.drawText(String.valueOf(number), x, topOff + layout.getLineBaseline(i), numPaint);
            }
        }
    }

    /**
     * Replaces the EditText from the layout XML with a CodeEditText that looks the same
     * (same id, size, colors, font, position) and adds line numbers + colors.
     */
    public static CodeEditText replace(EditText old) {
        if (old instanceof CodeEditText) return (CodeEditText) old;

        ViewGroup parent = (ViewGroup) old.getParent();
        CodeEditText n = new CodeEditText(old.getContext());

        n.setId(old.getId());
        n.setInputType(old.getInputType()
                | InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        n.setImeOptions(old.getImeOptions());
        n.setTextSize(TypedValue.COMPLEX_UNIT_PX, old.getTextSize());
        n.setTypeface(old.getTypeface() != null ? old.getTypeface() : Typeface.MONOSPACE);
        n.setTextColor(old.getTextColors());
        n.setHintTextColor(old.getHintTextColors());
        n.setHighlightColor(old.getHighlightColor());
        n.setGravity(old.getGravity());
        n.setLineSpacing(old.getLineSpacingExtra(), old.getLineSpacingMultiplier());
        n.setHorizontallyScrolling(false);
        n.setFilters(old.getFilters());
        n.setVerticalScrollBarEnabled(old.isVerticalScrollBarEnabled());
        n.setOverScrollMode(old.getOverScrollMode());

        Drawable bg = old.getBackground();
        if (bg != null && bg.getConstantState() != null) {
            n.setBackground(bg.getConstantState().newDrawable());
        } else {
            n.setBackground(bg);
        }

        n.basePadL = old.getPaddingLeft();
        n.setPadding(old.getPaddingLeft(), old.getPaddingTop(), old.getPaddingRight(), old.getPaddingBottom());
        n.setHint(old.getHint());
        n.setText(old.getText());
        n.setLayoutParams(old.getLayoutParams());

        if (parent != null) {
            int idx = parent.indexOfChild(old);
            parent.removeViewAt(idx);
            parent.addView(n, idx);
        }
        n.updateGutter(n.getText());
        return n;
    }

    /** Keep the number column: padding set from outside is treated as the base padding. */
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        basePadL = left;
        super.setPadding(left + gutterW + (gutterW > 0 ? dp(6) : 0), top, right, bottom);
    }
}
