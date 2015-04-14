package com.uphyca.stetho_realm;

import android.content.Context;

import com.facebook.stetho.inspector.database.DatabaseFilesProvider;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class RealmFilesProvider implements DatabaseFilesProvider {
    private final Context context;

    public RealmFilesProvider(Context context) {
        this.context = context;
    }

    @Override
    public List<File> getDatabaseFiles() {

        final File baseDir = context.getFilesDir();
        final String[] realmFiles = baseDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".realm");
            }
        });

        final ArrayList<File> files = new ArrayList<>();

        if (realmFiles == null) {
            return files;
        }

        for (String realmFileName : realmFiles) {
            files.add(new File(baseDir, realmFileName));
        }

        return files;
    }
}
