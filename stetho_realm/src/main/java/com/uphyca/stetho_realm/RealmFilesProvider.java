package com.uphyca.stetho_realm;

import com.facebook.stetho.inspector.database.DatabaseFilesProvider;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class RealmFilesProvider implements DatabaseFilesProvider {
    private final File folder;
    private final Pattern databaseNamePattern;

    public RealmFilesProvider(File folder, Pattern databaseNamePattern) {
        this.folder = folder;
        this.databaseNamePattern = databaseNamePattern;
    }

    @Override
    public List<File> getDatabaseFiles() {

        final File baseDir = folder;
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
}
