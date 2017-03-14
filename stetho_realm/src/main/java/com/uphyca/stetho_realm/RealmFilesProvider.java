package com.uphyca.stetho_realm;

import com.facebook.stetho.inspector.database.DatabaseFilesProvider;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RealmFilesProvider implements DatabaseFilesProvider {
    private final File folder;
    private final FilenameFilter filter;

    public RealmFilesProvider(File folder, final Pattern databaseNamePattern) {
        this.folder = folder;
        filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return databaseNamePattern.matcher(filename).matches();
            }
        };
    }

    @Override
    public List<File> getDatabaseFiles() {
        return findDatabaseFiles(folder);
    }

    private List<File> findDatabaseFiles(File baseDir) {
        List<File> files = new ArrayList<>();

        String[] realmFiles = baseDir.list(filter);
        if (realmFiles != null) {
            for (String realmFileName : realmFiles) {
                File file = new File(baseDir, realmFileName);
                if (file.isDirectory()) {
                    files.addAll(findDatabaseFiles(file));
                } else if (file.isFile() && file.canRead()) {
                    files.add(file);
                }
            }
        }

        return files;
    }

}
