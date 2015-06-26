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
    compile 'com.uphyca:stetho_realm:0.4.4'
}
```

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

```java
    RealmInspectorModulesProvider.builder(this)
            .withMetaTables()
            .databaseNamePattern(Pattern.compile(".+\\.realm"))
            .build()
```

## License
Stetho-Realm is BSD-licensed.

## TODO

* coexistence with SQLite module.
* error handling when Realm database requires Migration.
* implementation of update, delete, etc.

## deployment memo

1. update version information
2. ./gradlew clean assemble :stetho_realm:publishMavenPublicationToMavenRepository
3. git add, commit, push (on master branch)
