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
    compile 'com.uphyca.stetho:stetho_realm:0.3.1'
}
```

### Integration
In your `Application` class, please initialize Stetho with `RealmInspectorModulesProvider.wrap()`.

`RealmInspectorModulesProvider.wrap()` returns customized InspectorModulesProvider which removes
SQLite modules and appends Realm module instead.

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

You can use `RealmInspectorModulesProvider.wrap(Context,InspectorModulesProvider,boolean)`
to specify whether Stetho displays metadata tables. They are not displayed by default.

```java
    RealmInspectorModulesProvider.wrap(
                                this,
                                Stetho.defaultInspectorModulesProvider(this),
                                true)
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