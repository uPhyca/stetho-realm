package com.uphyca.stetho_realm;

import android.content.Context;

import com.facebook.stetho.inspector.database.DatabaseFilesProvider;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class RealmFilesProvider implements DatabaseFilesProvider {
    private final Context context;
    private final Pattern databaseNamePattern;
    private final Map<String, byte[]> encryptionKey;

    public RealmFilesProvider(Context context, Pattern databaseNamePattern, Map<String, byte[]> encryptionKey) {
        this.context = context;
        this.databaseNamePattern = databaseNamePattern;
        this.encryptionKey = encryptionKey;
    }

    @Override
    public List<File> getDatabaseFiles() {

        final File baseDir = context.getFilesDir();
        final String[] realmFiles = baseDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return databaseNamePattern.matcher(filename).matches();
            }
        });

        final ArrayList<File> files = new ArrayList<>();

        if (realmFiles == null) {
            return files;
        }

        for (String realmFileName : realmFiles) {
            final File file = new File(baseDir, realmFileName);
            if (file.isFile() && file.canRead()) {
                files.add(file);
            }
        }

        return files;
    }

    public byte[] getEncryptionKey(String fileName) {
        return encryptionKey != null ? encryptionKey.get(fileName) : null;
    }
}
