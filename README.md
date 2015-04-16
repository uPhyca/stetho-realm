# Stetho-Realm

Stetho-Realm は、[Stetho](https://facebook.github.io/stetho)  で [Realm](https://realm.io/)のデータベースの
内容を表示するようにするための Stetho モジュールです。

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
    compile 'com.uphyca.stetho:stetho_realm:0.3.0'
}
```

### アプリケーションへの組み込み
`Application` クラスで以下のように Stetho の初期化を行ってください。

RealmInspectorModulesProvider#wrap() メソッドが引数で渡された InspectorModulesProvider から
SQLite 用のモジュールを取り除き、代わりに Realm 用のモジュールを追加します。

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
                        .enableWebKitInspector(RealmInspectorModulesProvider.wrap(
                                this,
                                Stetho.defaultInspectorModulesProvider(this)))
                        .build());
    }
}
```

また、  ```RealmInspectorModulesProvider.wrap()``` の第三引き数で、メタデータのテーブル
(Realm 0.80.0 では pk と metadataテーブル)の情報を表示するかどうかを指定することができます。

```java
    RealmInspectorModulesProvider.wrap(
                                this,
                                Stetho.defaultInspectorModulesProvider(this),
                                true)
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