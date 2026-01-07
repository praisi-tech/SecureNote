package com.example.securenote.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.text.InputType;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


import com.example.securenote.MyApp;
import com.example.securenote.R;
import com.example.securenote.databinding.ActivityMainBinding;
import com.example.securenote.model.Note;
import com.example.securenote.viewmodel.NoteViewModel;
import com.example.securenote.viewmodel.ProfileViewModel;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.io.File;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;



public class MainActivity extends AppCompatActivity
        implements com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener {


    private ActivityMainBinding binding;
    private NoteAdapter noteAdapter;
    private NoteViewModel noteViewModel;
    private ProfileViewModel profileViewModel;

    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private com.google.android.material.navigation.NavigationView navigationView;

    private ImageView ivHeaderAvatar;
    private TextView tvHeaderName;
    private TextView tvHeaderEmail;

    private final Set<String> unlockedNotes = new HashSet<>();
    private static final String PREFS_UI = "ui_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private Toolbar toolbar;

    // Untuk tracking kategori yang sedang aktif
    private String currentCategory = "All";
    // PATCH: receives category from AddEditNoteActivity
    private final ActivityResultLauncher<Intent> noteEditorLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                            String updatedCategory = result.getData().getStringExtra("note_category");

                            if (updatedCategory != null) {

                                currentCategory = updatedCategory;

                                ChipGroup cg = findViewById(R.id.chipGroupCategories);

                                // Highlight UI
                                highlightChipByText(cg, updatedCategory);

                                // Apply ViewModel filter
                                noteViewModel.setCategoryFilter(updatedCategory);
                            }
                        }
                    }
            );

    private final ActivityResultLauncher<Intent> exportBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                exportBackupToUri(uri);
                            }
                        }
                    }
            );

    private final ActivityResultLauncher<Intent> importBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                importBackupFromUri(uri);
                            }
                        }
                    }
            );

    // Cache current notes untuk optimistic delete
    private List<Note> currentNotes = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SharedPreferences prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE);
        boolean dark = prefs.getBoolean(KEY_DARK_MODE, false);

        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);

        drawerLayout = binding.drawerLayout;
        navigationView = binding.navigationView;

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        setupNavHeaderProfile();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("");
        }

        noteAdapter = new NoteAdapter();
        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);

        binding.rvNotes.setLayoutManager(layoutManager);
        binding.rvNotes.setAdapter(noteAdapter);

        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        noteViewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        // [BARU/UBAH] Observer utama sekarang hanya pada filteredNotes
        noteViewModel.getFilteredNotes().observe(this, this::updateNotesDisplay);

        // Setup chip group untuk kategori
        setupCategoryChips();

        // Note click listeners
        noteAdapter.setOnNoteClickListener(new NoteAdapter.OnNoteClickListener() {
            @Override
            public void onNoteClick(Note note) {
                if (note.isLocked()) {
                    if (note.getId() != null && unlockedNotes.contains(note.getId())) {
                        openNoteEditor(note);
                    } else {
                        showUnlockDialog(note);
                    }
                } else {
                    openNoteEditor(note);
                }
            }

            @Override
            public void onNoteLongClick(Note note) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete Note")
                        .setMessage("Move this note to Trash?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            // OPTIMISTIC DELETE - Update UI immediately
                            deleteNoteOptimistically(note);

                            // Then update database
                            noteViewModel.moveToTrash(note);

                            Snackbar.make(binding.getRoot(), "Moved to Trash", Snackbar.LENGTH_LONG)
                                    .setAction("UNDO", v -> {
                                        // Restore in database
                                        noteViewModel.restoreFromTrash(note);
                                        // UI will be updated by observer
                                    })
                                    .show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        binding.fabAddNote.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, AddEditNoteActivity.class);
            noteEditorLauncher.launch(i);   // PATCH
        });

        // Panggil loadCategoryData di onCreate untuk memuat data awal dengan filter default
        loadCategoryData(currentCategory);
    }

    /**
     * Optimistic delete - update UI immediately before database finishes
     */
    private void deleteNoteOptimistically(Note note) {
        if (currentNotes == null || currentNotes.isEmpty()) return;

        // Create new list without the deleted note
        List<Note> updatedNotes = new ArrayList<>();
        for (Note n : currentNotes) {
            if (n.getId() == null || note.getId() == null || !n.getId().equals(note.getId())) {
                updatedNotes.add(n);
            }
        }

        // Update UI immediately
        updateNotesDisplay(updatedNotes);
    }

    private void setupCategoryChips() {
        ChipGroup chipGroup = findViewById(R.id.chipGroupCategories);
        chipGroup.removeAllViews();

        String[] allFilters = {
                "All", "Pinned", "Work", "Personal", "Urgent", "Study", "Goals", "Tasks"
        };

        for (String cat : allFilters) {
            Chip chip = new Chip(this);
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setClickable(true);

            // ===== PERUBAHAN UTAMA: BORDER LIGHT =====
            // Set stroke/border dengan warna light gray
            chip.setChipStrokeWidth(2f); // 1dp stroke width
            chip.setChipStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.chip_stroke_light)
            ));

            // Background color
            chip.setChipBackgroundColorResource(R.color.chip_bg_selector);

            // Text color
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            chip.setCheckedIconVisible(false);
            chip.setRippleColorResource(R.color.chip_ripple);

            // Tambahan styling untuk lebih cantik
            chip.setChipCornerRadius(48f); // 18dp corner radius
            chip.setChipMinHeight(96f); // 38dp min height
            chip.setTextSize(14f);

            chipGroup.addView(chip);
        }

        // Default: "All" aktif
        Chip allChip = (Chip) chipGroup.getChildAt(0);
        if (allChip != null) {
            allChip.setChecked(true);
            highlightSelectedChip(chipGroup, allChip);
        }

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                Chip defaultChip = (Chip) group.getChildAt(0);
                if (defaultChip != null) defaultChip.setChecked(true);
                return;
            }

            Chip selected = group.findViewById(checkedId);
            if (selected != null) {
                String filterName = selected.getText().toString();

                if (filterName.equals("Pinned")) {
                    boolean active = selected.isChecked();
                    noteViewModel.togglePinnedFilter(active);

                    if (selected.isChecked()) {
                        highlightSelectedChip(group, selected);
                    } else {
                        loadCategoryData(currentCategory);
                    }

                } else {
                    currentCategory = filterName;
                    noteViewModel.togglePinnedFilter(false);

                    Chip pinnedChip = null;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        Chip chip = (Chip) group.getChildAt(i);
                        if (chip.getText().toString().equals("Pinned")) {
                            pinnedChip = chip;
                            break;
                        }
                    }
                    if (pinnedChip != null && pinnedChip.isChecked()) {
                        pinnedChip.setChecked(false);
                    }

                    highlightSelectedChip(group, selected);
                    animateRecyclerViewChange();
                    loadCategoryData(currentCategory);
                }
            }
        });
    }

    /**
     * Load data based on selected category
     */
    private void loadCategoryData(String category) {
        // [UBAH TOTAL]
        // Sekarang kita HANYA perlu memanggil setter di ViewModel.
        // ViewModel akan menerapkan filter, dan observer pada getFilteredNotes()
        // akan otomatis memanggil updateNotesDisplay().

        currentCategory = category;
        noteViewModel.setCategoryFilter(category);
    }

    private void updateNotesDisplay(List<Note> notes) {
        // Cache current notes for optimistic delete
        if (notes != null) {
            currentNotes = new ArrayList<>(notes);

            for (Note note : currentNotes) {
                note.setContentHidden(
                        note.isLocked() &&
                                (note.getId() == null || !unlockedNotes.contains(note.getId()))
                );
            }
        } else {
            currentNotes = new ArrayList<>();
        }

        noteAdapter.setNotes(currentNotes);

        if (currentNotes.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.rvNotes.setVisibility(View.GONE);
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
            binding.rvNotes.setVisibility(View.VISIBLE);
        }
    }

    private void animateRecyclerViewChange() {
        RecyclerView rv = findViewById(R.id.rvNotes);
        rv.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> {
                    rv.animate()
                            .alpha(1f)
                            .setDuration(200)
                            .start();
                })
                .start();
    }

    private void highlightSelectedChip(ChipGroup chipGroup, Chip selected) {
        // [UBAH] Pastikan hanya satu chip kategori yang ter-highlight, dan Pinned bisa berdiri sendiri.
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);

            String chipText = chip.getText().toString();

            // Kategori (All, Work, Personal, dll) - hanya satu yang bisa aktif
            if (!chipText.equals("Pinned")) {
                boolean isSelected = chip == selected;

                int bgColor = ContextCompat.getColor(this,
                        isSelected ? R.color.peri_primary : R.color.pastel_default);
                int textColor = ContextCompat.getColor(this,
                        isSelected ? R.color.white : R.color.text_primary);

                chip.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
                chip.setTextColor(textColor);

                if (isSelected) {
                    chipGroup.post(() -> chipGroup.requestChildFocus(chip, chip));
                }
            }
            // Untuk chip Pinned, warnanya hanya berubah berdasarkan status checked, bukan berdasarkan chip lain.
            // Biarkan Pinned menggunakan selector bawaan atau pastikan chip.setChecked(true)
        }
    }

    /**
     * Highlights the chip whose text matches the provided category.
     * This is needed when returning to the activity to maintain UI state.
     */
    private void highlightChipByText(ChipGroup chipGroup, String category) {
        if (chipGroup == null || category == null) return;

        Chip selectedCategoryChip = null;
        Chip pinnedChip = null;

        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            String chipText = chip.getText().toString();

            if (chipText.equals("Pinned")) {
                pinnedChip = chip;
                // Tetapkan status checked untuk Pinned berdasarkan ViewModel
                chip.setChecked(noteViewModel.isPinnedFilterActive());
                continue;
            }

            if (category.equals(chipText)) {
                selectedCategoryChip = chip;
                chip.setChecked(true);
            } else {
                chip.setChecked(false);
            }
        }

        if (selectedCategoryChip != null) {
            // Panggil metode highlight yang sudah ada untuk update warna visual dan scroll
            highlightSelectedChip(chipGroup, selectedCategoryChip);
        } else {
            // Default ke "All" jika kategori tidak ditemukan
            Chip allChip = (Chip) chipGroup.getChildAt(0);
            if (allChip != null) {
                currentCategory = "All";
                allChip.setChecked(true);
                highlightSelectedChip(chipGroup, allChip);
            }
        }
    }


    // ----------------------------------------------------------
    //                PROFILE UI – FIXED REFRESH
    // ----------------------------------------------------------
    private void setupNavHeaderProfile() {
        if (navigationView == null) return;

        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) return;

        ivHeaderAvatar = headerView.findViewById(R.id.ivHeaderAvatar);
        tvHeaderName = headerView.findViewById(R.id.tvHeaderName);
        tvHeaderEmail = headerView.findViewById(R.id.tvHeaderEmail);

        ivHeaderAvatar.setImageResource(R.drawable.ic_person);
        tvHeaderName.setText("My Secure Notes");
        tvHeaderEmail.setText("Tap to set up your profile");

        profileViewModel = new ViewModelProvider(
                (MyApp) getApplication(),
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())
        ).get(ProfileViewModel.class);

        profileViewModel.getProfile().observe(this, profile -> {
            if (profile == null) {
                ivHeaderAvatar.setImageResource(R.drawable.ic_person);
                tvHeaderName.setText("My Secure Notes");
                tvHeaderEmail.setText("Tap to set up your profile");
                return;
            }

            if (profile.displayName != null && !profile.displayName.trim().isEmpty()) {
                tvHeaderName.setText(profile.displayName);
            } else {
                tvHeaderName.setText("My Secure Notes");
            }

            String bio = profile.bio != null ? profile.bio.trim() : "";
            if (!bio.isEmpty()) {
                tvHeaderEmail.setText(bio);
            } else {
                tvHeaderEmail.setText("Tap to set up your profile");
            }

            loadHeaderAvatarSafely(profile.avatarPath);
        });

        headerView.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class))
        );
    }

    private void loadHeaderAvatarSafely(String avatarPath) {
        ivHeaderAvatar.setImageResource(R.drawable.ic_person);

        if (avatarPath == null || avatarPath.isEmpty()) return;

        File file = new File(avatarPath);
        if (!file.exists()) return;

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Uri freshUri = fileUri.buildUpon()
                    .appendQueryParameter("t", String.valueOf(System.currentTimeMillis()))
                    .build();

            ivHeaderAvatar.setImageURI(null);
            ivHeaderAvatar.setImageURI(freshUri);

        } catch (Exception e) {
            e.printStackTrace();
            ivHeaderAvatar.setImageResource(R.drawable.ic_person);
        }
    }

    // ----------------------------------------------------------
    //                      OPTIONS MENU
    // ----------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                noteViewModel.searchNotes(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                noteViewModel.searchNotes(newText);
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) { return true; }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                // Reload current category when search is closed
                noteViewModel.searchNotes(null); // Reset search query
                // loadCategoryData(currentCategory); // Tidak perlu dipanggil, searchNotes(null) akan memicu applyFilterAndSort()
                return true;
            }
        });


        return true;
    }

    /**
     * Search within current category filter
     */
    private void searchInCurrentCategory(String query) {
        // Logika ini sudah tidak digunakan, digantikan oleh noteViewModel.searchNotes(query)
        // yang bekerja pada SEMUA catatan dan kemudian diterapkan filter kategori oleh applyFilterAndSort()
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_trash) {
            startActivity(new Intent(this, TrashActivity.class));
            return true;
        } else {
            // else = nothing happens
            return false;
        }
    }


    // ----------------------------------------------------------
    //                     UNLOCK NOTE DIALOG
    // ----------------------------------------------------------

    private void showUnlockDialog(Note note) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Note password");

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, padding, padding, padding);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Unlock note")
                .setView(container)
                .setPositiveButton("Unlock", (dialog, which) -> {
                    String entered = input.getText().toString();

                    boolean isCorrect = noteViewModel.isPasswordCorrect(
                            entered,
                            note.getLockPassword()
                    );

                    if (isCorrect) {
                        if (note.getId() != null) unlockedNotes.add(note.getId());
                        // loadCategoryData(currentCategory); // Panggil applyFilterAndSort untuk refresh konten
                        noteViewModel.setCategoryFilter(currentCategory);
                        openNoteEditor(note);
                    } else {
                        Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openNoteEditor(Note note) {
        Intent intent = new Intent(MainActivity.this, AddEditNoteActivity.class);
        intent.putExtra(AddEditNoteActivity.EXTRA_ID, note.getId());
        intent.putExtra(AddEditNoteActivity.EXTRA_TITLE, note.getTitle());
        intent.putExtra(AddEditNoteActivity.EXTRA_CONTENT, note.getContent());
        intent.putExtra(AddEditNoteActivity.EXTRA_PINNED, note.isPinned());
        intent.putExtra(AddEditNoteActivity.EXTRA_LOCKED, note.isLocked());
        intent.putExtra(AddEditNoteActivity.EXTRA_LOCK_PASSWORD_HASH, note.getLockPassword());
        // Tambahkan kategori agar AddEditNoteActivity dapat memuatnya
        intent.putExtra("note_category", note.getCategory());
        noteEditorLauncher.launch(intent);
    }

    // ----------------------------------------------------------
    //                       NAVIGATION MENU
    // ----------------------------------------------------------

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_change_password) {
            startActivity(new Intent(this, ChangePasswordActivity.class));

        } else if (id == R.id.nav_rate_app) {
            openRateApp();

        } else if (id == R.id.nav_faqs) {
            startActivity(new Intent(this, FaqActivity.class));

        } else if (id == R.id.nav_help_feedback) {
            startActivity(new Intent(this, HelpFeedbackActivity.class));

        } else if (id == R.id.nav_export_backup) {
            // FIXED: Call exportBackup() directly
            exportBackup();

        } else if (id == R.id.nav_import_backup) {
            // FIXED: Launch import intent directly
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importBackupLauncher.launch(intent);

        } else if (id == R.id.nav_logout) {
            unlockedNotes.clear();
            Intent intent = new Intent(this, LockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void toggleTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE);
        boolean dark = prefs.getBoolean(KEY_DARK_MODE, false);

        boolean newDark = !dark;
        prefs.edit().putBoolean(KEY_DARK_MODE, newDark).apply();

        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
        );

        recreate();
    }

    private void openRateApp() {
        String packageName = getPackageName();

        Uri uri = Uri.parse("market://details?id=" + packageName);
        Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
        goToMarket.addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY |
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        );

        try {
            startActivity(goToMarket);
        } catch (ActivityNotFoundException e) {
            Uri webUri = Uri.parse("https://play.google.com/store/apps/details?id=" + packageName);
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (noteViewModel != null) {
            ChipGroup chipGroup = findViewById(R.id.chipGroupCategories);

            // FIX: Panggil highlightChipByText untuk memastikan chip yang benar ter-highlight
            // Menggunakan nilai filter dari ViewModel sebagai Single Source of Truth
            String activeCategory = noteViewModel.getCurrentCategoryFilter();
            boolean pinnedActive = noteViewModel.isPinnedFilterActive();

            highlightChipByText(chipGroup, activeCategory);

            // Reload current category when returning to MainActivity
            // loadCategoryData(currentCategory); // Tidak perlu dipanggil, highlightChipByText sudah memicu filter melalui setChecked -> setOnCheckedChangeListener. Jika tidak, panggil di sini:
            noteViewModel.setCategoryFilter(activeCategory); // Memastikan filter aktif
            if (pinnedActive) {
                noteViewModel.togglePinnedFilter(true); // Memastikan filter pinned aktif
            }
        }
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "securenote_backup_" +
                System.currentTimeMillis() + ".json");
        exportBackupLauncher.launch(intent);
    }

    /**
     * Export backup to selected URI - FIXED
     */
    private void exportBackupToUri(Uri uri) {
        noteViewModel.getAllNotes().observe(this, notes -> {
            if (notes == null || notes.isEmpty()) {
                Toast.makeText(this, "No notes to export", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                org.json.JSONArray jsonArray = new org.json.JSONArray();
                for (Note note : notes) {
                    org.json.JSONObject jsonNote = new org.json.JSONObject();

                    // Only export basic fields that exist in Note class
                    if (note.getId() != null) jsonNote.put("id", note.getId());
                    if (note.getTitle() != null) jsonNote.put("title", note.getTitle());
                    if (note.getContent() != null) jsonNote.put("content", note.getContent());
                    if (note.getCategory() != null) jsonNote.put("category", note.getCategory());

                    jsonNote.put("pinned", note.isPinned());
                    jsonNote.put("locked", note.isLocked());

                    if (note.getLockPassword() != null) {
                        jsonNote.put("lockPassword", note.getLockPassword());
                    }

                    jsonNote.put("lastModified", note.getLastModified());

                    // REMOVED: isDeleted() - this method doesn't exist in Note class
                    // Only active notes are exported anyway (not trash)

                    jsonArray.put(jsonNote);
                }

                java.io.OutputStream output = getContentResolver().openOutputStream(uri);
                if (output != null) {
                    output.write(jsonArray.toString(2).getBytes());
                    output.close();

                    Toast.makeText(this,
                            "✓ Backup exported: " + notes.size() + " notes",
                            Toast.LENGTH_LONG).show();
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Export failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Import backup from selected URI - FIXED
     */
    private void importBackupFromUri(Uri uri) {
        try {
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show();
                return;
            }

            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();

            org.json.JSONArray jsonArray = new org.json.JSONArray(stringBuilder.toString());
            int importedCount = 0;

            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject jsonNote = jsonArray.getJSONObject(i);

                String title = jsonNote.optString("title", "Untitled");
                String content = jsonNote.optString("content", "");

                // FIXED: Use empty Note constructor then set fields
                Note note = new Note();
                note.setTitle(title);
                note.setContent(content);

                try {
                    String category = jsonNote.optString("category", "Personal");
                    note.setCategory(category);
                } catch (Exception e) { }

                try {
                    boolean pinned = jsonNote.optBoolean("pinned", false);
                    note.setPinned(pinned);
                } catch (Exception e) { }

                try {
                    boolean locked = jsonNote.optBoolean("locked", false);
                    note.setLocked(locked);

                    if (locked && jsonNote.has("lockPassword")) {
                        String lockPassword = jsonNote.getString("lockPassword");
                        note.setLockPassword(lockPassword);
                    }
                } catch (Exception e) { }

                // FIXED: Use addNote() from NoteViewModel
                noteViewModel.addNote(note);
                importedCount++;
            }

            Toast.makeText(this,
                    "✓ Import successful: " + importedCount + " notes",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Import failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
}