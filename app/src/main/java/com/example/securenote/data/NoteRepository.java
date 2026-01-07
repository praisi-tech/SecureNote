package com.example.securenote.data;

import android.content.Context;
import android.os.Environment;
import android.net.Uri;
import android.util.Base64;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.securenote.model.Note;
import com.example.securenote.util.BackupUtils;
import com.example.securenote.util.EncryptionUtil;
import com.example.securenote.model.Attachment;
import com.example.securenote.model.TodoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;




public class NoteRepository {

    private static NoteRepository INSTANCE;

    private final NoteDao noteDao;
    private final AttachmentDao attachmentDao;
    private final ExecutorService executor;
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();

    // LiveData from Room (encrypted in DB)
    private final LiveData<List<Note>> activeNotesEncrypted;
    private final LiveData<List<Note>> trashNotesEncrypted;

    // LiveData exposed to ViewModel (decrypted for UI)
    private final LiveData<List<Note>> activeNotesDecrypted;
    private final LiveData<List<Note>> trashNotesDecrypted;
    private final TodoDao todoDao;


    private NoteRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.noteDao = db.noteDao();
        this.attachmentDao = db.attachmentDao();
        this.todoDao = db.todoDao();
        this.executor = Executors.newSingleThreadExecutor();

        // DAO returns encrypted entities directly from Room
        activeNotesEncrypted = noteDao.getActiveNotes();
        trashNotesEncrypted = noteDao.getTrashNotes();

