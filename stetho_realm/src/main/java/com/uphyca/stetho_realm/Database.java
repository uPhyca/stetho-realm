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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.realm.internal.LinkView;
import io.realm.internal.Row;
import io.realm.internal.Table;

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
     * @param packageName          アプリケーションのパッケージネーム(application ID)。
     * @param filesProvider        {@link RealmFilesProvider} インスタンス。
     * @param withMetaTables       テーブル一覧にmeta テーブルを含めるかどうか。
     * @param limit                返却するデータの最大行数
     * @param ascendingOrder       {@code true}ならデータを id列の昇順に、{@code false}なら降順に返します。
     * @param defaultEncryptionKey データベースの復号に使用するキー。
     *                             {@code null} の場合は暗号化されていないものとして扱います。
     *                             また、 {@code encryptionKeys} で個別のキーが指定されている
     *                             データベースについては {@code encryptionKeys}の指定が優先されます。
     * @param encryptionKeys       データベース個別のキーを指定するマップ。
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

    private String formatList(LinkView linkList) {
        final StringBuilder sb = new StringBuilder(linkList.getTargetTable().getName());
        sb.append("{");

        final long size = linkList.size();
        for (long pos = 0; pos < size; pos++) {
            sb.append(linkList.getTargetRowIndex(pos));
            sb.append(',');
        }
        if (size != 0) {
            // remove last ','
            sb.setLength(sb.length() - 1);
        }

        sb.append("}");
        return sb.toString();
    }

    private abstract static class RowFetcher {
        private static RowFetcher sInstance;

        static {
            try {
                Table.class.getMethod("getCheckedRow", Long.TYPE);
                sInstance = new RowFetcherFor_0_81();
            } catch (NoSuchMethodException e) {
                sInstance = new RowFetcherFor_0_80();
            }
        }

        public static RowFetcher getInstance() {
            return sInstance;
        }

        RowFetcher() {
        }

        public abstract Row getRow(Table targetTable, long index);
    }

    private static final class RowFetcherFor_0_80 extends RowFetcher {
        private final Method getRowMethod;

        RowFetcherFor_0_80() {
            try {
                getRowMethod = Table.class.getMethod("getRow", Long.TYPE);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("getRow method not found in Table class.");
            }
        }

        @Override
        public Row getRow(Table targetTable, long index) {
            try {
                return (Row) getRowMethod.invoke(targetTable, index);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }
    }

    private static final class RowFetcherFor_0_81 extends RowFetcher {
        @Override
        public Row getRow(Table targetTable, long index) {
            return targetTable.getCheckedRow(index);
        }
    }

    private abstract static class RowWrapper {
        private static final boolean s0_90_OR_NEWER;
        private static final boolean s0_86_OR_NEWER;
        private static final boolean s0_83_OR_NEWER;
        private static final boolean s0_81_OR_NEWER;

        static {
            s0_81_OR_NEWER = is0_81_OrNewer();
            s0_83_OR_NEWER = (s0_81_OR_NEWER && is0_83_OrNewer());
            s0_86_OR_NEWER = (s0_83_OR_NEWER && is0_86_OrNewer());
            s0_90_OR_NEWER = (s0_86_OR_NEWER && is0_90_OrNewer());
        }

        private static boolean is0_81_OrNewer() {
            return Row.class.isInterface();
        }

        private static boolean is0_83_OrNewer() {
            try {
                Row.class.getDeclaredMethod("isNull", long.class);
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }

        private static boolean is0_86_OrNewer() {
            try {
                // 0.86.0 or newer does not have io.realm.internal.ColumnType
                Class.forName("io.realm.internal.ColumnType");
                return false;
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        private static boolean is0_90_OrNewer() {
            // 0.90.0 changed the value of io.realm.RealmFieldType.DATE from 7 to 8

            final Class<? extends Enum> fieldTypeClass;
            try {
                //noinspection unchecked
                fieldTypeClass = (Class<? extends Enum>) Class.forName("io.realm.RealmFieldType");
            } catch (ClassNotFoundException e) {
                return false;
            }

            try {
                final Enum<?> date = Enum.valueOf(fieldTypeClass, "DATE");
                final Method getNativeValueMethod = fieldTypeClass.getDeclaredMethod("getNativeValue");
                final Integer nativeValue = (Integer) getNativeValueMethod.invoke(date);

                return nativeValue != 7;
            } catch (NoSuchMethodException e) {
                // at least before 0.90 does not throw this exception
                return true;
            } catch (IllegalAccessException e) {
                // at least before 0.90 does not throw this exception
                return true;
            } catch (InvocationTargetException e) {
                // at least before 0.90 does not throw this exception
                return true;
            }
        }

        public static RowWrapper wrap(Row row) {
            if (s0_90_OR_NEWER) {
                return new RowWrapper_0_90(row);
            }
            if (s0_86_OR_NEWER) {
                return new RowWrapper_0_86(row);
            }
            if (s0_83_OR_NEWER) {
                return new RowWrapper_0_83(row);
            }
            if (s0_81_OR_NEWER) {
                return new RowWrapper_0_81(row);
            }
            return new RowWrapper_0_80(row);
        }

        protected RowWrapper() {
        }

        public abstract long getIndex();

        public abstract StethoRealmFieldType getColumnType(long columnIndex);

        // for BOOLEAN, INTEGER, FLOAT, DOUBLE, STRING, BINARY, DATE
        public abstract boolean isNull(long columnIndex);
        // for Link
        public abstract boolean isNullLink(long columnIndex);

        public abstract long getLong(long columnIndex);

        public abstract boolean getBoolean(long columnIndex);

        public abstract float getFloat(long columnIndex);

        public abstract double getDouble(long columnIndex);

        public abstract Date getDate(long columnIndex);

        public abstract String getString(long columnIndex);

        public abstract byte[] getBinaryByteArray(long columnIndex);

        public abstract long getLink(long columnIndex);

        public abstract LinkView getLinkList(long columnIndex);

    }

    private static final class RowWrapper_0_80 extends RowWrapper {

        /*
         * 0.81 以降でビルドされているので Row は interface だが、 0.80 では class なので
         * 直接メソッド呼び出しを行うとうまくいかない(interface のメソッド呼び出しの
         * 命令は invokeinterface だが、クラスは invokevirtual)。
         * そこで、0_80 ではリフレクションで呼び出しを行うので Object型で保持しておく(うっかり呼ばないように)。
         */
        private final Object row;

        private final Method getIndexMethod;
        private final Method getColumnTypeMethod;
        private final Method isNullLinkMethod;
        private final Method getLongMethod;
        private final Method getBooleanMethod;
        private final Method getFloatMethod;
        private final Method getDoubleMethod;
        private final Method getDateMethod;
        private final Method getStringMethod;
        private final Method getBinaryByteArrayMethod;
        private final Method getLinkMethod;
        private final Method getLinkListMethod;

        RowWrapper_0_80(Row row) {
            this.row = row;

            try {
                final Class<? extends Row> aClass = row.getClass();
                getIndexMethod = aClass.getMethod("getIndex");
                getColumnTypeMethod = aClass.getMethod("getColumnType", Long.TYPE);
                isNullLinkMethod = aClass.getMethod("isNullLink", Long.TYPE);
                getLongMethod = aClass.getMethod("getLong", Long.TYPE);
                getBooleanMethod = aClass.getMethod("getBoolean", Long.TYPE);
                getFloatMethod = aClass.getMethod("getFloat", Long.TYPE);
                getDoubleMethod = aClass.getMethod("getDouble", Long.TYPE);
                getDateMethod = aClass.getMethod("getDate", Long.TYPE);
                getStringMethod = aClass.getMethod("getString", Long.TYPE);
                getBinaryByteArrayMethod = aClass.getMethod("getBinaryByteArray", Long.TYPE);
                getLinkMethod = aClass.getMethod("getLink", Long.TYPE);
                getLinkListMethod = aClass.getMethod("getLinkList", Long.TYPE);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getIndex() {
            try {
                return (long) getIndexMethod.invoke(row);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public StethoRealmFieldType getColumnType(long columnIndex) {
            try {
                // io.realm.internal.ColumnType
                final Enum<?> result = (Enum<?>) getColumnTypeMethod.invoke(row, columnIndex);
                // see https://github.com/realm/realm-java/blob/v0.80.0/realm/src/main/java/io/realm/internal/ColumnType.java#L25-L35
                final String name = result.name();
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
                if (name.equals("TABLE")) {
                    return StethoRealmFieldType.UNSUPPORTED_TABLE;
                }
                if (name.equals("MIXED")) {
                    return StethoRealmFieldType.UNSUPPORTED_MIXED;
                }
                if (name.equals("DATE")) {
                    return StethoRealmFieldType.OLD_DATE;
                }
                if (name.equals("FLOAT")) {
                    return StethoRealmFieldType.FLOAT;
                }
                if (name.equals("DOUBLE")) {
                    return StethoRealmFieldType.DOUBLE;
                }
                if (name.equals("LINK")) {
                    return StethoRealmFieldType.OBJECT;
                }
                if (name.equals("LINK_LIST")) {
                    return StethoRealmFieldType.LIST;
                }
                return StethoRealmFieldType.UNKNOWN;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public boolean isNull(long columnIndex) {
            // null column is not supported
            return false;
        }

        @Override
        public boolean isNullLink(long columnIndex) {
            try {
                return (boolean) isNullLinkMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public long getLong(long columnIndex) {
            try {
                return (long) getLongMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public boolean getBoolean(long columnIndex) {
            try {
                return (boolean) getBooleanMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public float getFloat(long columnIndex) {
            try {
                return (float) getFloatMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public double getDouble(long columnIndex) {
            try {
                return (double) getDoubleMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public Date getDate(long columnIndex) {
            try {
                return (Date) getDateMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public String getString(long columnIndex) {
            try {
                return (String) getStringMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public byte[] getBinaryByteArray(long columnIndex) {
            try {
                return (byte[]) getBinaryByteArrayMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public long getLink(long columnIndex) {
            try {
                return (long) getLinkMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public LinkView getLinkList(long columnIndex) {
            try {
                return (LinkView) getLinkListMethod.invoke(row, columnIndex);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }
    }

    private static class RowWrapper_0_81 extends RowWrapper {
        protected final Row row;

        private final Method getColumnTypeMethod;

        RowWrapper_0_81(Row row) {
            this.row = row;
            try {
                getColumnTypeMethod = row.getClass().getMethod("getColumnType", Long.TYPE);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long getIndex() {
            return row.getIndex();
        }

        @Override
        public StethoRealmFieldType getColumnType(long columnIndex) {
            try {
                // io.realm.internal.ColumnType
                final Enum<?> result = (Enum<?>) getColumnTypeMethod.invoke(row, columnIndex);
                // see https://github.com/realm/realm-java/blob/v0.85.0/realm/realm-library/src/main/java/io/realm/internal/ColumnType.java#L26-L36
                final String name = result.name();
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
                if (name.equals("TABLE")) {
                    return StethoRealmFieldType.UNSUPPORTED_TABLE;
                }
                if (name.equals("MIXED")) {
                    return StethoRealmFieldType.UNSUPPORTED_MIXED;
                }
                if (name.equals("DATE")) {
                    return StethoRealmFieldType.OLD_DATE;
                }
                if (name.equals("FLOAT")) {
                    return StethoRealmFieldType.FLOAT;
                }
                if (name.equals("DOUBLE")) {
                    return StethoRealmFieldType.DOUBLE;
                }
                if (name.equals("LINK")) {
                    return StethoRealmFieldType.OBJECT;
                }
                if (name.equals("LINK_LIST")) {
                    return StethoRealmFieldType.LIST;
                }
                return StethoRealmFieldType.UNKNOWN;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        @Override
        public boolean isNull(long columnIndex) {
            // null column is not supported
            return false;
        }

        @Override
        public boolean isNullLink(long columnIndex) {
            return row.isNullLink(columnIndex);
        }

        @Override
        public long getLong(long columnIndex) {
            return row.getLong(columnIndex);
        }

        @Override
        public boolean getBoolean(long columnIndex) {
            return row.getBoolean(columnIndex);
        }

        @Override
        public float getFloat(long columnIndex) {
            return row.getFloat(columnIndex);
        }

        @Override
        public double getDouble(long columnIndex) {
            return row.getDouble(columnIndex);
        }

        @Override
        public Date getDate(long columnIndex) {
            return row.getDate(columnIndex);
        }

        @Override
        public String getString(long columnIndex) {
            return row.getString(columnIndex);
        }

        @Override
        public byte[] getBinaryByteArray(long columnIndex) {
            return row.getBinaryByteArray(columnIndex);
        }

        @Override
        public long getLink(long columnIndex) {
            return row.getLink(columnIndex);
        }

        @Override
        public LinkView getLinkList(long columnIndex) {
            return row.getLinkList(columnIndex);
        }
    }

    private static final class RowWrapper_0_83 extends RowWrapper_0_81 {

        RowWrapper_0_83(Row row) {
            super(row);
        }

        @Override
        public boolean isNull(long columnIndex) {
            return false;
        }
    }

    private static final class RowWrapper_0_86 extends RowWrapper {
        private final Row row;

        RowWrapper_0_86(Row row) {
            this.row = row;
        }

        @Override
        public long getIndex() {
            return row.getIndex();
        }

        public StethoRealmFieldType getColumnType(long columnIndex) {
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
            if (name.equals("DATE")) {
                return StethoRealmFieldType.OLD_DATE;
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

        @Override
        public boolean isNull(long columnIndex) {
            return row.isNull(columnIndex);
        }

        @Override
        public boolean isNullLink(long columnIndex) {
            return row.isNullLink(columnIndex);
        }

        @Override
        public long getLong(long columnIndex) {
            return row.getLong(columnIndex);
        }

        @Override
        public boolean getBoolean(long columnIndex) {
            return row.getBoolean(columnIndex);
        }

        @Override
        public float getFloat(long columnIndex) {
            return row.getFloat(columnIndex);
        }

        @Override
        public double getDouble(long columnIndex) {
            return row.getDouble(columnIndex);
        }

        @Override
        public Date getDate(long columnIndex) {
            return row.getDate(columnIndex);
        }

        @Override
        public String getString(long columnIndex) {
            return row.getString(columnIndex);
        }

        @Override
        public byte[] getBinaryByteArray(long columnIndex) {
            return row.getBinaryByteArray(columnIndex);
        }

        @Override
        public long getLink(long columnIndex) {
            return row.getLink(columnIndex);
        }

        @Override
        public LinkView getLinkList(long columnIndex) {
            return row.getLinkList(columnIndex);
        }
    }

    private static final class RowWrapper_0_90 extends RowWrapper {
        private final Row row;

        RowWrapper_0_90(Row row) {
            this.row = row;
        }

        @Override
        public long getIndex() {
            return row.getIndex();
        }

        public StethoRealmFieldType getColumnType(long columnIndex) {
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

        @Override
        public boolean isNull(long columnIndex) {
            return row.isNull(columnIndex);
        }

        @Override
        public boolean isNullLink(long columnIndex) {
            return row.isNullLink(columnIndex);
        }

        @Override
        public long getLong(long columnIndex) {
            return row.getLong(columnIndex);
        }

        @Override
        public boolean getBoolean(long columnIndex) {
            return row.getBoolean(columnIndex);
        }

        @Override
        public float getFloat(long columnIndex) {
            return row.getFloat(columnIndex);
        }

        @Override
        public double getDouble(long columnIndex) {
            return row.getDouble(columnIndex);
        }

        @Override
        public Date getDate(long columnIndex) {
            return row.getDate(columnIndex);
        }

        @Override
        public String getString(long columnIndex) {
            return row.getString(columnIndex);
        }

        @Override
        public byte[] getBinaryByteArray(long columnIndex) {
            return row.getBinaryByteArray(columnIndex);
        }

        @Override
        public long getLink(long columnIndex) {
            return row.getLink(columnIndex);
        }

        @Override
        public LinkView getLinkList(long columnIndex) {
            return row.getLinkList(columnIndex);
        }
    }
}
