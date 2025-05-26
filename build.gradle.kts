plugins {
    id("java")
}

group = "org.example"
version = "1.0"

repositories {
    maven("https://repo.purpurmc.org/snapshots/") // Репозиторий для Purpur
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Репозиторий для Spigot
    maven("https://papermc.io/repo/repository/maven-public/")
    mavenCentral() // Репозиторий для библиотек, таких как Kyori Adventure
}

dependencies {
    compileOnly("net.kyori:adventure-api:4.19.0") // Последняя версия Adventure API
    compileOnly("org.purpurmc.purpur:purpur-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-platform-bukkit:4.3.4") // Последняя версия платформы Bukkit
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks.jar {
    archiveFileName.set("MyPurpurPlugin.jar")
}