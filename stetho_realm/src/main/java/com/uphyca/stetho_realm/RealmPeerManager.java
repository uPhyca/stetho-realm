package com.uphyca.stetho_realm;

import android.database.sqlite.SQLiteException;

import com.facebook.stetho.inspector.helper.ChromePeerManager;
import com.facebook.stetho.inspector.helper.PeerRegistrationListener;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.internal.ImplicitTransaction;
import io.realm.internal.SharedGroup;
import io.realm.internal.Table;

public class RealmPeerManager extends ChromePeerManager {
    private static final String TABLE_PREFIX = "class_"; // Realm#TABLE_PREFIX

    private final String packageName;
    private final RealmFilesProvider realmFilesProvider;
    private byte[] defaultEncryptionKey;
    private Map<String, byte[]> encryptionKeys;

    public RealmPeerManager(String packageName,
                            RealmFilesProvider filesProvider,
                            byte[] defaultEncryptionKey,
                            Map<String, byte[]> encryptionKeys) {
        this.packageName = packageName;
        this.realmFilesProvider = filesProvider;
        this.defaultEncryptionKey = defaultEncryptionKey;
        this.encryptionKeys = encryptionKeys;

        setListener(new PeerRegistrationListener() {
            @Override
            public void onPeerRegistered(JsonRpcPeer peer) {
                bootstrapNewPeer(peer);
            }

            @Override
            public void onPeerUnregistered(JsonRpcPeer peer) {
            }
        });
    }

    public List<String> getDatabaseTableNames(String databaseId, boolean withMetaTables) {
        final List<String> tableNames = new ArrayList<>();

        final SharedGroup group = openSharedGroupForImplicitTransactions(databaseId);
        //noinspection TryWithIdenticalCatches,TryFinallyCanBeTryWithResources
        try {
            final ImplicitTransaction transaction = group.beginImplicitTransaction();

            for (int i = 0; i < transaction.size(); i++) {
                final String tableName = transaction.getTableName(i);
                if (withMetaTables || tableName.startsWith(TABLE_PREFIX)) {
                    tableNames.add(tableName);
                }
            }
        } finally {
            group.close();
        }

        return tableNames;
    }

    private void bootstrapNewPeer(JsonRpcPeer peer) {
        List<File> potentialDatabaseFiles = realmFilesProvider.getDatabaseFiles();
        Iterable<File> tidiedList = tidyDatabaseList(potentialDatabaseFiles);
        for (File database : tidiedList) {
            Database.DatabaseObject databaseParams = new Database.DatabaseObject();
            databaseParams.id = database.getPath();
            databaseParams.name = database.getName();
            databaseParams.domain = packageName;
            databaseParams.version = "N/A";
            Database.AddDatabaseEvent eventParams = new Database.AddDatabaseEvent();
            eventParams.database = databaseParams;

            peer.invokeMethod("Database.addDatabase", eventParams, null /* callback */);
        }
    }

    /**
     * Attempt to smartly eliminate uninteresting shadow databases such as -journal and -uid.  Note
     * that this only removes the database if it is true that it shadows another database lacking
     * the uninteresting suffix.
     *
     * @param databaseFiles Raw list of database files.
     * @return Tidied list with shadow databases removed.
     */
    // @VisibleForTesting
    static List<File> tidyDatabaseList(List<File> databaseFiles) {
        List<File> tidiedList = new ArrayList<>();
        for (File databaseFile : databaseFiles) {
            tidiedList.add(databaseFile);
        }
        return tidiedList;
    }

    private static final Pattern SELECT_PATTERN = Pattern.compile("SELECT[ \\t]+rowid,[ \\t]+\\*[ \\t]+FROM \"([^\"]+)\"");

    public <T> T executeSQL(String databaseId, String query, RealmPeerManager.ExecuteResultHandler<T> executeResultHandler) {
        final SharedGroup group = openSharedGroupForImplicitTransactions(databaseId);
        //noinspection TryWithIdenticalCatches,TryFinallyCanBeTryWithResources
        try {
            final ImplicitTransaction transaction = group.beginImplicitTransaction();

            query = query.trim();

            final Matcher selectMatcher = SELECT_PATTERN.matcher(query);
            if (selectMatcher.matches()) {
                final String tableName = selectMatcher.group(1);

                final Table table = transaction.getTable(tableName);
                return executeResultHandler.handleSelect(table, true);
            }

            // TODO 読み出し以外にも対応する
            return null;
        } finally {
            group.close();
        }
    }

    private SharedGroup openSharedGroupForImplicitTransactions(String databaseId) {
        final byte[] encryptionKey = getEncryptionKey(databaseId);
        return new SharedGroup(databaseId, true, encryptionKey);
    }

    private byte[] getEncryptionKey(String databaseId) {
        final String databaseName = new File(databaseId).getName();
        if (encryptionKeys.containsKey(databaseName)) { // value が null の場合があるので getではダメ
            return encryptionKeys.get(databaseName);
        }
        return defaultEncryptionKey;
    }

    public interface ExecuteResultHandler<T> {
        @SuppressWarnings("unused")
        T handleRawQuery() throws SQLiteException;

        T handleSelect(Table table, boolean addRowIndex) throws SQLiteException;

        @SuppressWarnings("unused")
        T handleInsert(long var1) throws SQLiteException;

        @SuppressWarnings("unused")
        T handleUpdateDelete(int var1) throws SQLiteException;
    }
}
