package com.aalamstudio.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Long-press menu for the file tree (Android Studio style).
 *
 *   FileContextMenu menu = new FileContextMenu(this, host);
 *   menu.setProject(projectRootDir, "com.example.myapp");
 *   row.setOnLongClickListener(v -> { menu.show(file); return true; });
 *
 * Activity must forward:
 *   protected void onActivityResult(int r, int res, Intent d) {
 *       if (!menu.handleActivityResult(r, res, d)) super.onActivityResult(r, res, d);
 *   }
 */
public class FileContextMenu {

    public interface Host {
        void onOpenFile(File f);
        void onRenamed(File oldFile, File newFile); // also used for move (cut/paste)
        void onDeleted(File f);
        void onTreeChanged();                       // refresh the tree UI
    }

    private static final int REQ_UPLOAD = 7101;
    private static final int REQ_EXPORT = 7102;
    private static final int THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private static final String[][] NEW_ITEMS = {
            {"Java Class", "new_class"},
            {"Interface", "new_interface"},
            {"Package", "new_package"},
            {"File", "new_file"},
            {"Folder", "new_folder"},
            {"Activity", "new_activity"},
            {"Fragment", "new_fragment"},
            {"Service", "new_service"},
    };

    // shared clipboard so Cut/Copy works across menu instances
    private static File clipFile;
    private static boolean clipCut;

    private final Activity act;
    private final Host host;
    private File projectRoot;
    private String appPackage = "";
    private File uploadDir, exportFile;

    public FileContextMenu(Activity activity, Host host) {
        this.act = activity;
        this.host = host;
    }

    public void setProject(File root, String packageName) {
        this.projectRoot = root;
        this.appPackage = packageName == null ? "" : packageName;
    }

    // ================= menus =================

    public void show(File t) {
        List<String[]> m = new ArrayList<>();
        boolean isRoot = t.equals(projectRoot);
        if (t.isDirectory()) {
            m.add(new String[]{"New  \u203A", "new"});
            m.add(new String[]{"Upload File", "upload"});
            if (clipFile != null) m.add(new String[]{"Paste (" + clipFile.getName() + ")", "paste"});
            m.add(new String[]{"Copy", "copy"});
            if (!isRoot) m.add(new String[]{"Cut", "cut"});
            m.add(new String[]{"Copy Path", "path"});
            m.add(new String[]{"Find in Files (soon)", "soon"});
            m.add(new String[]{"Replace in Files (soon)", "soon"});
            if (!isRoot) m.add(new String[]{"Rename", "rename"});
            m.add(new String[]{"Info", "info"});
            if (!isRoot) m.add(new String[]{"Delete", "delete"});
        } else {
            m.add(new String[]{"Open", "open"});
            m.add(new String[]{"Rename", "rename"});
            m.add(new String[]{"Duplicate", "duplicate"});
            m.add(new String[]{"Copy", "copy"});
            m.add(new String[]{"Cut", "cut"});
            m.add(new String[]{"Copy Path", "path"});
            m.add(new String[]{"Download", "export"});
            m.add(new String[]{"Info", "info"});
            m.add(new String[]{"Delete", "delete"});
        }
        showMenu(t.getName(), m.toArray(new String[0][]), t);
    }

