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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        for (long row = 0; row < limit && row < table.size(); row++) {
            final Row rowData = table.getRow(row);
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
}
