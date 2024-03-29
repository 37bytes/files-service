# files-service

## Релиз в Maven Central

1. Импорт GPG ключа. Создать файл
    maven_private.key с содержимым из Vault dev/maven_central/GPG_PRIVATE_KEY.
```bash
  gpg --import maven_private.key   
```

2. Настроить maven. Отредактировать ~/.m2/settings.xml 
Значения брать из Vault dev/maven_central

```xml
<settings>
    <servers>
        <server>
            <id>ossrh</id>
            <username>USERNAME</username>
            <password>PASSWORD</password>
        </server>
    </servers>
    <profiles>
        <profile>
            <id>ossrh</id>
            <activation>
                <activeByDefault>false</activeByDefault>
            </activation>
            <properties>
                <gpg.keyname>GPG_KEYNAME</gpg.keyname>
                <gpg.passphrase>GPG_PASSPHRASE</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
```

3. В pom.xml обновить версию.

4. В IDE выбрать Maven профиль osshr и запустить сначала clean, после deploy

5. Для проверки зайти https://s01.oss.sonatype.org и в поиске вбить
   dev.b37.libs и найти артифакт и версию


В maven central появляется не сразу, синкается через некоторое время (~1-3 дня)