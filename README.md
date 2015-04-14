# Stetho-Realm

Stetho-Realm は、[Stetho](https://facebook.github.io/stetho)  で [Realm](https://realm.io/)のデータベースの
内容を表示するようにするための Stetho モジュールです。

## Set-up

### Download
grab via Gradle:
```groovy
compile 'com.uphyca.stetho:stetho_realm:0.1.0'
```

注: 今はまだバイナリ配布していないので、stetho_realm ディレクトリをアプリケーションのプロジェクトにコピーして
使ってください(ローカルのMaven リポジトリにインストールできる方はインストールして使ってください。)。

### アプリケーションへの組み込み
`Application` クラスで以下のように Stetho の初期化を行ってください。
ポイントは、MySQL 用の Database インスタンスを取り除き、Realm 用の Database クラスを
追加した `InspectorModulesProvider` を Stetho に渡すことです。

以下はデフォルトの設定で有効になっている SQLite モジュールの代わりに Realm モジュールを
使用する例です。更に独自のモジュールを追加したり、ネットワーク通信のインスペクションに対応する
設定を行う場合は [Stetho](https://facebook.github.io/stetho)を参照して追加の設定を行ってください。

```java
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Stetho.initialize(
                Stetho.newInitializerBuilder(this)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                        .enableWebKitInspector(new MyInspectorModulesProvider(this))
                        .build());
    }

    private static final class MyInspectorModulesProvider implements InspectorModulesProvider {

        private final Context context;

        public MyInspectorModulesProvider(Context context) {
            this.context = context;
        }

        @Override
        public Iterable<ChromeDevtoolsDomain> get() {
            ArrayList<ChromeDevtoolsDomain> domains = new ArrayList<>();
            for (ChromeDevtoolsDomain domain : Stetho.defaultInspectorModulesProvider(context).get()) {
                if (!(domain instanceof com.facebook.stetho.inspector.protocol.module.Database)) {
                    domains.add(domain);
                }
            }
            domains.add(new com.uphyca.stetho_realm.Database(context, new RealmFilesProvider(context)));
            return domains;
        }
    }
}
```

## License
Stetho-Realm is BSD-licensed.

## TODO

* SQLite モジュールとの共存
* Realm データベースに Migration が必要な場合の動作確認とエラー処理
* 読み込み以外の実装
