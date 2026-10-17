package com.aalamstudio.app;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Colors code in the editor (spans only, the text itself is never changed). */
public class SyntaxHighlighter {

    public static final int KEYWORD = 0xFFC678DD;
    public static final int TYPE = 0xFF61AFEF;
    public static final int STRING = 0xFF98C379;
    public static final int NUMBER = 0xFFD19A66;
    public static final int COMMENT = 0xFF7F848E;
    public static final int ANNOTATION = 0xFFE5C07B;
    public static final int METHOD = 0xFFDCDCAA;
    public static final int TAG = 0xFFE06C75;
    public static final int ATTR = 0xFFD19A66;
    public static final int KEY = 0xFF61AFEF;

    /** Our own span class so old colors can be removed without touching other spans. */
    private static class ColorSpan extends ForegroundColorSpan {
        ColorSpan(int c) { super(c); }
    }

    private static final int MAX_CHARS = 150000;

    private static Set<String> words(String s) {
        return new HashSet<>(Arrays.asList(s.split(" ")));
    }

    private static final Set<String> JAVA = words(
            "abstract assert boolean break byte case catch char class const continue default do double else enum "
            + "extends final finally float for goto if implements import instanceof int interface long native new "
            + "package private protected public return short static strictfp super switch synchronized this throw "
            + "throws transient try void volatile while true false null var record");
    private static final Set<String> KOTLIN = words(
            "as break class continue do else false for fun if in interface is null object package return super this "
            + "throw true try typealias val var when while by catch constructor finally import init abstract "
            + "companion const data enum final inline inner internal lateinit open operator out override private "
            + "protected public sealed suspend vararg");
    private static final Set<String> CPP = words(
            "auto bool break case catch char class const constexpr continue default delete do double else enum "
            + "explicit extern false float for friend goto if inline int long mutable namespace new nullptr "
            + "operator private protected public register return short signed sizeof static struct switch "
            + "template this throw true try typedef typename union unsigned using virtual void volatile while "
            + "string var foreach readonly sealed abstract override base interface internal object ref out is as "
            + "in lock event delegate async await include define ifdef ifndef endif");
    private static final Set<String> PYTHON = words(
            "and as assert async await break class continue def del elif else except False finally for from "
            + "global if import in is lambda None nonlocal not or pass raise return True try while with yield self");
    private static final Set<String> JS = words(
            "async await break case catch class const continue debugger default delete do else export extends "
            + "false finally for function if import in instanceof let new null of return static super switch this "
            + "throw true try typeof undefined var void while with yield");
    private static final Set<String> LUA = words(
            "and break do else elseif end false for function goto if in local nil not or repeat return then true "
            + "until while");
    private static final Set<String> GO = words(
            "break case chan const continue default defer else fallthrough for func go goto if import interface "
            + "map package range return select struct switch type var true false nil");
    private static final Set<String> SWIFT = words(
            "associatedtype class deinit enum extension fileprivate func import init inout internal let open "
            + "operator private protocol public static struct subscript typealias var break case continue default "
            + "defer do else fallthrough for guard if in repeat return switch where while as catch false is nil "
            + "rethrows super self throw throws true try");
    private static final Set<String> DART = words(
            "abstract as assert async await break case catch class const continue default deferred do dynamic "
            + "else enum export extends extension external factory false final finally for if implements import in "
            + "interface is late library mixin new null on operator part required rethrow return static super "
            + "switch sync this throw true try typedef var void while with yield");
    private static final Set<String> NONE = new HashSet<>();

    // c-style / hash / dash comment flavours
    private static final int C_STYLE = 0, HASH = 1, DASH = 2;

    private static final String BODY =
            "|(\"\"\"[\\s\\S]*?\"\"\")"                             // 3 triple string
            + "|(\"(?:\\\\.|[^\"\\\\\\n])*\")"                     // 4 "string"
            + "|('(?:\\\\.|[^'\\\\\\n])*')"                        // 5 'string'
            + "|(@[A-Za-z_]\\w*)"                                  // 6 annotation
            + "|(\\b0[xX][0-9a-fA-F_]+\\b|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlLuU]*\\b)" // 7 number
            + "|([A-Za-z_]\\w*)";                                  // 8 word

    private static final Pattern P_C = Pattern.compile("(/\\*[\\s\\S]*?\\*/)|(//[^\\n]*)" + BODY);
    private static final Pattern P_HASH = Pattern.compile("('''[\\s\\S]*?''')|(#[^\\n]*)" + BODY);
    private static final Pattern P_DASH = Pattern.compile("(--\\[\\[[\\s\\S]*?\\]\\])|(--[^\\n]*)" + BODY);

