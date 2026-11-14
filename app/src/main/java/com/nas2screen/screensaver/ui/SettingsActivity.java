package com.nas2screen.screensaver.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nas2screen.screensaver.BuildConfig;
import com.nas2screen.screensaver.Diag;
import com.nas2screen.screensaver.Prefs;
import com.nas2screen.screensaver.R;
import com.nas2screen.screensaver.image.ScaledCache;
import com.nas2screen.screensaver.net.SmbPath;
import com.nas2screen.screensaver.source.SourceSpec;
import com.nas2screen.screensaver.source.SourceTester;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TV friendly settings: D-pad navigable rows, immediate save, connection test. */
public final class SettingsActivity extends Activity {

    private static final String TAG = "Settings";
    private static final int REQ_TREE = 1001;
    private static final int REQ_BROWSER = 1002;

    private static final int STATUS_NEUTRAL = 0;
    private static final int STATUS_OK = 1;
    private static final int STATUS_ERROR = 2;

    private Prefs prefs;
    private ScrollView scrollView;
    private LinearLayout content;

    private TextView currentSourceValue;
    private RadioButton radioLocal;
    private RadioButton radioSmb;
    private LinearLayout localGroup;
    private LinearLayout smbGroup;
    private TextView localStatus;
    private TextView smbStatus;
    private TextView folderValue;
    private EditText serverField;
    private EditText folderField;
    private EditText userField;
    private EditText passwordField;
    private Row testRow;
    private EditText intervalField;
    private Row shuffleRow;
    private Row clockRow;
    private Row cacheRow;
    private TextView lastErrorValue;

    private boolean shuffle;
    private boolean clockOn;
    private int lastInterval = Prefs.DEFAULT_INTERVAL;

    private ExecutorService background;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        background = Executors.newSingleThreadExecutor();
        buildUi();
        applyPrefs();
        Diag.i(TAG, "settings opened");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFolderValue();
        updateCurrentSource();
        updateCacheSize();
        updateLastError();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveFields(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (background != null) {
            background.shutdownNow();
        }
    }