    private void showMenu(String title, final String[][] items, final File t) {
        String[] labels = new String[items.length];
        for (int i = 0; i < items.length; i++) labels[i] = items[i][0];
        new AlertDialog.Builder(act, THEME)
                .setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        perform(items[which][1], t);
                    }
                }).show();
    }

    private void perform(String id, File t) {
        switch (id) {
            case "new": showMenu("New", NEW_ITEMS, t); break;
            case "new_class": ask("New Java Class", "Name (e.g. Player)", "", id, t); break;
            case "new_interface": ask("New Interface", "Name (e.g. Movable)", "", id, t); break;
            case "new_package": ask("New Package", "e.g. com.myapp.utils", "", id, t); break;
            case "new_file": ask("New File", "e.g. notes.txt", "", id, t); break;
            case "new_folder": ask("New Folder", "Folder name", "", id, t); break;
            case "new_activity": ask("New Activity", "e.g. SettingsActivity", "", id, t); break;
            case "new_fragment": ask("New Fragment", "e.g. HomeFragment", "", id, t); break;
            case "new_service": ask("New Service", "e.g. SyncService", "", id, t); break;
            case "rename": ask("Rename", "New name", t.getName(), id, t); break;
            case "open": host.onOpenFile(t); break;
            case "copy": clipFile = t; clipCut = false; toast("Copied: " + t.getName()); break;
            case "cut": clipFile = t; clipCut = true; toast("Cut: " + t.getName()); break;
            case "paste": paste(t); break;
            case "path": copyPath(t); break;
            case "duplicate": duplicate(t); break;
            case "delete": confirmDelete(t); break;
            case "info": info(t); break;
            case "upload": startUpload(t); break;
            case "export": startExport(t); break;
            default: toast("Ye feature jaldi aayega"); break;
        }
    }

    private void ask(String title, String hint, String def, final String id, final File t) {
        final EditText input = new EditText(act);
        input.setHint(hint);
        input.setText(def);
        input.setSingleLine(true);
        input.selectAll();
        FrameLayout box = new FrameLayout(act);
        int pad = (int) (20 * act.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(act, THEME)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        onInput(id, t, input.getText().toString().trim());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================= create / rename =================

    private void onInput(String id, File t, String text) {
        if (text.isEmpty()) return;
        try {
            switch (id) {
                case "new_class": {
                    String n = stripJava(text);
                    createFile(new File(t, n + ".java"),
                            pkgLine(t) + "public class " + n + " {\n}\n");
                    break;
                }
                case "new_interface": {
                    String n = stripJava(text);
                    createFile(new File(t, n + ".java"),
                            pkgLine(t) + "public interface " + n + " {\n}\n");
                    break;
                }
                case "new_package": {
                    File d = new File(t, text.replace('.', '/'));
                    if (d.exists()) { toast("Package pehle se hai"); return; }
                    d.mkdirs();
                    break;
                }
                case "new_file": createFile(new File(t, text), ""); break;
                case "new_folder": {
                    File d = new File(t, text);
                    if (d.exists()) { toast("Folder pehle se hai"); return; }
                    d.mkdirs();
                    break;
                }
                case "new_activity": newActivity(t, stripJava(text)); break;
                case "new_fragment": newFragment(t, stripJava(text)); break;
                case "new_service": newService(t, stripJava(text)); break;
                case "rename": rename(t, text); return;
                default: return;
            }
        } catch (IOException e) {
            toast("Error: " + e.getMessage());
            return;
        }
        host.onTreeChanged();
    }

    private void newActivity(File dir, String name) throws IOException {
        String layout = "activity_" + snake(name.replaceAll("Activity$", ""));
        String code = pkgLine(dir)
                + rImport()
                + "import android.app.Activity;\nimport android.os.Bundle;\n\n"
                + "public class " + name + " extends Activity {\n"
                + "    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout." + layout + ");\n    }\n}\n";
        createFile(new File(dir, name + ".java"), code);
        createLayout(layout);
        boolean ok = registerInManifest("activity", fullName(dir, name));
        if (!ok) toast("AndroidManifest me <activity> khud add karo");
    }

    private void newFragment(File dir, String name) throws IOException {
        String layout = "fragment_" + snake(name.replaceAll("Fragment$", ""));
        String code = pkgLine(dir)
                + rImport()
                + "import android.app.Fragment;\nimport android.os.Bundle;\n"
                + "import android.view.LayoutInflater;\nimport android.view.View;\n"
                + "import android.view.ViewGroup;\n\n"
                + "public class " + name + " extends Fragment {\n"
                + "    @Override\n    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {\n"
                + "        return inflater.inflate(R.layout." + layout + ", container, false);\n    }\n}\n";
        createFile(new File(dir, name + ".java"), code);
        createLayout(layout);
    }

    private void newService(File dir, String name) throws IOException {
        String code = pkgLine(dir)
                + "import android.app.Service;\nimport android.content.Intent;\nimport android.os.IBinder;\n\n"
                + "public class " + name + " extends Service {\n"
                + "    @Override\n    public IBinder onBind(Intent intent) {\n        return null;\n    }\n}\n";
        createFile(new File(dir, name + ".java"), code);
        boolean ok = registerInManifest("service", fullName(dir, name));
        if (!ok) toast("AndroidManifest me <service> khud add karo");
    }

    private void createLayout(String layoutName) throws IOException {
        if (projectRoot == null) return;
        File f = new File(projectRoot, "res/layout/" + layoutName + ".xml");
        if (f.exists()) return;
        createFile(f, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:orientation=\"vertical\">\n\n</LinearLayout>\n");
    }

    private boolean registerInManifest(String tag, String fullName) {
        if (projectRoot == null) return false;
        File[] candidates = {
                new File(projectRoot, "AndroidManifest.xml"),
                new File(projectRoot, "app/src/main/AndroidManifest.xml")};
        for (File m : candidates) {
            if (!m.exists()) continue;
            try {
                String s = readText(m);
                int i = s.lastIndexOf("</application>");
                if (i < 0) return false;
                String add = "    <" + tag + " android:name=\"" + fullName + "\" />\n    ";
                writeText(m, s.substring(0, i) + add + s.substring(i));
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private void createFile(File f, String content) throws IOException {
        if (f.exists()) throw new IOException(f.getName() + " pehle se hai");
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        writeText(f, content);
    }

    private void rename(File t, String name) {
        if (name.contains("/")) { toast("Naam me '/' nahi ho sakta"); return; }
        File dest = new File(t.getParentFile(), name);
        if (dest.exists()) { toast("Is naam ki file pehle se hai"); return; }
        if (!t.renameTo(dest)) { toast("Rename fail"); return; }
        host.onRenamed(t, dest);
        host.onTreeChanged();
    }

    // ================= clipboard / copy / delete =================

    private void paste(File dir) {
        if (clipFile == null || !clipFile.exists()) { toast("Clipboard khali hai"); clipFile = null; return; }
        if (clipFile.isDirectory()
                && (dir.getAbsolutePath() + "/").startsWith(clipFile.getAbsolutePath() + "/")) {
            toast("Folder ko apne andar paste nahi kar sakte");
            return;
        }
        if (clipCut && dir.equals(clipFile.getParentFile())) { toast("Ye already isi folder me hai"); return; }
        File dest = uniqueDest(dir, clipFile.getName());
        try {
            if (clipCut) {
                if (!clipFile.renameTo(dest)) {
                    copyRec(clipFile, dest);
                    deleteRec(clipFile);
                }
                host.onRenamed(clipFile, dest);
                clipFile = null;
            } else {
                copyRec(clipFile, dest);
            }
        } catch (IOException e) {
            toast("Paste fail: " + e.getMessage());
        }
        host.onTreeChanged();
    }

    private void duplicate(File f) {
        try {
            copyRec(f, uniqueDest(f.getParentFile(), f.getName()));
            host.onTreeChanged();
        } catch (IOException e) {
            toast("Duplicate fail: " + e.getMessage());
        }
    }

    private void copyPath(File f) {
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("path", f.getAbsolutePath()));
        toast("Path copy ho gaya");
    }

    private void confirmDelete(final File f) {
        new AlertDialog.Builder(act, THEME)
                .setTitle("Delete")
                .setMessage("\"" + f.getName() + "\" delete karna hai?"
                        + (f.isDirectory() ? "\nFolder ke andar ki sab files bhi delete ho jaayengi." : ""))
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        deleteRec(f);
                        if (f.equals(clipFile)) clipFile = null;
                        host.onDeleted(f);
                        host.onTreeChanged();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void info(File f) {
        String msg = "Path: " + f.getAbsolutePath()
                + "\nSize: " + Formatter.formatFileSize(act, sizeOf(f))
                + "\nModified: " + DateFormat.getDateTimeInstance().format(new Date(f.lastModified()));
        if (f.isDirectory()) {
            String[] kids = f.list();
            msg += "\nItems: " + (kids == null ? 0 : kids.length);
        }
        new AlertDialog.Builder(act, THEME).setTitle(f.getName()).setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    // ================= upload / download =================

    private void startUpload(File dir) {
        uploadDir = dir;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        act.startActivityForResult(i, REQ_UPLOAD);
    }

    private void startExport(File f) {
        exportFile = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_TITLE, f.getName());
        act.startActivityForResult(i, REQ_EXPORT);
    }

    /** Call from Activity.onActivityResult. Returns true if it was ours. */
    public boolean handleActivityResult(int req, int res, Intent data) {
        if (req != REQ_UPLOAD && req != REQ_EXPORT) return false;
        if (res != Activity.RESULT_OK || data == null) return true;
        try {
            if (req == REQ_UPLOAD && uploadDir != null) {
                int n = 0;
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        copyUri(data.getClipData().getItemAt(i).getUri(), uploadDir);
                        n++;
                    }
                } else if (data.getData() != null) {
                    copyUri(data.getData(), uploadDir);
                    n = 1;
                }
                toast(n + " file upload ho gayi");
                host.onTreeChanged();
            } else if (req == REQ_EXPORT && exportFile != null && data.getData() != null) {
                try (OutputStream os = act.getContentResolver().openOutputStream(data.getData());
                     InputStream is = new FileInputStream(exportFile)) {
                    pipe(is, os);
                }
                toast("Download ho gaya");
            }
        } catch (IOException e) {
            toast("Error: " + e.getMessage());
        }
        return true;
    }

    private void copyUri(Uri uri, File dir) throws IOException {
        String name = "upload_" + System.currentTimeMillis();
        try (Cursor c = act.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        }
        File dest = uniqueDest(dir, name);
        try (InputStream is = act.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(dest)) {
            pipe(is, os);
        }
    }

    // ================= helpers =================

    private String packageOf(File dir) {
        String p = dir.getAbsolutePath().replace('\\', '/');
        int i = p.indexOf("/java/");
        return i >= 0 ? p.substring(i + 6).replace('/', '.') : "";
    }

    private String pkgLine(File dir) {
        String p = packageOf(dir);
        return p.isEmpty() ? "" : "package " + p + ";\n\n";
    }

    private String fullName(File dir, String cls) {
        String p = packageOf(dir);
        return p.isEmpty() ? cls : p + "." + cls;
    }

    private String rImport() {
        return appPackage.isEmpty() ? "" : "import " + appPackage + ".R;\n";
    }

    private String stripJava(String s) {
        return s.endsWith(".java") ? s.substring(0, s.length() - 5) : s;
    }

    private String snake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private File uniqueDest(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int n = 1;
        while (f.exists()) {
            f = new File(dir, base + "_copy" + (n > 1 ? n : "") + ext);
            n++;
        }
        return f;
    }

    private void copyRec(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("folder nahi bana");
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyRec(k, new File(dst, k.getName()));
        } else {
            try (InputStream is = new FileInputStream(src); OutputStream os = new FileOutputStream(dst)) {
                pipe(is, os);
            }
        }
    }

    private void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    private long sizeOf(File f) {
        if (!f.isDirectory()) return f.length();
        long n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += sizeOf(k);
        return n;
    }

    private void pipe(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
    }

    private String readText(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            pipe(is, bo);
            return new String(bo.toByteArray(), "UTF-8");
        }
    }

    private void writeText(File f, String s) throws IOException {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(s.getBytes("UTF-8"));
        }
    }

    private void toast(String m) {
        Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
    }
}
