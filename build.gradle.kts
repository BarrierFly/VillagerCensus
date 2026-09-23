import org.gradle.jvm.tasks.Jar

plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17.493" apply false
    id("dev.architectury.loom-no-remap") version "1.17.493" apply false
    id("maven-publish")
}

val current = stonecutter.current.project
val mcVersion = current
val isUnobfuscated = current >= "26.1"

if (isUnobfuscated) {
    apply(plugin = "dev.architectury.loom-no-remap")
} else {
    apply(plugin = "dev.architectury.loom")
}

base {
    archivesName.set(property("archives_base_name") as String)
}
version = "${property("mod_version")}+${current}"
group = property("maven_group") as String

val javaInt = (findProperty("java_version") as String?)?.toIntOrNull() ?: 21

repositories {
    mavenCentral()
    maven {
        name = "Architectury"
        url = uri("https://maven.architectury.dev/")
    }
    maven {
        name = "Sponge"
        url = uri("https://repo.spongepowered.org/repository/maven-public/")
    }
    maven {
        name = "MinecraftForge"
        url = uri("https://maven.minecraftforge.net/")
    }
    maven {
        name = "NeoForge"
        url = uri("https://maven.neoforged.net/releases")
    }
    maven {
        name = "FallenBreath"
        url = uri("https://maven.fallenbreath.me/releases")
    }
    maven {
        name = "Masa / Sakura"
        url = uri("https://masa.dy.fi/maven/sakura-ryoko")
        content {
            includeGroupAndSubgroups("fi.dy.masa")
        }
    }
}

val loom = extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>()

loom.apply {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir("../../run")
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")

    if (!isUnobfuscated) {
        "mappings"(loom.layered {
            officialMojangMappings()
        })
    }

    val modImpl = if (isUnobfuscated) "implementation" else "modImplementation"

    modImpl("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImpl("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    modImpl("fi.dy.masa.malilib:malilib-fabric-${current}:${property("malilib_version")}")
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", version.toString())
    inputs.property("mc_version", mcVersion)
    inputs.property("java_version", javaInt)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to version.toString(),
                "mc_version" to mcVersion,
                "java_version" to javaInt
            )
        )
    }

    filesMatching("villagercensus.mixins.json") {
        expand(mapOf("java_version" to javaInt))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaInt)
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaInt))
    withSourcesJar()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = true
}

tasks.named<Jar>("jar") {
    from(rootProject.file("README.md")) {
        rename { "villagercensus-README.md" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
    }
}
