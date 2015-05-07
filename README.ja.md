# Stetho-Realm

Stetho-Realm は、[Stetho](https://facebook.github.io/stetho)  で [Realm](https://realm.io/)のデータベースの内容を表示するようにするための Stetho モジュールです。

Stetho がもともと持っている SQLite データベースの内容を表示する機能を置き換える形で Realm データベースの内容を表示します。

## Set-up

### Download
grab via Gradle:
```groovy
repositories {
    maven {
        url 'https://github.com/uPhyca/stetho-realm/raw/master/maven-repo'
    }
}

dependencies {
    compile 'com.uphyca:stetho_realm:0.4.0'
}
```

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

        Stetho.initialize(
                Stetho.newInitializerBuilder(this)
                        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
                        .enableWebKitInspector(RealmInspectorModulesProvider.builder(this).build())
                        .build());
    }
}
```

`RealmInspectorModulesProvider.ProviderBuilder` の各種メソッドを呼び出すことで、メタデータのテーブル
(Realm 0.80.2 では pk と metadataテーブル)の情報を表示するかどうかや、データベースファイル名のパターンを
指定することができます。

```java
    RealmInspectorModulesProvider.builder(this)
            .withMetaTables()
            .databaseNamePattern(Pattern.compile(".+\\.realm"))
            .build()
```

## License
Stetho-Realm is BSD-licensed.

## TODO

* SQLite モジュールとの共存
* Realm データベースに Migration が必要な場合の動作確認とエラー処理
* 読み込み以外の実装

## deployメモ

1. バージョン番号を変更
2. ./gradlew clean assemble :stetho_realm:publishMavenPublicationToMavenRepository
3. git に add して commit して push (masterブランチで！)
