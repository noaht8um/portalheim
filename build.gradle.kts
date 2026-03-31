plugins {
    java
}

val minecraftVersion = "1.21.4"
val defaultVersion = "0.1.0-SNAPSHOT"

group = "dev.noaht8um"
version = providers.gradleProperty("releaseVersion").orElse(defaultVersion).get()

base {
    archivesName.set("portalheim-paper-mc$minecraftVersion")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${minecraftVersion}-R0.1-SNAPSHOT")
    compileOnly("xyz.jpenilla:squaremap-api:1.3.12")
    testImplementation("io.papermc.paper:paper-api:${minecraftVersion}-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
