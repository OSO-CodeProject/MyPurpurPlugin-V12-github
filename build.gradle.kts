plugins {
    id("java")
    id("com.diffplug.spotless") version "6.25.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

group = "org.example"
version = "1.0"

repositories {
    maven("https://repo.purpurmc.org/snapshots/") // Репозиторий для Purpur
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Репозиторий для Spigot
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
    mavenCentral() // Репозиторий для библиотек, таких как Kyori Adventure
}

dependencies {
    compileOnly("net.kyori:adventure-api:4.19.0") // Последняя версия Adventure API
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-platform-bukkit:4.3.4") // Последняя версия платформы Bukkit
    compileOnly("org.jetbrains:annotations:24.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

tasks.jar {
    archiveFileName.set("MyPurpurPlugin.jar")
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.jar)
}

spotless {
    java {
        googleJavaFormat()
        target("src/**/*.java")
    }
}
