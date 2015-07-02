/*
 * Copyright (c) 2015-present, uPhyca, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.uphyca.stetho_realm;

import android.content.Context;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import io.realm.internal.ColumnType;
import io.realm.internal.LinkView;
import io.realm.internal.Row;
import io.realm.internal.Table;

public class Database implements ChromeDevtoolsDomain {
    private static final int MAX_EXECUTE_RESULTS = 250;

    private final RealmPeerManager realmPeerManager;
    private final ObjectMapper objectMapper;
    private final boolean withMetaTables;

    @SuppressWarnings("unused")
    public Database(Context context, RealmFilesProvider filesProvider) {
        this(context, filesProvider, false);
    }

    public Database(Context context, RealmFilesProvider filesProvider, boolean withMetaTables) {
        this.realmPeerManager = new RealmPeerManager(context, filesProvider);
        this.objectMapper = new ObjectMapper();
        this.withMetaTables = withMetaTables;
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
                            response.values = flattenRows(table, MAX_EXECUTE_RESULTS, addRowIndex);
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

    private List<Object> flattenRows(Table table, int limit, boolean addRowIndex) {
        Util.throwIfNot(limit >= 0);
        final List<Object> flatList = new ArrayList<>();
        long numColumns = table.getColumnCount();

        final RowFetcher rowFetcher = RowFetcher.getInstance();
        for (long row = 0; row < limit && row < table.size(); row++) {
            final RowWrapper rowData = RowWrapper.wrap(rowFetcher.getRow(table, row));
            if (addRowIndex) {
                flatList.add(rowData.getIndex());
            }
            for (int column = 0; column < numColumns; column++) {
                switch (rowData.getColumnType(column)) {
                    case INTEGER:
                        flatList.add(rowData.getLong(column));
                        break;
                    case BOOLEAN:
                        flatList.add(rowData.getBoolean(column));
                        break;
                    case STRING:
                        flatList.add(rowData.getString(column));
                        break;
                    case BINARY:
                        flatList.add(rowData.getBinaryByteArray(column));
                        break;
                    case FLOAT:
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
                        break;
                    case DOUBLE:
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
                        break;
                    case DATE:
                        flatList.add(rowData.getDate(column));
                        break;
                    case LINK:
                        flatList.add(rowData.getLink(column));
                        break;
                    case LINK_LIST:
                        flatList.add(rowData.getLinkList(column));
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
        private static final boolean s0_81_OR_NEWER;

        static {
            s0_81_OR_NEWER = Row.class.isInterface();
        }

        public static RowWrapper wrap(Row row) {
            if (s0_81_OR_NEWER) {
                return new RowWrapper_0_81(row);
            } else {
                return new RowWrapper_0_80(row);
            }
        }

        protected RowWrapper() {
        }

        public abstract long getIndex();

        public abstract ColumnType getColumnType(long columnIndex);

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
         * 0.81 でビルドされているので Row は interface だが、 0.80 では class なので
         * 直接メソッド呼び出しを行うとうまくいかない(interface のメソッド呼び出しの
         * 命令は invokeinterface だが、クラスは invokevirtual)。
         * そこで、0_80 ではリフレクションで呼び出しを行うので Object型で保持しておく(うっかり呼ばないように)。
         */
        private final Object row;

        private Method getIndexMethod;
        private Method getColumnTypeMethod;
        private Method getLongMethod;
        private Method getBooleanMethod;
        private Method getFloatMethod;
        private Method getDoubleMethod;
        private Method getDateMethod;
        private Method getStringMethod;
        private Method getBinaryByteArrayMethod;
        private Method getLinkMethod;
        private Method getLinkListMethod;

        RowWrapper_0_80(Row row) {
            this.row = row;

            try {
                final Class<? extends Row> aClass = row.getClass();
                getIndexMethod = aClass.getMethod("getIndex");
                getColumnTypeMethod = aClass.getMethod("getColumnType", Long.TYPE);
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
        public ColumnType getColumnType(long columnIndex) {
            try {
                return (ColumnType) getColumnTypeMethod.invoke(row, columnIndex);
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

    private static final class RowWrapper_0_81 extends RowWrapper {
        private final Row row;

        RowWrapper_0_81(Row row) {
            this.row = row;
        }

        @Override
        public long getIndex() {
            return row.getIndex();
        }

        @Override
        public ColumnType getColumnType(long columnIndex) {
            return row.getColumnType(columnIndex);
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
