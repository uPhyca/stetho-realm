package com.uphyca.stetho_realm;

import android.content.Context;

import com.facebook.stetho.InspectorModulesProvider;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;

import java.util.ArrayList;
import java.util.List;

/**
 * Stetho へモジュールを組み込むための InspectorModulesProvider です。
 *
 * Stetho の初期化の際に、InspectorModulesProvider インスタンスを
 * {@link #wrap(Context, InspectorModulesProvider)} メソッドに渡し、その返り値を
 * {@link com.facebook.stetho.Stetho.InitializerBuilder#enableWebKitInspector(InspectorModulesProvider)}
 * に渡してください。
 *
 * <pre>
 *     Stetho.initialize(
 *         Stetho.newInitializerBuilder(this)
 *             .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
 *             .enableWebKitInspector(RealmInspectorModulesProvider.wrap(
 *                 this,
 *                 Stetho.defaultInspectorModulesProvider(this)))
 *             .build());
 * </pre>
 */
public class RealmInspectorModulesProvider implements InspectorModulesProvider {
    public static InspectorModulesProvider wrap(Context context, InspectorModulesProvider provider) {
        return new RealmInspectorModulesProvider(context, provider);
    }

    private final Context context;
    private final InspectorModulesProvider baseProvider;

    private RealmInspectorModulesProvider(Context context, InspectorModulesProvider baseProvider) {
        this.context = context.getApplicationContext();
        this.baseProvider = baseProvider;
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
        modules.add(new com.uphyca.stetho_realm.Database(context, new RealmFilesProvider(context)));
        return modules;
    }
}
