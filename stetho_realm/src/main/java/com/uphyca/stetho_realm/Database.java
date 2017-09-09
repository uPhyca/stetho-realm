/*
 * Copyright (c) 2015-present, uPhyca, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.uphyca.stetho_realm;

import android.database.sqlite.SQLiteException;

import com.facebook.stetho.common.Util;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod;
import com.facebook.stetho.json.ObjectMapper;
import com.facebook.stetho.json.annotation.JsonProperty;

import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.realm.internal.OsList;
import io.realm.internal.Row;
import io.realm.internal.Table;


@SuppressWarnings("WeakerAccess")
public class Database implements ChromeDevtoolsDomain {

    private static final String NULL = "[null]";

    private final RealmPeerManager realmPeerManager;
    private final ObjectMapper objectMapper;
    private final boolean withMetaTables;
    private final long limit;
    private final boolean ascendingOrder;

    private DateFormat dateTimeFormatter;

    private enum StethoRealmFieldType {
        INTEGER(0),
        BOOLEAN(1),
        STRING(2),
        BINARY(4),
        UNSUPPORTED_TABLE(5),
        UNSUPPORTED_MIXED(6),
        OLD_DATE(7),
        DATE(8),
        FLOAT(9),
        DOUBLE(10),
        OBJECT(12),
        LIST(13),
        // BACKLINK(14); Not exposed until needed

        // Stetho Realmが勝手に定義した特別な値
        UNKNOWN(-1);

        private final int nativeValue;

        StethoRealmFieldType(int nativeValue) {
            this.nativeValue = nativeValue;
        }

        @SuppressWarnings("unused")
        public int getValue() {
            return nativeValue;
        }
    }

    /**
     * 指定されたパラメータで {@link Database}インスタンスを構築します。
     *
     * @param packageName アプリケーションのパッケージネーム(application ID)。
     * @param filesProvider {@link RealmFilesProvider} インスタンス。
     * @param withMetaTables テーブル一覧にmeta テーブルを含めるかどうか。
     * @param limit 返却するデータの最大行数
     * @param ascendingOrder {@code true}ならデータを id列の昇順に、{@code false}なら降順に返します。
     * @param defaultEncryptionKey データベースの復号に使用するキー。
     * {@code null} の場合は暗号化されていないものとして扱います。
     * また、 {@code encryptionKeys} で個別のキーが指定されている
     * データベースについては {@code encryptionKeys}の指定が優先されます。
     * @param encryptionKeys データベース個別のキーを指定するマップ。
     */
    Database(String packageName,
            RealmFilesProvider filesProvider,
            boolean withMetaTables,
            long limit,
            boolean ascendingOrder,
            byte[] defaultEncryptionKey,
            Map<String, byte[]> encryptionKeys) {
        this.realmPeerManager = new RealmPeerManager(packageName, filesProvider, defaultEncryptionKey, encryptionKeys);
        this.objectMapper = new ObjectMapper();
        this.withMetaTables = withMetaTables;
        this.limit = limit;
        this.ascendingOrder = ascendingOrder;
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public void enable(JsonRpcPeer peer, JSONObject params) {
        realmPeerManager.addPeer(peer);
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public void disable(JsonRpcPeer peer, JSONObject params) {
        realmPeerManager.removePeer(peer);
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public JsonRpcResult getDatabaseTableNames(JsonRpcPeer peer, JSONObject params) {
        GetDatabaseTableNamesRequest request = objectMapper.convertValue(params, GetDatabaseTableNamesRequest.class);
        GetDatabaseTableNamesResponse response = new GetDatabaseTableNamesResponse();
        response.tableNames = realmPeerManager.getDatabaseTableNames(request.databaseId, withMetaTables);
        return response;
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public JsonRpcResult executeSQL(JsonRpcPeer peer, JSONObject params) {
        ExecuteSQLRequest request = this.objectMapper.convertValue(params, ExecuteSQLRequest.class);

        try {
            return realmPeerManager.executeSQL(request.databaseId, request.query,
                    new RealmPeerManager.ExecuteResultHandler<ExecuteSQLResponse>() {
                        public ExecuteSQLResponse handleRawQuery() throws SQLiteException {
                            ExecuteSQLResponse response = new ExecuteSQLResponse();
                            response.columnNames = Collections.singletonList("success");
                            response.values = Collections.<Object>singletonList("true");
                            return response;
                        }

                        public ExecuteSQLResponse handleSelect(Table table, boolean addRowIndex) throws SQLiteException {
                            ExecuteSQLResponse response = new ExecuteSQLResponse();

                            final ArrayList<String> columnNames = new ArrayList<>();
                            if (addRowIndex) {
                                columnNames.add("<index>");
                            }
                            for (int i = 0; i < table.getColumnCount(); i++) {
                                columnNames.add(table.getColumnName(i));
                            }

                            response.columnNames = columnNames;
                            response.values = flattenRows(table, limit, addRowIndex);
                            return response;
                        }

                        public ExecuteSQLResponse handleInsert(long insertedId) throws SQLiteException {
                            ExecuteSQLResponse response = new ExecuteSQLResponse();
                            response.columnNames = Collections.singletonList("ID of last inserted row");
                            response.values = Collections.<Object>singletonList(insertedId);
                            return response;
                        }

                        public ExecuteSQLResponse handleUpdateDelete(int count) throws SQLiteException {
                            ExecuteSQLResponse response = new ExecuteSQLResponse();
                            response.columnNames = Collections.singletonList("Modified rows");
                            response.values = Collections.<Object>singletonList(count);
                            return response;
                        }
                    });
        } catch (SQLiteException e) {
            Error error = new Error();
            error.code = 0;
            error.message = e.getMessage();
            ExecuteSQLResponse response = new ExecuteSQLResponse();
            response.sqlError = error;
            return response;
        }
    }

    private List<Object> flattenRows(Table table, long limit, boolean addRowIndex) {
        Util.throwIfNot(limit >= 0);
        final List<Object> flatList = new ArrayList<>();
        long numColumns = table.getColumnCount();

        final RowFetcher rowFetcher = RowFetcher.getInstance();
        final long tableSize = table.size();
        for (long index = 0; index < limit && index < tableSize; index++) {
            final long row = ascendingOrder ? index : (tableSize - index - 1);
            final RowWrapper rowData = RowWrapper.wrap(rowFetcher.getRow(table, row));
            if (addRowIndex) {
                flatList.add(rowData.getIndex());
            }
            for (int column = 0; column < numColumns; column++) {
                switch (rowData.getColumnType(column)) {
                    case INTEGER:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            flatList.add(rowData.getLong(column));
                        }
                        break;
                    case BOOLEAN:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            flatList.add(rowData.getBoolean(column));
                        }
                        break;
                    case STRING:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            flatList.add(rowData.getString(column));
                        }
                        break;
                    case BINARY:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            flatList.add(rowData.getBinaryByteArray(column));
                        }
                        break;
                    case FLOAT:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            final float aFloat = rowData.getFloat(column);
                            if (Float.isNaN(aFloat)) {
                                flatList.add("NaN");
                            } else if (aFloat == Float.POSITIVE_INFINITY) {
                                flatList.add("Infinity");
                            } else if (aFloat == Float.NEGATIVE_INFINITY) {
                                flatList.add("-Infinity");
                            } else {
                                flatList.add(aFloat);
                            }
                        }
                        break;
                    case DOUBLE:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            final double aDouble = rowData.getDouble(column);
                            if (Double.isNaN(aDouble)) {
                                flatList.add("NaN");
                            } else if (aDouble == Double.POSITIVE_INFINITY) {
                                flatList.add("Infinity");
                            } else if (aDouble == Double.NEGATIVE_INFINITY) {
                                flatList.add("-Infinity");
                            } else {
                                flatList.add(aDouble);
                            }
                        }
                        break;
                    case OLD_DATE:
                    case DATE:
                        if (rowData.isNull(column)) {
                            flatList.add(NULL);
                        } else {
                            flatList.add(formatDate(rowData.getDate(column)));
                        }
                        break;
                    case OBJECT:
                        if (rowData.isNullLink(column)) {
                            flatList.add(NULL);
                        } else {
                            flatList.add(rowData.getLink(column));
                        }
                        break;
                    case LIST:
                        // LIST never be null
                        flatList.add(formatList(rowData.getLinkList(column)));
                        break;
                    default:
                        flatList.add("unknown column type: " + rowData.getColumnType(column));
                        break;
                }
            }
        }

        if (limit < table.size()) {
            for (int column = 0; column < numColumns; column++) {
                flatList.add("{truncated}");
            }
        }

        return flatList;
    }

    private static class GetDatabaseTableNamesRequest {
        @JsonProperty(required = true)
        public String databaseId;
    }

    private static class GetDatabaseTableNamesResponse implements JsonRpcResult {
        @JsonProperty(required = true)
        public List<String> tableNames;
    }

    private static class ExecuteSQLRequest {
        @JsonProperty(required = true)
        public String databaseId;

        @JsonProperty(required = true)
        public String query;
    }

    private static class ExecuteSQLResponse implements JsonRpcResult {
        @JsonProperty
        public List<String> columnNames;

        @JsonProperty
        public List<Object> values;

        @JsonProperty
        public Error sqlError;
    }

    public static class AddDatabaseEvent {
        @JsonProperty(required = true)
        public DatabaseObject database;
    }

    public static class DatabaseObject {
        @JsonProperty(required = true)
        public String id;

        @JsonProperty(required = true)
        public String domain;

        @JsonProperty(required = true)
        public String name;

        @JsonProperty(required = true)
        public String version;
    }

    public static class Error {
        @JsonProperty(required = true)
        public String message;

        @JsonProperty(required = true)
        public int code;
    }

    private String formatDate(Date date) {
        if (dateTimeFormatter == null) {
            dateTimeFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.LONG, SimpleDateFormat.LONG);
        }
        return dateTimeFormatter.format(date) + " (" + date.getTime() + ')';
    }

    private String formatList(OsList linkList) {
        final StringBuilder sb = new StringBuilder(linkList.getTargetTable().getName());
        sb.append("{");

        final long size = linkList.size();
        for (long pos = 0; pos < size; pos++) {
            sb.append(linkList.getUncheckedRow(pos).getIndex());
            sb.append(',');
        }
        if (size != 0) {
            // remove last ','
            sb.setLength(sb.length() - 1);
        }

        sb.append("}");
        return sb.toString();
    }

    static class RowFetcher {
        private static RowFetcher sInstance = new RowFetcher();

        static RowFetcher getInstance() {
            return sInstance;
        }

        RowFetcher() {
        }

        Row getRow(Table targetTable, long index) {
            return targetTable.getCheckedRow(index);
        }
    }

    static class RowWrapper {
        static RowWrapper wrap(Row row) {
            return new RowWrapper(row);
        }

        private final Row row;

        RowWrapper(Row row) {
            this.row = row;
        }

        long getIndex() {
            return row.getIndex();
        }

        StethoRealmFieldType getColumnType(long columnIndex) {
            // io.realm.RealmFieldType
            final Enum<?> columnType = row.getColumnType(columnIndex);
            final String name = columnType.name();
            if (name.equals("INTEGER")) {
                return StethoRealmFieldType.INTEGER;
            }
            if (name.equals("BOOLEAN")) {
                return StethoRealmFieldType.BOOLEAN;
            }
            if (name.equals("STRING")) {
                return StethoRealmFieldType.STRING;
            }
            if (name.equals("BINARY")) {
                return StethoRealmFieldType.BINARY;
            }
            if (name.equals("UNSUPPORTED_TABLE")) {
                return StethoRealmFieldType.UNSUPPORTED_TABLE;
            }
            if (name.equals("UNSUPPORTED_MIXED")) {
                return StethoRealmFieldType.UNSUPPORTED_MIXED;
            }
            if (name.equals("UNSUPPORTED_DATE")) {
                return StethoRealmFieldType.OLD_DATE;
            }
            if (name.equals("DATE")) {
                return StethoRealmFieldType.DATE;
            }
            if (name.equals("FLOAT")) {
                return StethoRealmFieldType.FLOAT;
            }
            if (name.equals("DOUBLE")) {
                return StethoRealmFieldType.DOUBLE;
            }
            if (name.equals("OBJECT")) {
                return StethoRealmFieldType.OBJECT;
            }
            if (name.equals("LIST")) {
                return StethoRealmFieldType.LIST;
            }
            return StethoRealmFieldType.UNKNOWN;
        }

        boolean isNull(long columnIndex) {
            return row.isNull(columnIndex);
        }

        boolean isNullLink(long columnIndex) {
            return row.isNullLink(columnIndex);
        }

        long getLong(long columnIndex) {
            return row.getLong(columnIndex);
        }

        boolean getBoolean(long columnIndex) {
            return row.getBoolean(columnIndex);
        }

        float getFloat(long columnIndex) {
            return row.getFloat(columnIndex);
        }

        double getDouble(long columnIndex) {
            return row.getDouble(columnIndex);
        }

        Date getDate(long columnIndex) {
            return row.getDate(columnIndex);
        }

        String getString(long columnIndex) {
            return row.getString(columnIndex);
        }

        byte[] getBinaryByteArray(long columnIndex) {
            return row.getBinaryByteArray(columnIndex);
        }

        long getLink(long columnIndex) {
            return row.getLink(columnIndex);
        }

        OsList getLinkList(long columnIndex) {
            return row.getLinkList(columnIndex);
        }
    }
}
