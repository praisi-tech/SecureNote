package com.example.securenote.viewmodel;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.securenote.data.NoteRepository;
import com.example.securenote.model.Attachment;
import com.example.securenote.model.Note;
import com.example.securenote.model.TodoItem;
import com.example.securenote.util.PasswordUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoteViewModel extends AndroidViewModel {

    public static final String SORT_LAST_MODIFIED = "lastModified";
    public static final String SORT_TITLE_ASC = "titleAsc";

    private String currentSort = SORT_LAST_MODIFIED;
    private boolean pinnedFilterActive = false;
    // [BARU] Tambahkan variabel untuk filter kategori, default "All"
    private String currentCategoryFilter = "All";

    private final NoteRepository noteRepository;
    private final LiveData<List<Note>> allNotes;
    private final LiveData<List<Note>> trashNotes;
    private final LiveData<String> error;
    private final MutableLiveData<List<Note>> filteredNotes = new MutableLiveData<>();

    private List<Note> latestSearchResults = new ArrayList<>();

    public NoteViewModel(@NonNull Application application) {
        super(application);
        noteRepository = NoteRepository.getInstance(application);

        allNotes = noteRepository.getNotesLiveData();
        trashNotes = noteRepository.getTrashLiveData();
        error = noteRepository.getErrorLiveData();

        // Mengganti observasi: Agar filter/sort diterapkan saat data dimuat pertama kali
        allNotes.observeForever(notes -> {
            // Ketika allNotes berubah, reset search dan apply filter/sort
            searchNotes(null);
        });
    }

    // ---------------------- GETTERS ----------------------

    public LiveData<List<Note>> getAllNotes() { return allNotes; }

    public LiveData<List<Note>> getTrashNotes() { return trashNotes; }

    public LiveData<List<Note>> getFilteredNotes() { return filteredNotes; }

    public LiveData<String> getError() { return error; }

    public String getCurrentSort() { return currentSort; }

    public boolean isPinnedFilterActive() { return pinnedFilterActive; }

    // [BARU] Getter untuk Category Filter
    public String getCurrentCategoryFilter() { return currentCategoryFilter; }

    public LiveData<List<Note>> getPinnedNotes() {
        return Transformations.map(allNotes, notes -> {
            if (notes == null) return null;
            List<Note> pinned = new ArrayList<>();
            for (Note n : notes) {
                if (n.isPinned()) pinned.add(n);
            }
            return pinned;
        });
    }

    public LiveData<List<Note>> getNotesByCategory(String category) {
        // Metode ini tidak lagi digunakan untuk filter utama, tapi tetap dipertahankan
        // jika ada kegunaan lain di repository.
        return noteRepository.getNotesByCategory(category);
    }

    // ---------------------- SEARCH & FILTER ----------------------

    public void searchNotes(String query) {
        List<Note> notes = allNotes.getValue();
        if (notes == null) return;

        // Note: Disini kita search pada SEMUA catatan aktif (allNotes.getValue())
        // sebelum filtering kategori diterapkan.

        if (query == null || query.trim().isEmpty()) {
            latestSearchResults = notes;
        } else {
            String lower = query.toLowerCase();
            List<Note> results = new ArrayList<>();
            for (Note n : notes) {
                boolean matchTitle = n.getTitle() != null && n.getTitle().toLowerCase().contains(lower);
                boolean matchContent = n.getContent() != null && n.getContent().toLowerCase().contains(lower);
                if (matchTitle || matchContent) results.add(n);
            }
            latestSearchResults = results;
        }
        applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        // 1. Inisialisasi daftar dengan hasil pencarian/semua catatan
        List<Note> list = new ArrayList<>(latestSearchResults);

        // ===========================================
        // ⬇️ LANGKAH 1: FILTER (Mengurangi jumlah item) ⬇️
        // ===========================================

        // 1. PINNED FILTER
        if (pinnedFilterActive) {
            list.removeIf(note -> !note.isPinned());
        }

        // 2. CATEGORY FILTER (Memfilter hanya pada item yang tersisa setelah Pinned Filter)
        if (currentCategoryFilter != null && !currentCategoryFilter.equals("All")) {
            list.removeIf(note -> {
                String category = note.getCategory() != null ? note.getCategory() : "";
                return !currentCategoryFilter.equals(category);
            });
        }

        // ===========================================
        // ⬇️ LANGKAH 2: SORTING (Mengurutkan item yang tersisa) ⬇️
        // ===========================================

        Collections.sort(list, (n1, n2) -> {

            // Prioritas Utama: Selalu tempatkan catatan yang di-pin di atas,
            // TERLEPAS dari mode pengurutan (Sort) yang dipilih, kecuali jika Pinned Filter aktif.
            if (n1.isPinned() != n2.isPinned()) {
                return n1.isPinned() ? -1 : 1; // n1 is pinned -> put n1 first (-1)
            }

            // Prioritas Kedua: Terapkan pengurutan berdasarkan mode yang dipilih
            switch (currentSort) {
                case SORT_TITLE_ASC:
                    // Sort Title: Pastikan null handling tetap ada
                    return n1.getTitle() == null ? 1 :
                            (n2.getTitle() == null ? -1 :
                                    n1.getTitle().compareToIgnoreCase(n2.getTitle()));

                case SORT_LAST_MODIFIED:
                default:
                    // Sort Last Modified: Menggunakan waktu terbaru (n2) dikurangi waktu lama (n1) untuk DESCENDING
                    // (Asumsi getTimestamp() menyimpan waktu modifikasi terakhir)
                    return Long.compare(n2.getTimestamp(), n1.getTimestamp());
            }
        });

        // Perbarui LiveData
        filteredNotes.setValue(list);
    }

    public void togglePinnedFilter(boolean active) {
        this.pinnedFilterActive = active;
        applyFilterAndSort();
    }

    // [BARU] Setter untuk Category Filter
    public void setCategoryFilter(String category) {
        if (category == null || category.trim().isEmpty()) {
            category = "All";
        }
        if (!currentCategoryFilter.equals(category)) {
            currentCategoryFilter = category;
            applyFilterAndSort();
        }
    }

    public void setSortOrder(String sort) {
        if (!sort.equals(currentSort)) {
            currentSort = sort;
            applyFilterAndSort();
        }
    }

    // [DIHAPUS/DIUBAH] Hapus atau ubah metode ini karena kita menggunakan setCategoryFilter() dan applyFilterAndSort()
    /*
    public void filterByCategory(String category) {
        if (category == null || category.equals("All")) {
            searchNotes(null);
        } else {
            noteRepository.getNotesByCategory(category).observeForever(filteredNotes::setValue);
        }
    }
    */

    // ---------------------- NOTE ACTIONS ----------------------

    public void addNote(Note note) { noteRepository.addNote(note); }

    public void updateNote(Note note) { noteRepository.updateNote(note); }

    public void deleteNote(Note note) { deleteNote(note); }

    public void moveToTrash(Note note) { noteRepository.moveToTrash(note); }

    public void restoreFromTrash(Note note) { noteRepository.restoreFromTrash(note); }

    public void deleteFromTrash(Note note) { noteRepository.deleteFromTrash(note); }

    // ---------------------- TODO ACTIONS ----------------------

    public LiveData<List<TodoItem>> getTodosByNoteId(String noteId) {
        return noteRepository.getTodosByNote(noteId);
    }

    public void addTodoItem(TodoItem item) {
        noteRepository.addTodoItem(item);
    }

    public void updateTodoItem(TodoItem item) {
        noteRepository.updateTodoItem(item);
    }

    public void deleteTodoItem(TodoItem item) {
        noteRepository.deleteTodoItem(item);
    }

    public void deleteTodosByNoteId(String noteId) {
        noteRepository.deleteTodosByNoteId(noteId);
    }

    // ---------------------- ATTACHMENT ACTIONS ----------------------

    public LiveData<List<Attachment>> getAttachments(String noteId) {
        return noteRepository.getAttachmentsLiveData(noteId);
    }

    public void addAttachmentToNote(Context context, String noteId, Uri uri) {
        noteRepository.addAttachmentToNote(context, noteId, uri);
    }

    public void deleteAttachment(Context context, Attachment attachment) {
        noteRepository.deleteAttachment(context, attachment);
    }

    public byte[] loadAttachmentBytes(Context context, Attachment attachment) {
        return noteRepository.loadAttachmentBytes(context, attachment);
    }

    // ---------------------- PASSWORD ----------------------

    public boolean isPasswordCorrect(String raw, String hashed) {
        return PasswordUtil.verifyLockPassword(raw, hashed);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        noteRepository.removeListener();
    }
}