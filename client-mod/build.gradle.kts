// NovoMC Client: a Forge 1.8.9 mod (Java 8, Mixins).
// Build with `gradlew build` (needs JDK 17 to run Gradle; compiles with JDK 8).
// Output: build/libs/novomc-<version>.jar

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val baseGroup: String by project
val mcVersion: String by project
val modid: String by project
val mixinGroup = "$baseGroup.mixin"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modid.json")
    }
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}

// Mixin needs the resources next to the classes in the dev environment.
sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

// Dependencies bundled into the mod jar.
val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    // WebP decoding for Modrinth pack icons (pure Java; Java 8 ImageIO has no WebP).
    shadowImpl("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(modid)
    // Lets Forge find the mod and start Mixin from a plain mods-folder jar.
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("mixinGroup", mixinGroup)
    filesMatching(listOf("mcmod.info", "mixins.$modid.json")) {
        expand(inputs.properties)
    }
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
}

tasks.assemble.get().dependsOn(tasks.remapJar)

// Standalone Sodium Renderer: build/libs/novomc-sodium-<version>.jar, the port alone
// (no NovoMC modules) for testing it on plain Forge. Built with `gradlew sodiumJar`.
val standalone: SourceSet = sourceSets.create("standalone") {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
}
val sodiumShadowJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveBaseName.set("novomc-sodium")
    archiveClassifier.set("non-obfuscated-with-deps")
    from(standalone.output)
    from(sourceSets.main.get().output) {
        include("com/novomc/client/sodium/**", "assets/novomc/shaders/sodium/**", "mixins.$modid.refmap.json",
            "com/novomc/client/mixin/MixinChunkRenderDispatcher*", "com/novomc/client/mixin/MixinVboRenderList*",
            "com/novomc/client/mixin/MixinVertexBuffer*", "com/novomc/client/mixin/accessor/ChunkRenderContainerAccessor*")
    }
    configurations = listOf(shadowImpl)
    dependencies { include(dependency("org.spongepowered:mixin")) }
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/services/javax.annotation.processing.Processor")
    manifest.attributes(mapOf(
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "MixinConfigs" to "mixins.novomc_sodium.json",
    ))
}
val sodiumJar by tasks.registering(net.fabricmc.loom.task.RemapJarTask::class) {
    archiveBaseName.set("novomc-sodium")
    archiveClassifier.set("")
    input.set(sodiumShadowJar.flatMap { it.archiveFile })
}
