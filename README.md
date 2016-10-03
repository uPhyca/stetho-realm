# Stetho-Realm

Stetho-Realm is a [Realm](https://realm.io/) module for [Stetho](https://facebook.github.io/stetho).

It displays Realm database content in Stetho instead of SQLite database content.

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
    compile 'com.facebook.stetho:stetho:1.4.1'
}
```

Stetho-Realm 2.0 supports Stetho 1.1 or newer and Realm 2.0.0 or newer. If you'd like to use Stetho-Realm with Realm 0.80.0 to Realm 1.2.0, please use the latest version of Realm 0.x.

### Integration
In your `Application` class, please initialize Stetho with `RealmInspectorModulesProvider.ProviderBuilder` as follows.

`RealmInspectorModulesProvider.ProviderBuilder` replaces SQLite module with Realm module.
You can use `RealmInspectorModulesProvider.ProviderBuilder#baseProvider(InspectorModulesProvider)`
in order to customized `InspectorModulesProvider` instead of default provider.

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

By calling some methods in `RealmInspectorModulesProvider.ProviderBuilder`,
you can include metadata table in table list, and can provide database file name pattern.
And also you can specify base folder for database files, encryption keys, limit, sort order.

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

## use Stetho in debug build only

http://littlerobots.nl/blog/stetho-for-android-debug-builds-only/

## License

Stetho-Realm is BSD-licensed.

## TODO

* coexistence with SQLite module.
* implementation of update, delete, etc.

## deployment memo

1. update version information
2. ./gradlew clean assemble :stetho_realm:publishMavenPublicationToMavenRepository
3. git add, commit, push (on master branch)
