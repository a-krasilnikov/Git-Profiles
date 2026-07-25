import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware

plugins {
    // NB: do not add the `application` plugin here (the modular-plugin template carries it).
    // Its distZip/distTar write build/distributions/<name>-<version>.zip — the exact path
    // buildPlugin uses — so `build` silently overwrites the plugin artifact with a JVM app
    // distribution (bin/ scripts + *-base.jar) that no IDE can install.
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("rpc") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

subprojects {
    apply(plugin = "org.jetbrains.intellij.platform.module")
    apply(plugin = "rpc")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2025.3.5")

        pluginModule(implementation(project(":shared")))
        pluginModule(implementation(project(":frontend")))
        pluginModule(implementation(project(":backend")))

        testFramework(TestFrameworkType.Platform)
    }
}

// Split mode controls only how `runIde` launches locally, not the plugin's architecture (it is
// modular / RPC-based either way). Default to a monolithic sandbox — one window, the whole plugin
// loaded, easiest for manual testing. Opt into split-mode local testing with `-PsplitMode=true`
// (then use the `runIdeBackend` + `runIdeFrontend` compound and work in the JetBrains Client window).
val enableSplitMode = providers.gradleProperty("splitMode").map(String::toBoolean).getOrElse(false)

intellijPlatform {
    splitMode = enableSplitMode
    // Always BOTH: this describes the plugin itself (it has a frontend part and a backend part),
    // not how `runIde` happens to launch. The `runIdeBackend` / `runIdeFrontend` tasks exist
    // regardless of the `splitMode` flag, and both sandboxes need the plugin installed.
    pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BOTH
}
