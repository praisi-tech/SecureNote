package com.example.securenote.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;


import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.securenote.R;
import com.example.securenote.databinding.ActivityAddEditNoteBinding;
import com.example.securenote.model.Attachment;
import com.example.securenote.model.Note;
import com.example.securenote.model.TodoItem;
import com.example.securenote.util.PasswordUtil;
import com.example.securenote.viewmodel.NoteViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class AddEditNoteActivity extends AppCompatActivity {

    // Intent keys
    public static final String EXTRA_ID = "note_id";
    public static final String EXTRA_TITLE = "note_title";
    public static final String EXTRA_CONTENT = "note_content";
    public static final String EXTRA_PINNED = "note_pinned";
    public static final String EXTRA_LOCKED = "note_locked";
    public static final String EXTRA_LOCK_PASSWORD_HASH = "note_lock_password_hash";

    // Misc constants
    private static final int REQUEST_CODE_PICK_ATTACHMENT = 1001;
    private static final int SCROLL_BUTTON_THRESHOLD = 350;
    private static final long SAVE_DELAY_MS = 500;

    // Core objects
    private ActivityAddEditNoteBinding binding;
    private NoteViewModel noteViewModel;

    // Adapters
    private AttachmentAdapter attachmentAdapter;
    private TodoAdapter todoAdapter;

    // State
    private boolean isTodoSectionVisible = false;
    private boolean isEditMode = false;
    private boolean isKeyboardVisible = false;
    private boolean isFormattingToolbarVisible = false;

    private String noteId;
    private String existingPasswordHash;
    private Note note;

    // Autosave systems
    private final Handler todoSaveHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pendingTodoSaves = new HashMap<>();
    private final Handler noteSaveHandler = new Handler(Looper.getMainLooper());
    private Runnable noteSaveRunnable;

    // Attachment picker
    private final ActivityResultLauncher<String[]> attachmentPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) addAttachment(uri); });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAddEditNoteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        note = new Note();

        receiveIntentData();
        ensureNoteIdInitialized();

        setupCategorySystem();
        setupFormattingToolbar();
        setupAttachmentUI();
        setupQuickActionIcons();
        setupKeyboardListener();
        setupScrollButton();
        setupSmartFocus();
        setupAutosaveListeners();

        updateAllIcons();

        // Back button callback logic
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveAndFinish();
            }
        });

        // CUSTOM BACK BUTTON — put this HERE
        binding.btnBack.setOnClickListener(v -> {
            getOnBackPressedDispatcher().onBackPressed();
        });
    }


    /* ==========================================================================================
     *  CATEGORY SYSTEM
     * ========================================================================================== */

    private void setupCategorySystem() {
        binding.btnCategory.setOnClickListener(v -> {
            String[] categories = getResources().getStringArray(R.array.note_categories);

            int selectedIndex = -1;
            String current = note.getCategory();
            if (current != null) {
                for (int i = 0; i < categories.length; i++) {
                    if (categories[i].equals(current)) {
                        selectedIndex = i;
                        break;
                    }
                }
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select Category")
                    .setSingleChoiceItems(categories, selectedIndex, (dialog, which) -> {
                        note.setCategory(categories[which]);
                        updateCategoryIcon();
                        debouncedAutosaveNote();
                        dialog.dismiss();
                    })
                    .show();
        });
    }

    private void updateCategoryIcon() {
        boolean active = note.getCategory() != null && !"All".equals(note.getCategory());
        int color = ContextCompat.getColor(this, active ? R.color.peri_primary : R.color.text_secondary);
        binding.btnCategory.setColorFilter(color);
    }

    /* ==========================================================================================
     *  RECEIVE INTENT DATA (UNIFIED & FIXED)
     * ========================================================================================== */

    private void receiveIntentData() {
        Intent i = getIntent();
        if (i == null) return;

        if (i.hasExtra(EXTRA_ID)) {
            isEditMode = true;

            noteId = i.getStringExtra(EXTRA_ID);

            // ★ Restore HTML formatting safely
            String html = i.getStringExtra(EXTRA_CONTENT);
            if (!TextUtils.isEmpty(html)) {

                CharSequence restored;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    restored = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
                } else {
                    restored = Html.fromHtml(html); // API < 24 fallback
                }

                binding.etContent.setText(restored, TextView.BufferType.SPANNABLE);
            }

            // ★ DO NOT overwrite text again (this was destroying formatting)
            // binding.etContent.setText(i.getStringExtra(EXTRA_CONTENT));  ← REMOVE THIS LINE

            binding.etTitle.setText(i.getStringExtra(EXTRA_TITLE));
            binding.cbPinned.setChecked(i.getBooleanExtra(EXTRA_PINNED, false));

            boolean locked = i.getBooleanExtra(EXTRA_LOCKED, false);
            binding.cbLock.setChecked(locked);

            existingPasswordHash = i.getStringExtra(EXTRA_LOCK_PASSWORD_HASH);
            if (existingPasswordHash != null) {
                note.setLocked(true);
                binding.cbLock.setChecked(true);
            }

            // CATEGORY
            String category = i.getStringExtra("note_category");
            if (TextUtils.isEmpty(category)) category = "All";
            note.setCategory(category);

        } else {
            // NEW NOTE
            noteId = UUID.randomUUID().toString();
            note.setCategory("All");
        }
    }


    private void ensureNoteIdInitialized() {
        if (noteId == null || noteId.isEmpty())
            noteId = UUID.randomUUID().toString();
    }

    /* ==========================================================================================
     *  SAVE SYSTEM (FINAL CORRECT VERSION B)
     * ========================================================================================== */

    private void doSaveNote() {

        // Save pending TODOs
        for (Runnable r : pendingTodoSaves.values()) {
            todoSaveHandler.removeCallbacks(r);
            r.run();
        }
        pendingTodoSaves.clear();

        String title = binding.etTitle.getText().toString().trim();

        // Convert Spannable → HTML (API-friendly)
        String contentHtml = Html.toHtml((Spanned) binding.etContent.getText());

        if (title.isEmpty() && contentHtml.trim().isEmpty()) return;

        boolean locked = binding.cbLock.isChecked();
        String passwordHash = null;

        if (locked) {
            String pwd = binding.etLockPassword.getText().toString().trim();
            if (!pwd.isEmpty()) {
                passwordHash = PasswordUtil.hashLockPassword(pwd);
            } else if (existingPasswordHash != null) {
                passwordHash = existingPasswordHash;
            }
        }

        Note n = new Note(
                noteId,
                title,
                contentHtml, // save HTML, not plain text
                System.currentTimeMillis(),
                binding.cbPinned.isChecked()
        );

        n.setLocked(locked);
        n.setLockPassword(passwordHash);
        n.setInTrash(note.isInTrash());
        n.setCategory(note.getCategory());

        if (isEditMode) noteViewModel.updateNote(n);
        else noteViewModel.addNote(n);
    }




    private void saveAndFinish() {
        doSaveNote();
        Intent result = new Intent();
        result.putExtra("note_category", note.getCategory());
        setResult(RESULT_OK, result);
        finish();
    }



    /* ==========================================================================================
     *  FORMATTING TOOLBAR
     * ========================================================================================== */

    private void setupFormattingToolbar() {

        // BOLD
        binding.btnBold.setOnClickListener(v -> applyStyle(Typeface.BOLD));

        // ITALIC
        binding.btnItalic.setOnClickListener(v -> applyStyle(Typeface.ITALIC));

        // UNDERLINE
        binding.btnUnderline.setOnClickListener(v -> applyUnderline());

        // ALIGN LEFT
        binding.btnAlignLeft.setOnClickListener(v -> applyAlignment(Gravity.START, "left"));

        // ALIGN CENTER
        binding.btnAlignCenter.setOnClickListener(v -> applyAlignment(Gravity.CENTER_HORIZONTAL, "center"));

        // ALIGN RIGHT
        binding.btnAlignRight.setOnClickListener(v -> applyAlignment(Gravity.END, "right"));

        // Toggle toolbar visibility
        binding.ibFormatting.setOnClickListener(v -> toggleFormattingToolbar());
    }

    private void toggleFormattingToolbar() {
        isFormattingToolbarVisible = !isFormattingToolbarVisible;
        binding.layoutFormattingToolbar.setVisibility(
                isFormattingToolbarVisible ? View.VISIBLE : View.GONE
        );

        int tint = ContextCompat.getColor(this,
                isFormattingToolbarVisible ? R.color.peri_primary : R.color.text_secondary);
        ImageViewCompat.setImageTintList(binding.ibFormatting, ColorStateList.valueOf(tint));
    }

    private void applyStyle(int style) {
        int s = binding.etContent.getSelectionStart();
        int e = binding.etContent.getSelectionEnd();
        if (s >= e) return;

        SpannableStringBuilder ssb = new SpannableStringBuilder(binding.etContent.getText());
        ssb.setSpan(new StyleSpan(style), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.etContent.setText(ssb, TextView.BufferType.SPANNABLE);
        binding.etContent.setSelection(e);
    }

    private void applyUnderline() {
        int s = binding.etContent.getSelectionStart();
        int e = binding.etContent.getSelectionEnd();
        if (s >= e) return;

        SpannableStringBuilder ssb = new SpannableStringBuilder(binding.etContent.getText());
        ssb.setSpan(new UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.etContent.setText(ssb, TextView.BufferType.SPANNABLE);
        binding.etContent.setSelection(e);
    }

    private void applyAlignment(int gravity, String alignHtml) {

        // VISUAL alignment (Android)
        binding.etContent.setGravity(gravity | Gravity.TOP);

        // WRAP HTML alignment so it saves correctly
        CharSequence current = binding.etContent.getText();
        String html = Html.toHtml((Spanned) current);

        String wrapped =
                "<div align=\"" + alignHtml + "\">" +
                        html +
                        "</div>";

        CharSequence sp = Html.fromHtml(wrapped, Html.FROM_HTML_MODE_LEGACY);
        binding.etContent.setText(sp, TextView.BufferType.SPANNABLE);
    }



    /* ==========================================================================================
     *  ATTACHMENT SYSTEM - FIXED TO SHOW RESULTS
     * ========================================================================================== */

    private void setupAttachmentUI() {
        attachmentAdapter = new AttachmentAdapter();
        binding.rvAttachments.setLayoutManager(new LinearLayoutManager(this));
        binding.rvAttachments.setAdapter(attachmentAdapter);

        // Ensure attachment container is visible when there are attachments
        noteViewModel.getAttachments(noteId).observe(this, attachments -> {
            boolean hasAttachments = attachments != null && !attachments.isEmpty();

            if (hasAttachments) {
                // Show attachment section - use layoutAttachmentSection if it exists
                if (binding.layoutAttachmentSection != null) {
                    binding.layoutAttachmentSection.setVisibility(View.VISIBLE);
                }

                binding.tvAttachmentsLabel.setVisibility(View.VISIBLE);
                binding.rvAttachments.setVisibility(View.VISIBLE);
                binding.tvAttachmentsEmpty.setVisibility(View.GONE);

                attachmentAdapter.setAttachments(attachments);
            } else {
                // Hide attachment section when empty
                if (binding.layoutAttachmentSection != null) {
                    binding.layoutAttachmentSection.setVisibility(View.GONE);
                }

                binding.tvAttachmentsLabel.setVisibility(View.GONE);
                binding.rvAttachments.setVisibility(View.GONE);
                binding.tvAttachmentsEmpty.setVisibility(View.GONE);
            }
        });

        attachmentAdapter.setOnAttachmentClickListener(new AttachmentAdapter.OnAttachmentClickListener() {
            @Override public void onAttachmentClick(Attachment att) { openAttachment(att); }
            @Override public void onAttachmentLongClick(Attachment att) { confirmDeleteAttachment(att); }
        });
    }

    private void addAttachment(Uri uri) {
        noteViewModel.addAttachmentToNote(this, noteId, uri);
        Toast.makeText(this, "Adding attachment...", Toast.LENGTH_SHORT).show();
    }

    private void openAttachment(Attachment att) {
        new Thread(() -> {
            byte[] bytes = noteViewModel.loadAttachmentBytes(this, att);
            if (bytes == null) {
                runOnUiThread(() -> Toast.makeText(this, "Failed loading", Toast.LENGTH_SHORT).show());
                return;
            }

            try {
                String mime = att.getMimeType();
                if (mime == null || mime.isEmpty())
                    mime = "*/*";

                File tmp = File.createTempFile("att_", ".tmp", getCacheDir());
                FileOutputStream fos = new FileOutputStream(tmp);
                fos.write(bytes);
                fos.close();

                Uri uri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        tmp
                );

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(uri, mime);

                startActivity(intent);

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void confirmDeleteAttachment(Attachment att) {
        new AlertDialog.Builder(this)
                .setTitle("Delete attachment?")
                .setPositiveButton("Delete", (d, w) -> noteViewModel.deleteAttachment(this, att))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ==========================================================================================
     *  QUICK ACTION ICONS
     * ========================================================================================== */

    // Ganti metode setupQuickActionIcons() dengan kode ini:

    private void setupQuickActionIcons() {
        // PERBAIKAN: Menggunakan ActivityResultLauncher yang sudah didefinisikan
        binding.ibAttachment.setOnClickListener(v -> {
            // Mime types yang sama dengan yang ada di openFilePicker()
            attachmentPicker.launch(new String[]{"image/*", "application/pdf", "audio/*"});
        });

        binding.ibPinned.setOnClickListener(v -> {
            binding.cbPinned.setChecked(!binding.cbPinned.isChecked());
            updatePinIcon();
            debouncedAutosaveNote();
        });


        binding.ibLock.setOnClickListener(v -> showLockDialog());
        binding.ibSave.setOnClickListener(v -> saveAndFinish());
        binding.ibShare.setOnClickListener(v -> shareNote());
    }

    private void updatePinIcon() {
        int color = ContextCompat.getColor(
                this,
                binding.cbPinned.isChecked() ? R.color.peri_primary : R.color.text_secondary
        );
        ImageViewCompat.setImageTintList(binding.ibPinned, ColorStateList.valueOf(color));
    }

    private void updateLockIcon() {
        int color = ContextCompat.getColor(
                this,
                binding.cbLock.isChecked() ? R.color.peri_primary : R.color.text_secondary
        );
        ImageViewCompat.setImageTintList(binding.ibLock, ColorStateList.valueOf(color));
    }


    private void updateAllIcons() {
        updateCategoryIcon();
        updatePinIcon();
        updateLockIcon();
    }

    /* ==========================================================================================
     *  LOCK SYSTEM
     * ========================================================================================== */

    private void showLockDialog() {
        EditText input = new EditText(this);
        input.setHint("Password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Lock note")
                .setMessage("Set password. Empty = unlock.")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String pwd = input.getText().toString().trim();
                    boolean locked = !pwd.isEmpty();

                    binding.cbLock.setChecked(locked);
                    binding.etLockPassword.setText(locked ? pwd : "");

                    updateLockIcon();
                    debouncedAutosaveNote();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ==========================================================================================
     *  SHARE SYSTEM
     * ========================================================================================== */

    private void shareNote() {
        String title = binding.etTitle.getText().toString();
        String content = binding.etContent.getText().toString();

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append(title);
        if (!content.isEmpty()) sb.append("\n\n").append(content);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");

        if (!title.isEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, title);

        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Share note via"));
    }

    /* ==========================================================================================
     *  SCROLL BUTTON
     * ========================================================================================== */

    private void setupScrollButton() {
        binding.btnScrollToTop.setAlpha(0f);
        binding.btnScrollToTop.setVisibility(View.GONE);

        binding.scrollContainer.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener)
                        (v, sx, sy, ox, oy) -> {
                            if (sy > SCROLL_BUTTON_THRESHOLD) showScrollButton();
                            else hideScrollButton();
                        });

        binding.btnScrollToTop.setOnClickListener(v ->
                binding.scrollContainer.smoothScrollTo(0, 0));
    }

    private void showScrollButton() {
        if (binding.btnScrollToTop.getVisibility() != View.VISIBLE) {
            binding.btnScrollToTop.setVisibility(View.VISIBLE);
            binding.btnScrollToTop.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(200).start();
        }
    }

    private void hideScrollButton() {
        if (binding.btnScrollToTop.getVisibility() == View.VISIBLE) {
            binding.btnScrollToTop.animate()
                    .alpha(0f).scaleX(0.8f).scaleY(0.8f)
                    .setDuration(200)
                    .withEndAction(() -> binding.btnScrollToTop.setVisibility(View.GONE))
                    .start();
        }
    }

    /* ==========================================================================================
     *  KEYBOARD LISTENER + SMART FOCUS
     * ========================================================================================== */

    private void setupKeyboardListener() {
        View root = binding.getRoot();

        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            root.getWindowVisibleDisplayFrame(r);

            int height = root.getRootView().getHeight();
            int keypad = height - r.bottom;

            boolean visible = keypad > height * 0.15;
            isKeyboardVisible = visible;

            if (visible && binding.etContent.hasFocus()) {
                scrollToCursor(binding.etContent);
            }
        });
    }

    private void scrollToCursor(EditText et) {
        int pos = et.getSelectionStart();
        if (pos < 0) return;

        android.text.Layout layout = et.getLayout();
        if (layout == null) return;

        int line = layout.getLineForOffset(pos);
        int top = layout.getLineTop(line);

        int[] loc = new int[2];
        et.getLocationInWindow(loc);

        binding.scrollContainer.smoothScrollTo(0, loc[1] + top - 200);
    }

    private void setupSmartFocus() {
        binding.etTitle.postDelayed(() -> {
            if (!isEditMode) binding.etTitle.requestFocus();
        }, 200);
    }

    /* ==========================================================================================
     *  AUTOSAVE LISTENERS
     * ========================================================================================== */

    private void setupAutosaveListeners() {

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { debouncedAutosaveNote(); }
        };

        binding.etTitle.addTextChangedListener(watcher);
        binding.etContent.addTextChangedListener(watcher);
        binding.etLockPassword.addTextChangedListener(watcher);

        binding.cbPinned.setOnCheckedChangeListener((b, c) -> debouncedAutosaveNote());
        binding.cbLock.setOnCheckedChangeListener((b, c) -> debouncedAutosaveNote());
    }

    private void debouncedAutosaveNote() {
        if (noteSaveRunnable != null)
            noteSaveHandler.removeCallbacks(noteSaveRunnable);

        noteSaveRunnable = this::doSaveNote;
        noteSaveHandler.postDelayed(noteSaveRunnable, SAVE_DELAY_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Finish pending todo saves
        for (Runnable r : pendingTodoSaves.values()) {
            todoSaveHandler.removeCallbacks(r);
            r.run();
        }
        pendingTodoSaves.clear();

        // Finish pending note save
        if (noteSaveRunnable != null) {
            noteSaveHandler.removeCallbacks(noteSaveRunnable);
            noteSaveRunnable.run();
            noteSaveRunnable = null;
        }
    }

    /* ==========================================================================================
     *  FILE PICKER
     * ========================================================================================== */

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");

        String[] mimeTypes = {"image/*", "application/pdf", "audio/*"};
        i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        startActivityForResult(i, REQUEST_CODE_PICK_ATTACHMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_ATTACHMENT
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            Uri uri = data.getData();
            noteViewModel.addAttachmentToNote(this, noteId, uri);
            Toast.makeText(this, "Attachment added", Toast.LENGTH_SHORT).show();
        }
    }
}
