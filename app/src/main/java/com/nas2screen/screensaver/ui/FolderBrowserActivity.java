package com.nas2screen.screensaver.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nas2screen.screensaver.Diag;
import com.nas2screen.screensaver.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fallback folder picker for TVs where the system document picker is missing.
 * Works on plain paths, so it needs storage permission (or "all files access"
 * on Android 11+).
 */
public final class FolderBrowserActivity extends Activity {

    public static final String EXTRA_PATH = "path";
    private static final int REQ_STORAGE = 2001;
    private static final int MAX_ENTRIES = 500;

    private LinearLayout list;
    private ScrollView scrollView;
    private TextView pathView;
    private TextView accessView;
    private Row grantRow;

    private File current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && current != null) {
            up();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------- ui

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(40));

        TextView title = new TextView(this);
        title.setText(R.string.browser_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title);

        pathView = new TextView(this);
        pathView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        pathView.setTextColor(getColor(R.color.text_secondary));
        pathView.setPadding(0, dp(6), 0, dp(14));
        content.addView(pathView);

        accessView = new TextView(this);
        accessView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        accessView.setTextColor(getColor(R.color.error));
        accessView.setPadding(0, 0, 0, dp(10));
        accessView.setVisibility(View.GONE);
        content.addView(accessView);

        grantRow = Row.action(this, getString(R.string.browser_grant), null, this::requestStorage);
        grantRow.state.setText("›");
        grantRow.setVisibility(View.GONE);
        content.addView(grantRow);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(1040));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(scrollView, params);
        setContentView(root);
    }

    private void rebuild() {
        list.removeAllViews();
        boolean access = hasAccess();
        accessView.setVisibility(access ? View.GONE : View.VISIBLE);
        grantRow.setVisibility(access ? View.GONE : View.VISIBLE);
        if (!access) {
            accessView.setText(R.string.storage_permission_needed);
        }

        pathView.setText(current == null
                ? getString(R.string.browser_roots_title)
                : getString(R.string.browser_current, current.getAbsolutePath()));

        if (current == null) {
            if (!access) {
                return;
            }
            List<File> roots = storageRoots();
            for (File root : roots) {
                addRow(labelFor(root), root.getAbsolutePath(), () -> enter(root));
            }
            if (roots.isEmpty()) {
                addInfo(getString(R.string.browser_empty));
            }
            return;
        }

        Row pick = Row.action(this, getString(R.string.browser_pick), current.getAbsolutePath(),
                this::pick);
        pick.title.setTextColor(getColor(R.color.accent));
        addRow(pick);
        if (current.getParentFile() != null) {
            addRow(Row.action(this, "↑ " + getString(R.string.browser_up),
                    current.getParentFile().getAbsolutePath(), this::up));
        }

        File[] children = current.listFiles();
        if (children == null) {
            addInfo(getString(R.string.storage_permission_needed));
            return;
        }
        Arrays.sort(children, (a, b) -> {
            boolean aDir = a.isDirectory();
            boolean bDir = b.isDirectory();
            if (aDir != bDir) {
                return aDir ? -1 : 1;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
        });
        int added = 0;
        for (File child : children) {
            if (!child.isDirectory() || child.getName().startsWith(".")) {
                continue;
            }
            addRow(labelFor(child), null, () -> enter(child));
            if (++added >= MAX_ENTRIES) {
                break;
            }
        }
        if (added == 0) {
            addInfo(getString(R.string.browser_empty));
        }
    }

    private void addRow(String title, String subtitle, Runnable action) {
        Row row = Row.action(this, title, subtitle, action);
        addRow(row);
    }

    private void addRow(Row row) {
        list.addView(row);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollIntoView(v);
            }
        });
        if (list.getChildCount() == 1) {
            row.post(row::requestFocus);
        }
    }

    private void addInfo(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setPadding(dp(20), dp(14), dp(20), dp(14));
        list.addView(view);
    }

    private void scrollIntoView(View view) {
        int top = 0;
        View current = view;
        while (current != null && current.getParent() != scrollView) {
            top += current.getTop();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        final int target = Math.max(0, top - dp(140));
        scrollView.post(() -> scrollView.smoothScrollTo(0, target));
    }

    // -------------------------------------------------------------- navigation

    private void enter(File dir) {
        current = dir;
        rebuild();
    }

    private void up() {
        if (current == null) {
            return;
        }
        File parent = current.getParentFile();
        if (parent == null || current.getAbsolutePath().equals("/")) {
            current = null;
        } else {
            current = parent;
        }
        rebuild();
    }

    private void pick() {
        if (current == null) {
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_PATH, current.getAbsolutePath());
        setResult(RESULT_OK, result);
        finish();
    }

    // ------------------------------------------------------------- permissions

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            } catch (RuntimeException e) {
                Diag.w("Browser", "cannot open app all files settings", e);
            }
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return;
            } catch (RuntimeException e) {
                Diag.w("Browser", "cannot open all files settings", e);
            }
        }
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_STORAGE) {
            rebuild();
        }
    }

    // ------------------------------------------------------------------ roots

    private List<File> storageRoots() {
        List<File> roots = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null && primary.isDirectory() && seen.add(primary.getAbsolutePath())) {
            roots.add(primary);
        }
        File storage = new File("/storage");
        File[] volumes = storage.listFiles();
        if (volumes != null) {
            for (File volume : volumes) {
                if (!volume.isDirectory()) {
                    continue;
                }
                String name = volume.getName();
                if ("self".equals(name)) {
                    continue;
                }
                if ("emulated".equals(name)) {
                    File zero = new File(volume, "0");
                    if (zero.isDirectory() && seen.add(zero.getAbsolutePath())) {
                        roots.add(zero);
                    }
                    continue;
                }
                if (seen.add(volume.getAbsolutePath())) {
                    roots.add(volume);
                }
            }
        }
        return roots;
    }

    private String labelFor(File dir) {
        String path = dir.getAbsolutePath();
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null && path.equals(primary.getAbsolutePath())) {
            return getString(R.string.browser_internal);
        }
        if (path.startsWith("/storage/")) {
            return dir.getName();
        }
        return dir.getName().isEmpty() ? path : dir.getName();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
