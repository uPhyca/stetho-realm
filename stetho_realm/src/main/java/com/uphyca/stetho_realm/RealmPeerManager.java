package com.uphyca.stetho_realm;

import android.content.Context;
import android.database.sqlite.SQLiteException;

import com.facebook.stetho.inspector.helper.ChromePeerManager;
import com.facebook.stetho.inspector.helper.PeerRegistrationListener;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.realm.Realm;
import io.realm.internal.ImplicitTransaction;
import io.realm.internal.Table;

public class RealmPeerManager extends ChromePeerManager {
    private static final String TABLE_PREFIX = "class_"; // Realm#TABLE_PREFIX

    private final Context context;
    private final RealmFilesProvider realmFilesProvider;

    public RealmPeerManager(Context context, RealmFilesProvider filesProvider) {
        this.context = context;
        this.realmFilesProvider = filesProvider;

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

    public List<String> getDatabaseTableNames(String databaseId) {
        final List<String> tableNames = new ArrayList<>();

        final Realm realm = openDatabase(databaseId);
        //noinspection TryWithIdenticalCatches,TryFinallyCanBeTryWithResources
        try {
            final Field transactionField = realm.getClass().getDeclaredField("transaction");
            transactionField.setAccessible(true);
            final ImplicitTransaction transaction = (ImplicitTransaction) transactionField.get(realm);

            for (int i = 0; i < transaction.size(); i++) {
                final String tableName = transaction.getTableName(i);
                if (tableName.startsWith(TABLE_PREFIX)) {
                    tableNames.add(tableName);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } finally {
            realm.close();
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
            databaseParams.domain = context.getPackageName();
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
        final Realm realm = openDatabase(databaseId);
        //noinspection TryWithIdenticalCatches,TryFinallyCanBeTryWithResources
        try {
            final Field transactionField = realm.getClass().getDeclaredField("transaction");
            transactionField.setAccessible(true);
            final ImplicitTransaction transaction = (ImplicitTransaction) transactionField.get(realm);

            query = query.trim();

            final Matcher selectMatcher = SELECT_PATTERN.matcher(query);
            if (selectMatcher.matches()) {
                final String tableName = selectMatcher.group(1);

                final Table table = transaction.getTable(tableName);
                return executeResultHandler.handleSelect(table, true);
            }

            // TODO 読み出し以外にも対応する
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } finally {
            realm.close();
        }
    }

    private Realm openDatabase(String databaseId) {
        return Realm.getInstance(context, new File(databaseId).getName());
    }

    public interface ExecuteResultHandler<T> {
        @SuppressWarnings("unused")
        T handleRawQuery() throws SQLiteException;

        T handleSelect(Table table, boolean addRowId) throws SQLiteException;

        @SuppressWarnings("unused")
        T handleInsert(long var1) throws SQLiteException;

        @SuppressWarnings("unused")
        T handleUpdateDelete(int var1) throws SQLiteException;
    }
}