    // --------------------------------------------------------------------- ui

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(56));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(1040));
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT);
        scrollParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(scrollView, scrollParams);
        setContentView(root);

        TextView appTitle = new TextView(this);
        appTitle.setText(R.string.app_name);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        appTitle.setTextColor(getColor(R.color.text_primary));
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(appTitle);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.settings_title);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        subtitle.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.bottomMargin = dp(16);
        content.addView(subtitle, subtitleParams);

        // current source card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_panel);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        TextView cardLabel = new TextView(this);
        cardLabel.setText(R.string.current_source);
        cardLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        cardLabel.setTextColor(getColor(R.color.text_secondary));
        cardLabel.setLetterSpacing(0.08f);
        card.addView(cardLabel);
        currentSourceValue = new TextView(this);
        currentSourceValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        currentSourceValue.setTextColor(getColor(R.color.text_primary));
        currentSourceValue.setPadding(0, dp(6), 0, 0);
        card.addView(currentSourceValue);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(14);
        content.addView(card, cardParams);

        Row previewRow = Row.action(this, getString(R.string.preview), null, this::openPreview);
        previewRow.title.setTextColor(getColor(R.color.accent));
        content.addView(previewRow);
        attachFocus(previewRow, null);

        // ------------------------------------------------------------- source
        content.addView(sectionHeader(getString(R.string.section_source)));

        RadioGroup group = new RadioGroup(this);
        radioLocal = radio(getString(R.string.source_local), true);
        radioSmb = radio(getString(R.string.source_smb), false);
        group.addView(radioLocal);
        group.addView(radioSmb);
        content.addView(group);
        attachFocus(radioLocal, null);
        attachFocus(radioSmb, null);
        group.setOnCheckedChangeListener((g, id) -> {
            boolean smb = id == radioSmb.getId();
            prefs.setSourceType(smb ? Prefs.SOURCE_SMB : Prefs.SOURCE_LOCAL);
            applySourceVisibility();
            updateCurrentSource();
        });

        localGroup = new LinearLayout(this);
        localGroup.setOrientation(LinearLayout.VERTICAL);
        content.addView(localGroup);

        folderValue = infoBlock(localGroup, getString(R.string.folder_location),
                getString(R.string.folder_not_set));
        localGroup.addView(Row.action(this, getString(R.string.choose_folder), null, this::pickFolderSaf));
        attachFocus(localGroup.getChildAt(localGroup.getChildCount() - 1), null);
        localGroup.addView(Row.action(this, getString(R.string.choose_folder_manual), null,
                this::pickFolderManual));
        attachFocus(localGroup.getChildAt(localGroup.getChildCount() - 1), null);
        localStatus = statusView(localGroup);

        smbGroup = new LinearLayout(this);
        smbGroup.setOrientation(LinearLayout.VERTICAL);
        content.addView(smbGroup);

        serverField = addField(smbGroup, getString(R.string.smb_server),
                getString(R.string.smb_server_hint), InputType.TYPE_CLASS_TEXT, false);
        folderField = addField(smbGroup, getString(R.string.smb_folder),
                getString(R.string.smb_folder_hint), InputType.TYPE_CLASS_TEXT, false);
        userField = addField(smbGroup, getString(R.string.smb_user),
                getString(R.string.smb_credentials_hint), InputType.TYPE_CLASS_TEXT, false);
        passwordField = addField(smbGroup, getString(R.string.smb_password),
                getString(R.string.smb_credentials_hint),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true);

        testRow = Row.action(this, getString(R.string.test_connection), null, this::testConnection);
        smbGroup.addView(testRow);
        attachFocus(testRow, null);
        smbStatus = statusView(smbGroup);

        // ---------------------------------------------------------- slideshow
        content.addView(sectionHeader(getString(R.string.section_slideshow)));
        content.addView(intervalBlock());
        shuffleRow = Row.create(this, getString(R.string.shuffle_title), getString(R.string.shuffle_desc));
        shuffleRow.setOnClickListener(v -> {
            shuffle = !shuffle;
            prefs.setShuffle(shuffle);
            updateToggle(shuffleRow, shuffle);
        });
        content.addView(shuffleRow);
        attachFocus(shuffleRow, null);

        // -------------------------------------------------------------- screen
        content.addView(sectionHeader(getString(R.string.section_screen)));
        clockRow = Row.create(this, getString(R.string.clock_title), getString(R.string.clock_desc));
        clockRow.setOnClickListener(v -> {
            clockOn = !clockOn;
            prefs.setClock(clockOn);
            updateToggle(clockRow, clockOn);
        });
        content.addView(clockRow);
        attachFocus(clockRow, null);

        // --------------------------------------------------------- maintenance
        content.addView(sectionHeader(getString(R.string.section_maintenance)));
        cacheRow = Row.action(this, getString(R.string.clear_cache), getString(R.string.cache_used, "0 МБ"),
                this::clearCache);
        content.addView(cacheRow);
        attachFocus(cacheRow, null);

        Row dreamSettings = Row.action(this, getString(R.string.open_system_dream_settings), null,
                this::openSystemDreamSettings);
        content.addView(dreamSettings);
        attachFocus(dreamSettings, null);

        TextView lastErrorLabel = new TextView(this);
        lastErrorLabel.setText(R.string.last_error_title);
        lastErrorLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        lastErrorLabel.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams lastErrorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lastErrorParams.topMargin = dp(12);
        content.addView(lastErrorLabel, lastErrorParams);

        lastErrorValue = new TextView(this);
        lastErrorValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        lastErrorValue.setTextColor(getColor(R.color.text_secondary));
        content.addView(lastErrorValue);

        TextView version = new TextView(this);
        version.setText(getString(R.string.version_fmt, BuildConfig.VERSION_NAME));
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        version.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = dp(20);
        content.addView(version, versionParams);

        TextView hint = new TextView(this);
        hint.setText(R.string.nav_hint);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        hint.setTextColor(getColor(R.color.text_secondary));
        content.addView(hint);
    }

    private TextView sectionHeader(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setTextColor(getColor(R.color.accent));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLetterSpacing(0.1f);
        view.setPadding(dp(4), dp(20), dp(4), dp(8));
        return view;
    }

    private RadioButton radio(String text, boolean first) {
        RadioButton button = new RadioButton(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        button.setTextColor(getColor(R.color.text_primary));
        button.setButtonTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        button.setBackgroundResource(R.drawable.bg_row);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setId(first ? 100 : 101);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private TextView infoBlock(LinearLayout parent, String label, String value) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.bg_panel);
        block.setPadding(dp(20), dp(14), dp(20), dp(14));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        labelView.setTextColor(getColor(R.color.text_secondary));
        block.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        valueView.setTextColor(getColor(R.color.text_primary));
        valueView.setPadding(0, dp(4), 0, 0);
        block.addView(valueView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(block, params);
        return valueView;
    }

    private EditText addField(LinearLayout parent, String label, String hint, int inputType,
                              boolean password) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        labelView.setTextColor(getColor(R.color.text_secondary));
        labelView.setPadding(dp(4), dp(6), dp(4), dp(4));
        parent.addView(labelView);

        EditText field = new EditText(this);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        field.setTextColor(getColor(R.color.text_primary));
        field.setHintTextColor(getColor(R.color.text_secondary));
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(inputType);
        if (password) {
            field.setTypeface(Typeface.MONOSPACE);
        }
        field.setBackgroundResource(R.drawable.bg_field);
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        field.setSelectAllOnFocus(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(field, params);
        attachFocus(field, () -> saveFields(false));
        return field;
    }

    private View intervalBlock() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(10), dp(20), dp(10));
        row.setBackgroundResource(R.drawable.bg_panel);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.interval_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setTextColor(getColor(R.color.text_primary));
        column.addView(title);

        TextView desc = new TextView(this);
        desc.setText(R.string.interval_desc);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        desc.setTextColor(getColor(R.color.text_secondary));
        column.addView(desc);

        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        intervalField = new EditText(this);
        intervalField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        intervalField.setTextColor(getColor(R.color.text_primary));
        intervalField.setGravity(Gravity.CENTER);
        intervalField.setSingleLine(true);
        intervalField.setInputType(InputType.TYPE_CLASS_NUMBER);
        intervalField.setBackgroundResource(R.drawable.bg_field);
        intervalField.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.addView(intervalField, new LinearLayout.LayoutParams(dp(110),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        attachFocus(intervalField, () -> saveFields(true));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);
        return row;
    }

    private TextView statusView(LinearLayout parent) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setPadding(dp(18), dp(12), dp(18), dp(12));
        view.setVisibility(View.GONE);
        view.setBackgroundResource(R.drawable.bg_panel_neutral);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        params.bottomMargin = dp(8);
        parent.addView(view, params);
        return view;
    }

    private void attachFocus(View view, Runnable onFocusLost) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollIntoView(v);
            } else if (onFocusLost != null) {
                onFocusLost.run();
            }
        });
    }

    private void scrollIntoView(View view) {
        int top = 0;
        View current = view;
        while (current != null && current != content) {
            top += current.getTop();
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        final int target = Math.max(0, top - dp(140));
        scrollView.post(() -> scrollView.smoothScrollTo(0, target));
    }

    // ------------------------------------------------------------ preferences

    private void applyPrefs() {
        boolean smb = prefs.isSmb();
        radioSmb.setChecked(smb);
        radioLocal.setChecked(!smb);

        serverField.setText(prefs.smbServer());
        folderField.setText(prefs.smbFolder());
        userField.setText(prefs.smbUser());
        passwordField.setText(prefs.smbPassword());

        lastInterval = prefs.intervalSeconds();
        intervalField.setText(String.valueOf(lastInterval));

        shuffle = prefs.shuffle();
        clockOn = prefs.clock();
        updateToggle(shuffleRow, shuffle);
        updateToggle(clockRow, clockOn);

        applySourceVisibility();
        updateFolderValue();
        updateCurrentSource();
        updateLastError();

        View first = smb ? radioSmb : radioLocal;
        first.post(first::requestFocus);
    }

    private void applySourceVisibility() {
        boolean smb = prefs.isSmb();
        localGroup.setVisibility(smb ? View.GONE : View.VISIBLE);
        smbGroup.setVisibility(smb ? View.VISIBLE : View.GONE);
    }

    private void updateToggle(Row row, boolean value) {
        row.state.setText(value ? getString(R.string.toggle_on) : getString(R.string.toggle_off));
        row.state.setTextColor(getColor(value ? R.color.accent : R.color.text_secondary));
    }

    private void updateFolderValue() {
        String label = prefs.localLabel();
        folderValue.setText(label == null || label.isEmpty()
                ? getString(R.string.folder_not_set) : label);
        folderValue.setTextColor(getColor(label == null || label.isEmpty()
                ? R.color.text_secondary : R.color.text_primary));
    }

    private void updateCurrentSource() {
        SourceSpec spec = prefs.toSpec();
        currentSourceValue.setText(spec == null
                ? getString(R.string.current_source_none)
                : spec.describe(this));
    }

    private void updateLastError() {
        String error = prefs.lastError();
        lastErrorValue.setText(error == null || error.isEmpty()
                ? getString(R.string.last_error_none) : error);
    }

    private void updateCacheSize() {
        background.execute(() -> {
            long bytes;
            try {
                bytes = new ScaledCache(this).bytes();
            } catch (Throwable t) {
                bytes = 0;
            }
            final long value = bytes;
            main.post(() -> cacheRow.setSubtitle(getString(R.string.cache_used, formatSize(value))));
        });
    }

    private void saveFields(boolean notify) {
        String server = serverField.getText().toString();
        String folder = folderField.getText().toString();
        String[] normalized = SmbPath.normalizeFields(server, folder);
        if (!normalized[0].equals(server) && !serverField.hasFocus()) {
            serverField.setText(normalized[0]);
        }
        if (!normalized[1].equals(folder) && !folderField.hasFocus()) {
            folderField.setText(normalized[1]);
        }
        prefs.setSmb(normalized[0], normalized[1],
                userField.getText().toString(), passwordField.getText().toString());

        int interval = parseInterval();
        if (interval != lastInterval) {
            lastInterval = interval;
            intervalField.setText(String.valueOf(interval));
            if (notify) {
                Toast.makeText(this, getString(R.string.toast_interval_clamped, interval),
                        Toast.LENGTH_SHORT).show();
            }
        }
        updateCurrentSource();
    }

    private int parseInterval() {
        String text = intervalField.getText().toString().trim();
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            value = lastInterval;
        }
        int clamped = Math.max(Prefs.MIN_INTERVAL, Math.min(Prefs.MAX_INTERVAL, value));
        prefs.setIntervalSeconds(clamped);
        return clamped;
    }

    // --------------------------------------------------------------- actions

    private void openPreview() {
        saveFields(false);
        if (!prefs.isConfigured()) {
            setStatus(smbStatus, getString(R.string.err_not_configured), STATUS_ERROR);
            setStatus(localStatus, getString(R.string.err_not_configured), STATUS_ERROR);
            return;
        }
        startActivity(new Intent(this, PreviewActivity.class));
    }

    private void pickFolderSaf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_TREE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.toast_no_picker, Toast.LENGTH_LONG).show();
            Diag.w(TAG, "no document tree picker", e);
            pickFolderManual();
        }
    }

    private void pickFolderManual() {
        try {
            startActivityForResult(new Intent(this, FolderBrowserActivity.class), REQ_BROWSER);
        } catch (RuntimeException e) {
            Diag.e(TAG, "cannot open folder browser", e);
        }
    }

    private void testConnection() {
        saveFields(false);
        final SourceSpec spec = prefs.toSpec();
        if (spec == null || !spec.isComplete()) {
            setStatus(smbStatus, getString(R.string.test_need_values), STATUS_ERROR);
            return;
        }
        setStatus(smbStatus, getString(R.string.testing), STATUS_NEUTRAL);
        testRow.setEnabled(false);
        background.execute(() -> {
            final SourceTester.Result result = SourceTester.test(getApplicationContext(), spec);
            main.post(() -> {
                setStatus(smbStatus, result.message, result.ok ? STATUS_OK : STATUS_ERROR);
                testRow.setEnabled(true);
                updateCurrentSource();
            });
        });
    }

    private void clearCache() {
        background.execute(() -> {
            try {
                new ScaledCache(this).clear();
                main.post(() -> {
                    Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
                    updateCacheSize();
                });
            } catch (Throwable t) {
                Diag.e(TAG, "cannot clear cache", t);
            }
        });
    }

    private void openSystemDreamSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.open_system_settings_failed, Toast.LENGTH_LONG).show();
            Diag.w(TAG, "cannot open dream settings", e);
        }
    }

    private void setStatus(TextView view, CharSequence text, int kind) {
        if (view == null) {
            return;
        }
        view.setText(text);
        view.setVisibility(View.VISIBLE);
        switch (kind) {
            case STATUS_OK:
                view.setBackgroundResource(R.drawable.bg_panel_ok);
                view.setTextColor(getColor(R.color.ok));
                break;
            case STATUS_ERROR:
                view.setBackgroundResource(R.drawable.bg_panel_error);
                view.setTextColor(getColor(R.color.error));
                break;
            default:
                view.setBackgroundResource(R.drawable.bg_panel_neutral);
                view.setTextColor(getColor(R.color.text_secondary));
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == REQ_TREE && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Diag.w(TAG, "cannot take persistable permission", e);
            }
            String label = describeTree(uri);
            prefs.setLocalUri(uri.toString(), label);
            prefs.setSourceType(Prefs.SOURCE_LOCAL);
            radioLocal.setChecked(true);
            applySourceVisibility();
            updateFolderValue();
            updateCurrentSource();
            Toast.makeText(this, R.string.toast_folder_selected, Toast.LENGTH_SHORT).show();
            testLocal(label);
        } else if (requestCode == REQ_BROWSER && data != null) {
            String path = data.getStringExtra(FolderBrowserActivity.EXTRA_PATH);
            if (path != null && !path.isEmpty()) {
                prefs.setLocalPath(path, path);
                prefs.setSourceType(Prefs.SOURCE_LOCAL);
                radioLocal.setChecked(true);
                applySourceVisibility();
                updateFolderValue();
                updateCurrentSource();
                Toast.makeText(this, R.string.toast_folder_selected, Toast.LENGTH_SHORT).show();
                testLocal(path);
            }
        }
    }

    private void testLocal(final String label) {
        final SourceSpec spec = prefs.toSpec();
        if (spec == null) {
            return;
        }
        setStatus(localStatus, getString(R.string.testing), STATUS_NEUTRAL);
        background.execute(() -> {
            final SourceTester.Result result = SourceTester.test(getApplicationContext(), spec);
            main.post(() -> {
                if (result.ok && result.count == 0) {
                    setStatus(localStatus, getString(R.string.err_no_files), STATUS_ERROR);
                } else {
                    setStatus(localStatus, result.message,
                            result.ok ? STATUS_OK : STATUS_ERROR);
                }
            });
        });
    }

    private String describeTree(Uri uri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(uri);
            int colon = documentId.indexOf(':');
            if (colon > 0) {
                String volume = documentId.substring(0, colon);
                String path = documentId.substring(colon + 1);
                String prefix = "primary".equalsIgnoreCase(volume)
                        ? getString(R.string.browser_internal) : volume;
                return path.isEmpty() ? prefix : prefix + " / " + path;
            }
            return documentId;
        } catch (RuntimeException e) {
            return uri.toString();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return getString(R.string.size_bytes, bytes);
        }
        if (bytes < 1024 * 1024) {
            return getString(R.string.size_kb, bytes / 1024.0);
        }
        return getString(R.string.size_mb, bytes / (1024.0 * 1024.0));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
