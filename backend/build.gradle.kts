import org.jetbrains.intellij.platform.gradle.TestFrameworkType

// Backend (host) content module: runs where the git repositories physically live. It owns all
// git4idea usage and the RPC implementation.
dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.rpc.topics.backend")
        bundledModule("intellij.platform.backend")

        // Bundled Git integration: repository detection + running `git config`.
        bundledPlugin("Git4Idea")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.VCS)
    }

    implementation(project(":shared"))

    testImplementation(libs.junit)
}
