# Stetho-Realm

Stetho-Realm は、[Stetho](https://facebook.github.io/stetho)  で [Realm](https://realm.io/)のデータベースの内容を表示するようにするための Stetho モジュールです。

Stetho がもともと持っている SQLite データベースの内容を表示する機能を置き換える形で Realm データベースの内容を表示します。

## Set-up

### Download
grab via Gradle:
```groovy
repositories {
    maven { url 'https://github.com/linchaolong/stetho-realm/raw/master/maven-repo' } // realm-stetho
}

dependencies {
    compile 'com.facebook.stetho:stetho:1.4.2'
    compile 'com.uphyca:stetho_realm:2.0.1'
}
```

Stetho-Realm 2.0 は、 Stetho 1.1以降、Realm 2.0.0 以降に対応しています。Realm 0.80.0 からRealm 1.2.0でStetho-Realmを使用する場合は Stetho-Realm 0.x の最新版を利用してください。

### アプリケーションへの組み込み
`Application` クラスで以下のように Stetho の初期化を行ってください。

`RealmInspectorModulesProvider.ProviderBuilder` を用いて `InspectorModulesProvider` を作成します。
`RealmInspectorModulesProvider.ProviderBuilder` はデフォルトのモジュールリストからSQLite 用の
モジュールを取り除き、代わりに Realm 用のモジュールを追加します。
`RealmInspectorModulesProvider.ProviderBuilder#baseProvider(InspectorModulesProvider)`を用いて
デフォルト以外の InspectorModulesProvider を使用させることもできます。

以下はデフォルトの設定で有効になっている SQLite モジュールの代わりに Realm モジュールを
使用する例です。

```java
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Realm.init(this);

        Stetho.initialize(
                Stetho.newInitializerBuilder(this)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                        .enableWebKitInspector(RealmInspectorModulesProvider.builder(this).build())
                        .build());
    }
}
```

`RealmInspectorModulesProvider.ProviderBuilder` の各種メソッドを呼び出すことで、データベースファイルを
探すフォルダの指定、表示する件数の上限、表示をidの昇順にする加か降順にするか、メタデータのテーブル
(pk と metadataテーブル)の情報を表示するかどうか、復号に使用するキー、
データベースファイル名のパターンを指定することができます。

```java
    RealmInspectorModulesProvider.builder(this)
            .withFolder(getCacheDir())
            .withEncryptionKey("encrypted.realm", key)
            .withMetaTables()
            .withDescendingOrder()
            .withLimit(1000)
            .databaseNamePattern(Pattern.compile(".+\\.realm"))
            .build()
```

## デバッグビルドのみに Stetho を組み込む方法

英語ですが以下のページを参考にしてください。

http://littlerobots.nl/blog/stetho-for-android-debug-builds-only/

## License

Stetho-Realm is BSD-licensed.

## TODO

* SQLite モジュールとの共存
* 読み込み以外の実装

## deployメモ

1. バージョン番号を変更
2. ./gradlew clean assemble :stetho_realm:publishMavenPublicationToMavenRepository
3. git に add して commit して push (masterブランチで！)