        // Repository maps them to decrypted Note objects for the UI
        activeNotesDecrypted =
                Transformations.map(activeNotesEncrypted, this::decryptListForDisplay);
        trashNotesDecrypted =
                Transformations.map(trashNotesEncrypted, this::decryptListForDisplay);
    }

    public static synchronized NoteRepository getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new NoteRepository(context.getApplicationContext());
        }
        return INSTANCE;
    }

    // ---- LiveData exposed to ViewModel/UI ----

    public LiveData<List<Note>> getNotesLiveData() {
        return activeNotesDecrypted;
    }

    public LiveData<List<Note>> getTrashLiveData() {
        return trashNotesDecrypted;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }
    public LiveData<List<Attachment>> getAttachmentsLiveData(String noteId) {
        return attachmentDao.getForNoteLive(noteId);
    }


    // ----------------- Encryption helpers -----------------

    // Encrypt a UI note before storing it in Room
    private Note encryptForStorage(Note plain) {
        if (plain == null) return null;

        Note stored = new Note();
        stored.setId(plain.getId());
        stored.setTimestamp(plain.getTimestamp());
        stored.setPinned(plain.isPinned());
        stored.setLocked(plain.isLocked());
        stored.setLockPassword(plain.getLockPassword());
        stored.setInTrash(plain.isInTrash());

        // PERBAIKAN KRITIS UNTUK KATEGORI: Salin kategori agar tersimpan di Room.
        stored.setCategory(plain.getCategory());

        String title = plain.getTitle() != null ? plain.getTitle() : "";
        String content = plain.getContent() != null ? plain.getContent() : "";

        // AES-GCM via CryptoManager – requires key initialized in LockActivity
        stored.setEncryptedTitle(EncryptionUtil.encrypt(title));
        stored.setEncryptedContent(EncryptionUtil.encrypt(content));

        // Plaintext title/content are NOT stored in DB
        return stored;
    }

    // NoteRepository.java

    // Decrypt a stored Note from Room for UI usage
    private Note decryptForDisplay(Note stored) {
        if (stored == null) return null;

        // Create a UI Note object using decrypted data
        Note ui = new Note(
                stored.getId(),
                "",   // title will be set below
                "",   // content will be set below
                stored.getTimestamp(),
                stored.isPinned()
        );

        ui.setLocked(stored.isLocked());
        ui.setLockPassword(stored.getLockPassword());
        ui.setInTrash(stored.isInTrash());

        // PERBAIKAN: Salin nilai kategori dari objek yang tersimpan ke objek UI
        ui.setCategory(stored.getCategory());

        String cipherTitle = stored.getEncryptedTitle();
        String cipherContent = stored.getEncryptedContent();

        // Be defensive: never let decrypt exceptions crash the app
        if (cipherTitle != null) {
            try {
                ui.setTitle(EncryptionUtil.decrypt(cipherTitle));
            } catch (Exception e) {
                // Wrong key / corrupted data / mid-migration: show placeholder instead of crashing
                ui.setTitle("[Decryption error]");
            }
        }

        if (cipherContent != null) {
            try {
                ui.setContent(EncryptionUtil.decrypt(cipherContent));
            } catch (Exception e) {
                ui.setContent("[Decryption error]");
            }
        }

        return ui;
    }



    private static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }


    // Map list<Note> from DB → decrypted list<Note> for UI
    private List<Note> decryptListForDisplay(List<Note> storedList) {
        List<Note> result = new ArrayList<>();
        if (storedList == null) return result;

        for (Note n : storedList) {
            result.add(decryptForDisplay(n));
        }
        return result;
    }

    // ----------------- CRUD methods -----------------

    public void addNote(Note note) {
        executor.execute(() -> {
            try {
                if (note.getId() == null || note.getId().trim().isEmpty()) {
                    note.setId(UUID.randomUUID().toString());
                }
                note.setTimestamp(System.currentTimeMillis());

                Note enc = encryptForStorage(note);
                noteDao.insert(enc);

            } catch (Exception e) {
                errorLiveData.postValue("Failed to add note: " + e.getMessage());
            }
        });
    }

    public void updateNote(Note note) {
        executor.execute(() -> {
            try {
                note.setTimestamp(System.currentTimeMillis());
                Note enc = encryptForStorage(note);
                noteDao.update(enc);
            } catch (Exception e) {
                errorLiveData.postValue("Failed to update note: " + e.getMessage());
            }
        });
    }

    public void deleteNote(Note note) {
        executor.execute(() -> {
            try {
                // Only id really matters for delete; we can re-encrypt for consistency
                Note enc = encryptForStorage(note);
                noteDao.delete(enc);
            } catch (Exception e) {
                errorLiveData.postValue("Failed to delete note: " + e.getMessage());
            }
        });
    }

    public void moveToTrash(Note note) {
        executor.execute(() -> {
            try {
                noteDao.moveToTrash(note.getId());
            } catch (Exception e) {
                errorLiveData.postValue("Failed to move to trash: " + e.getMessage());
            }
        });
    }

    public void restoreFromTrash(Note note) {
        executor.execute(() -> {
            try {
                noteDao.restoreFromTrash(note.getId());
            } catch (Exception e) {
                errorLiveData.postValue("Failed to restore from trash: " + e.getMessage());
            }
        });
    }

    public void deleteFromTrash(Note note) {
        executor.execute(() -> {
            try {
                noteDao.deleteFromTrash(note.getId());
            } catch (Exception e) {
                errorLiveData.postValue("Failed to delete from trash: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronous backup export.
     * Call this from a background thread (NOT main/UI thread).
     * Returns the absolute file path of the created backup file.
     */
    public String exportBackupSync(Context context, String backupPassword) {
        try {
            // 1) Get all notes from DB in encrypted form (as stored in Room)
            List<Note> activeEnc = noteDao.getActiveNotesNow();
            List<Note> trashEnc  = noteDao.getTrashNotesNow();

            List<Note> allEnc = new ArrayList<>();
            if (activeEnc != null) allEnc.addAll(activeEnc);
            if (trashEnc  != null) allEnc.addAll(trashEnc);

            // 2) JSON (still encryptedTitle/encryptedContent)
            String json = BackupUtils.notesToJson(allEnc);

            // 3) Encrypt JSON with backup password
            String encrypted = BackupUtils.encryptBackup(json, backupPassword);

            // 4) Save into PUBLIC Downloads folder instead of Android/data
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
            );
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = "SecureNoteBackup_" + System.currentTimeMillis() + ".enc";
            File outFile = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(outFile);
            fos.write(encrypted.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            fos.close();

            return outFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("Backup export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronous backup import.
     * Decrypts the backup with backupPassword and writes notes into Room.
     * MUST be called from a background thread.
     */
    public void importBackupSync(Context context, String encryptedBase64, String backupPassword) {
        try {
            // 1) Decrypt outer backup (salt + iv + ciphertext)
            String json = BackupUtils.decryptBackup(encryptedBase64, backupPassword);

            // 2) Parse JSON -> list of Note objects with encryptedTitle/encryptedContent set
            List<Note> imported = BackupUtils.jsonToNotes(json);

            if (imported == null || imported.isEmpty()) {
                return; // nothing to do
            }

            // 3) Insert or replace into DB, as-is (they already contain encrypted fields)
            //    We are not touching EncryptionUtil here.
            for (Note n : imported) {
                // Defensive: if ID is missing for some reason, generate one
                if (n.getId() == null || n.getId().trim().isEmpty()) {
                    n.setId(java.util.UUID.randomUUID().toString());
                }
                noteDao.insert(n);  // assuming @Insert(onConflict = REPLACE)
            }

        } catch (Exception e) {
            throw new RuntimeException("Backup import failed: " + e.getMessage(), e);
        }
    }

    /**
     * Add an attachment to a note.
     *
     * This will:
     * 1) Read the original file from the given Uri
     * 2) Encrypt its bytes using EncryptionUtil
     * 3) Store the encrypted data in an internal file
     * 4) Insert an Attachment row with metadata
     */
    public void addAttachmentToNote(Context context, String noteId, Uri sourceUri) {
        executor.execute(() -> {
            try {
                if (noteId == null || noteId.trim().isEmpty()) {
                    throw new IllegalArgumentException("noteId is null/empty");
                }

                // 1) Read original bytes
                byte[] originalBytes;
                String displayName = "attachment";
                String mimeType = null;
                long sizeBytes;

                // Try to get metadata from ContentResolver
                if (sourceUri != null) {
                    mimeType = context.getContentResolver().getType(sourceUri);

                    // display name + size via query, best-effort
                    try (android.database.Cursor cursor = context.getContentResolver()
                            .query(sourceUri, null, null, null, null)) {

                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            int sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);

                            if (nameIdx >= 0) {
                                String name = cursor.getString(nameIdx);
                                if (name != null && !name.isEmpty()) {
                                    displayName = name;
                                }
                            }

                            if (sizeIdx >= 0) {
                                sizeBytes = cursor.getLong(sizeIdx);
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    try (InputStream is = context.getContentResolver().openInputStream(sourceUri)) {
                        if (is == null) {
                            throw new IOException("Cannot open input stream for Uri: " + sourceUri);
                        }
                        originalBytes = readAllBytes(is);
                    }
                } else {
                    throw new IllegalArgumentException("sourceUri is null");
                }

                sizeBytes = originalBytes.length;

                // 2) Encrypt bytes using EncryptionUtil (string-based)
                //    We encode to Base64 first so we can reuse string encryption.
                String base64Plain = Base64.encodeToString(originalBytes, Base64.NO_WRAP);
                String encryptedBase64 = EncryptionUtil.encrypt(base64Plain);

                // 3) Save encrypted data to internal file
                String id = java.util.UUID.randomUUID().toString();
                File dir = new File(context.getFilesDir(), "attachments");
                if (!dir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                }
                File outFile = new File(dir, id + ".bin");

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(encryptedBase64.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                }

                // 4) Build Attachment entity
                Attachment att = new Attachment();
                att.setId(id);
                att.setNoteId(noteId);
                att.setDisplayName(displayName);
                att.setMimeType(mimeType);
                att.setSizeBytes(sizeBytes);
                att.setCreatedAt(System.currentTimeMillis());
                att.setEncryptedFilePath(outFile.getAbsolutePath());

                attachmentDao.insert(att);

            } catch (Exception e) {
                errorLiveData.postValue("Failed to add attachment: " + e.getMessage());
            }
        });
    }

    public void deleteAttachment(Context context, Attachment attachment) {
        if (attachment == null) return;

        executor.execute(() -> {
            try {
                // Delete DB row
                attachmentDao.delete(attachment);

                // Delete file on disk
                String path = attachment.getEncryptedFilePath();
                if (path != null && !path.isEmpty()) {
                    File f = new File(path);
                    if (f.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    }
                }
            } catch (Exception e) {
                errorLiveData.postValue("Failed to delete attachment: " + e.getMessage());
            }
        });
    }

    public void deleteAllAttachmentsForNote(Context context, String noteId) {
        executor.execute(() -> {
            try {
                // First, get all attachments now so we can delete files
                List<Attachment> list = attachmentDao.getForNoteNow(noteId);
                if (list != null) {
                    for (Attachment att : list) {
                        String path = att.getEncryptedFilePath();
                        if (path != null && !path.isEmpty()) {
                            File f = new File(path);
                            if (f.exists()) {
                                //noinspection ResultOfMethodCallIgnored
                                f.delete();
                            }
                        }
                    }
                }
                // Then delete DB rows
                attachmentDao.deleteForNote(noteId);
            } catch (Exception e) {
                errorLiveData.postValue("Failed to delete attachments: " + e.getMessage());
            }
        });
    }

    /**
     * Load decrypted bytes of an attachment.
     * Call this from background (executor) or wrap it similarly to other methods.
     */
    public byte[] loadAttachmentBytes(Context context, Attachment attachment) {
        try {
            String path = attachment.getEncryptedFilePath();
            if (path == null || path.isEmpty()) {
                throw new IllegalStateException("Attachment file path is empty");
            }

            File file = new File(path);
            if (!file.exists()) {
                throw new IOException("Attachment file does not exist");
            }

            // Read encrypted Base64 string
            String encryptedBase64;
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] encBytes = readAllBytes(fis);
                encryptedBase64 = new String(encBytes, StandardCharsets.UTF_8);
            }

            // Decrypt → Base64(plainBytes) → bytes
            String base64Plain = EncryptionUtil.decrypt(encryptedBase64);
            return Base64.decode(base64Plain, Base64.NO_WRAP);

        } catch (Exception e) {
            errorLiveData.postValue("Failed to load attachment: " + e.getMessage());
            return null;
        }
    }


    public LiveData<List<TodoItem>> getTodosByNote(String noteId) {
        return todoDao.getTodos(noteId);
    }

    public void addTodoItem(TodoItem item) {
        executor.execute(() -> todoDao.insert(item));
    }

    public void updateTodoItem(TodoItem item) {
        executor.execute(() -> todoDao.update(item));
    }

    public void deleteTodoItem(TodoItem item) {
        executor.execute(() -> todoDao.delete(item));
    }

    public void deleteTodosByNoteId(String noteId) {
        executor.execute(() -> todoDao.deleteTodosByNoteId(noteId));
    }

    public LiveData<List<Note>> getNotesByCategory(String category) {
        return noteDao.getNotesByCategory(category);
    }

    public void removeListener() {
        // No-op now; was for Firebase listeners.
    }
}
