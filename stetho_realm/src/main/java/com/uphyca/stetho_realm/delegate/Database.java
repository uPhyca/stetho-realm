package com.uphyca.stetho_realm.delegate;

import com.facebook.stetho.inspector.jsonrpc.JsonRpcException;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod;
import com.facebook.stetho.json.ObjectMapper;
import com.facebook.stetho.json.annotation.JsonProperty;

import org.json.JSONObject;

import java.util.regex.Pattern;

public class Database implements ChromeDevtoolsDomain {

    private final ObjectMapper objectMapper;
    private final com.facebook.stetho.inspector.protocol.module.Database database;
    private final com.uphyca.stetho_realm.Database realmDatabase;
    private final Pattern databaseNamePattern;

    public Database(com.facebook.stetho.inspector.protocol.module.Database database, com.uphyca.stetho_realm.Database realmDatabase, Pattern databaseNamePattern) {
        this.objectMapper = new ObjectMapper();
        this.database = database;
        this.realmDatabase = realmDatabase;
        this.databaseNamePattern = databaseNamePattern;
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public void enable(JsonRpcPeer peer, JSONObject params) {
        database.enable(peer, params);
        realmDatabase.enable(peer, params);
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public void disable(JsonRpcPeer peer, JSONObject params) {
        database.disable(peer, params);
        realmDatabase.disable(peer, params);
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public JsonRpcResult getDatabaseTableNames(JsonRpcPeer peer, JSONObject params) throws JsonRpcException {
        GetDatabaseTableNamesRequest request = objectMapper.convertValue(params, GetDatabaseTableNamesRequest.class);
        if (databaseNamePattern.matcher(request.databaseId).find()) {
            return realmDatabase.getDatabaseTableNames(peer, params);
        } else {
            return database.getDatabaseTableNames(peer, params);
        }
    }

    @ChromeDevtoolsMethod
    @SuppressWarnings("unused")
    public JsonRpcResult executeSQL(JsonRpcPeer peer, JSONObject params) {
        ExecuteSQLRequest request = objectMapper.convertValue(params, ExecuteSQLRequest.class);
        if (databaseNamePattern.matcher(request.databaseId).find()) {
            return realmDatabase.executeSQL(peer, params);
        } else {
            return database.executeSQL(peer, params);
        }
    }

    private static class GetDatabaseTableNamesRequest {

        @JsonProperty(required = true)
        public String databaseId;
    }

    private static class ExecuteSQLRequest {

        @JsonProperty(required = true)
        public String databaseId;

        @JsonProperty(required = true)
        @SuppressWarnings("unused")
        public String query;
    }
}
