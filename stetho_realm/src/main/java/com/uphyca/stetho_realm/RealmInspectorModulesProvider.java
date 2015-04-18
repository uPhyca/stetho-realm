package com.uphyca.stetho_realm;

import android.content.Context;

import com.facebook.stetho.InspectorModulesProvider;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stetho へモジュールを組み込むための InspectorModulesProvider です。
 * <p/>
 * Stetho の初期化の際に、{@link #builder(Context)} で作成した RealmInspectorModulesProvider インスタンスを
 * {@link com.facebook.stetho.Stetho.InitializerBuilder#enableWebKitInspector(InspectorModulesProvider)}
 * に渡してください。
 * <p/>
 * <pre>
 *     Stetho.initialize(
 *         Stetho.newInitializerBuilder(this)
 *             .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
 *             .enableWebKitInspector(RealmInspectorModulesProvider.builder(this).build())
 *             .build());
 * </pre>
 *
 * {@link com.uphyca.stetho_realm.RealmInspectorModulesProvider.ProviderBuilder} の各種メソッドを呼ぶことで
 * メタデータテーブルを表示に含めるかや、データベースファイル名のパターンを指定することができます。
 */
@SuppressWarnings("unused")
public class RealmInspectorModulesProvider implements InspectorModulesProvider {

    private static final Pattern DEFAULT_DATABASE_NAME_PATTERN = Pattern.compile(".+\\.realm");

    @SuppressWarnings("unused")
    @Deprecated
    public static RealmInspectorModulesProvider wrap(Context context, InspectorModulesProvider provider) {
        return wrap(context, provider, false);
    }

    @Deprecated
    public static RealmInspectorModulesProvider wrap(Context context, InspectorModulesProvider provider, boolean withMetaTables) {
        return wrap(context, provider, withMetaTables, null);
    }

    @Deprecated
    public static RealmInspectorModulesProvider wrap(Context context,
                                                InspectorModulesProvider provider,
                                                boolean withMetaTables,
                                                Pattern databaseNamePattern) {
        return new RealmInspectorModulesProvider(context, provider, withMetaTables, databaseNamePattern);
    }

    private final Context context;
    private final InspectorModulesProvider baseProvider;
    private final boolean withMetaTables;
    private final Pattern databaseNamePattern;

    private RealmInspectorModulesProvider(Context context,
                                          InspectorModulesProvider baseProvider,
                                          boolean withMetaTables,
                                          Pattern databaseNamePattern) {
        this.context = context.getApplicationContext();
        this.baseProvider = baseProvider;
        this.withMetaTables = withMetaTables;
        if (databaseNamePattern == null) {
            this.databaseNamePattern = DEFAULT_DATABASE_NAME_PATTERN;
        } else {
            this.databaseNamePattern = databaseNamePattern;
        }
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
                context,
                new RealmFilesProvider(context, databaseNamePattern),
                withMetaTables));
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

        public ProviderBuilder(Context context) {
            applicationContext = context.getApplicationContext();
        }

        public ProviderBuilder baseProvider(InspectorModulesProvider provider) {
            baseProvider = provider;
            return this;
        }

        public ProviderBuilder withMetaTables() {
            this.withMetaTables = true;
            return this;
        }

        public ProviderBuilder databaseNamePattern(Pattern databaseNamePattern) {
            this.databaseNamePattern = databaseNamePattern;
            return this;
        }

        public RealmInspectorModulesProvider build() {
            final InspectorModulesProvider baseProvider =
                    (this.baseProvider != null)
                            ? this.baseProvider
                            : Stetho.defaultInspectorModulesProvider(applicationContext);

            return RealmInspectorModulesProvider.wrap(
                    applicationContext,
                    baseProvider,
                    withMetaTables,
                    databaseNamePattern);
        }
    }
}
