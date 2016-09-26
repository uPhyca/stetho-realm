package com.uphyca.stetho_realm;

import android.content.Context;

import com.facebook.stetho.InspectorModulesProvider;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@link com.facebook.stetho.Stetho.InitializerBuilder#enableWebKitInspector(InspectorModulesProvider)}
 *     Stetho.initialize(
 *         Stetho.newInitializerBuilder(this)
 *             .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
 *             .enableWebKitInspector(RealmInspectorModulesProvider.builder(this).build())
 *             .build());
 */
@SuppressWarnings("unused")
public class RealmInspectorModulesProvider implements InspectorModulesProvider {

    private static final Pattern DEFAULT_DATABASE_NAME_PATTERN = Pattern.compile(".+\\.realm");

    private static final long DEFAULT_LIMIT = 250L;
    private static final boolean DEFAULT_ASCENDING_ORDER = true;
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss z";

    private static final int ENCRYPTION_KEY_LENGTH = 64;

    @SuppressWarnings("unused")
    @Deprecated
    public static RealmInspectorModulesProvider wrap(Context context, InspectorModulesProvider provider) {
        //noinspection deprecation
        return wrap(context, provider, false);
    }

    @Deprecated
    public static RealmInspectorModulesProvider wrap(Context context, InspectorModulesProvider provider, boolean withMetaTables) {
        //noinspection deprecation
        return wrap(context, provider, withMetaTables, null);
    }

    @Deprecated
    public static RealmInspectorModulesProvider wrap(Context context,
                                                     InspectorModulesProvider provider,
                                                     boolean withMetaTables,
                                                     Pattern databaseNamePattern) {
        return new RealmInspectorModulesProvider(context.getPackageName(), provider, context.getFilesDir(), withMetaTables, databaseNamePattern, DEFAULT_LIMIT, DEFAULT_ASCENDING_ORDER, DEFAULT_DATE_FORMAT, null, null);
    }

    private final String packageName;
    private final InspectorModulesProvider baseProvider;
    private File folder;
    private final boolean withMetaTables;
    private final Pattern databaseNamePattern;
    private final long limit;
    private final boolean ascendingOrder;
    private final String dateFormat;
    private byte[] defaultEncryptionKey;
    private Map<String, byte[]> encryptionKeys;

    private RealmInspectorModulesProvider(String packageName,
                                          InspectorModulesProvider baseProvider,
                                          File folder,
                                          boolean withMetaTables,
                                          Pattern databaseNamePattern,
                                          long limit,
                                          boolean ascendingOrder,
                                          String dateFormat,
                                          byte[] defaultEncryptionKey,
                                          Map<String, byte[]> encryptionKeys) {
        this.packageName = packageName;
        this.baseProvider = baseProvider;
        this.folder = folder;
        this.withMetaTables = withMetaTables;
        if (databaseNamePattern == null) {
            this.databaseNamePattern = DEFAULT_DATABASE_NAME_PATTERN;
        } else {
            this.databaseNamePattern = databaseNamePattern;
        }
        this.limit = limit;
        this.ascendingOrder = ascendingOrder;
        this.dateFormat = dateFormat;
        this.defaultEncryptionKey = defaultEncryptionKey;
        this.encryptionKeys = encryptionKeys == null ? Collections.<String, byte[]>emptyMap() : encryptionKeys;
    }

    @Override
    public Iterable<ChromeDevtoolsDomain> get() {
        final List<ChromeDevtoolsDomain> modules = new ArrayList<>();
        for (ChromeDevtoolsDomain domain : baseProvider.get()) {
            if (domain instanceof com.facebook.stetho.inspector.protocol.module.Database) {
                continue;
            }
            modules.add(domain);
        }
        modules.add(new com.uphyca.stetho_realm.Database(
                packageName,
                new RealmFilesProvider(folder, databaseNamePattern),
                withMetaTables,
                limit,
                ascendingOrder,
                defaultEncryptionKey,
                encryptionKeys));
        return modules;
    }

    public static ProviderBuilder builder(Context context) {
        return new ProviderBuilder(context);
    }

    public static class ProviderBuilder {
        private final Context applicationContext;

        private InspectorModulesProvider baseProvider;
        private boolean withMetaTables;
        private Pattern databaseNamePattern;

        private File folder;
        private long limit = DEFAULT_LIMIT;
        private boolean ascendingOrder = DEFAULT_ASCENDING_ORDER;
        private String dateFormat = DEFAULT_DATE_FORMAT;
        private byte[] defaultEncryptionKey;
        private Map<String, byte[]> encryptionKeys;

        public ProviderBuilder(Context context) {
            applicationContext = context.getApplicationContext();
            folder = applicationContext.getFilesDir();
        }

        public ProviderBuilder baseProvider(InspectorModulesProvider provider) {
            baseProvider = provider;
            return this;
        }

        public ProviderBuilder withMetaTables() {
            this.withMetaTables = true;
            return this;
        }

        public ProviderBuilder withLimit(long limit) {
            this.limit = limit;
            return this;
        }

        public ProviderBuilder withFolder(File folder) {
            this.folder = folder;
            return this;
        }

        public ProviderBuilder withDescendingOrder() {
            this.ascendingOrder = false;
            return this;
        }

        public ProviderBuilder withDateFormat(String dateFormat) {
            this.dateFormat = dateFormat;
            return this;
        }

        public ProviderBuilder databaseNamePattern(Pattern databaseNamePattern) {
            this.databaseNamePattern = databaseNamePattern;
            return this;
        }

        public ProviderBuilder withDefaultEncryptionKey(byte[] key) {

            if (key != null) {
                if (key.length != ENCRYPTION_KEY_LENGTH) {
                    throw new IllegalArgumentException(String.format("The provided key must be %s bytes. Yours was: %s",
                            ENCRYPTION_KEY_LENGTH, key.length));
                }
                defaultEncryptionKey = key.clone();
            } else {
                defaultEncryptionKey = null;
            }

            return this;
        }

        public ProviderBuilder withEncryptionKey(String filename, byte[] key) {
            if (encryptionKeys == null) {
                encryptionKeys = new HashMap<>();
            }

            if (key != null) {
                if (key.length != ENCRYPTION_KEY_LENGTH) {
                    throw new IllegalArgumentException(String.format("The provided key must be %s bytes. Yours was: %s",
                            ENCRYPTION_KEY_LENGTH, key.length));
                }
                encryptionKeys.put(filename, key.clone());
            } else {
                encryptionKeys.put(filename, null);
            }
            return this;
        }

        public RealmInspectorModulesProvider build() {
            final InspectorModulesProvider baseProvider =
                    (this.baseProvider != null)
                            ? this.baseProvider
                            : Stetho.defaultInspectorModulesProvider(applicationContext);

            //noinspection deprecation
            return new RealmInspectorModulesProvider(
                    applicationContext.getPackageName(),
                    baseProvider,
                    folder,
                    withMetaTables,
                    databaseNamePattern,
                    limit,
                    ascendingOrder,
                    dateFormat,
                    defaultEncryptionKey,
                    encryptionKeys);
        }
    }
}