    private static final Pattern P_XML = Pattern.compile(
            "(<!--[\\s\\S]*?-->)"                    // 1 comment
            + "|(<\\?[\\s\\S]*?\\?>)"                // 2 declaration
            + "|(\"[^\"<>]*\")"                      // 3 attribute value
            + "|(</?[A-Za-z_][\\w.:-]*)"             // 4 tag name
            + "|([A-Za-z_][\\w.:-]*(?=\\s*=))");     // 5 attribute name

    private static final Pattern P_JSON = Pattern.compile(
            "(\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:))"                       // 1 key
            + "|(\"(?:\\\\.|[^\"\\\\\\n])*\")"                             // 2 string
            + "|(-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)"             // 3 number
            + "|(\\b(?:true|false|null)\\b)");                             // 4 literal

    /** Re-colors the whole text. ext is the file extension without the dot. */
    public static void highlight(Editable e, String ext) {
        if (e == null) return;
        ColorSpan[] old = e.getSpans(0, e.length(), ColorSpan.class);
        for (ColorSpan s : old) e.removeSpan(s);
        if (e.length() == 0 || e.length() > MAX_CHARS) return;

        String text = e.toString();
        ext = ext == null ? "" : ext.toLowerCase();

        switch (ext) {
            case "java": case "gradle":
                code(e, text, P_C, C_STYLE, JAVA, true); break;
            case "kt": case "kts":
                code(e, text, P_C, C_STYLE, KOTLIN, true); break;
            case "c": case "h": case "cpp": case "cc": case "hpp": case "cs": case "m": case "hlsl": case "glsl":
                code(e, text, P_C, C_STYLE, CPP, true); break;
            case "js": case "ts":
                code(e, text, P_C, C_STYLE, JS, true); break;
            case "go":
                code(e, text, P_C, C_STYLE, GO, true); break;
            case "swift":
                code(e, text, P_C, C_STYLE, SWIFT, true); break;
            case "dart":
                code(e, text, P_C, C_STYLE, DART, true); break;
            case "py":
                code(e, text, P_HASH, HASH, PYTHON, true); break;
            case "lua":
                code(e, text, P_DASH, DASH, LUA, false); break;
            case "properties": case "sh": case "yml": case "yaml":
                code(e, text, P_HASH, HASH, NONE, false); break;
            case "xml": case "html":
                xml(e, text); break;
            case "json":
                json(e, text); break;
            default:
                break;
        }
    }

    private static void put(Editable e, int color, int start, int end) {
        e.setSpan(new ColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void code(Editable e, String text, Pattern p, int style, Set<String> kw, boolean types) {
        Matcher m = p.matcher(text);
        int len = text.length();
        while (m.find()) {
            int color = 0;
            if (m.group(1) != null) {
                color = style == HASH ? STRING : COMMENT;
            } else if (m.group(2) != null) {
                color = COMMENT;
            } else if (m.group(3) != null || m.group(4) != null || m.group(5) != null) {
                color = STRING;
            } else if (m.group(6) != null) {
                color = ANNOTATION;
            } else if (m.group(7) != null) {
                color = NUMBER;
            } else {
                String w = m.group(8);
                if (kw.contains(w)) {
                    color = KEYWORD;
                } else if (types && Character.isUpperCase(w.charAt(0))) {
                    color = TYPE;
                } else {
                    int k = m.end();
                    while (k < len && text.charAt(k) == ' ') k++;
                    if (k < len && text.charAt(k) == '(') color = METHOD;
                }
            }
            if (color != 0) put(e, color, m.start(), m.end());
        }
    }

    private static void xml(Editable e, String text) {
        Matcher m = P_XML.matcher(text);
        while (m.find()) {
            int color;
            if (m.group(1) != null || m.group(2) != null) color = COMMENT;
            else if (m.group(3) != null) color = STRING;
            else if (m.group(4) != null) color = TAG;
            else color = ATTR;
            put(e, color, m.start(), m.end());
        }
    }

    private static void json(Editable e, String text) {
        Matcher m = P_JSON.matcher(text);
        while (m.find()) {
            int color;
            if (m.group(1) != null) color = KEY;
            else if (m.group(2) != null) color = STRING;
            else if (m.group(3) != null) color = NUMBER;
            else color = KEYWORD;
            put(e, color, m.start(), m.end());
        }
    }
}
