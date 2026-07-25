// Frontend (JetBrains Client) content module: the dialogs and the client-side profile store.
// It never touches git4idea — all git data arrives over RPC from the backend.
dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        // Provides the projectRemoteTopicListener EP for receiving host-side dialog requests.
        bundledModule("intellij.platform.rpc.topics.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
    }

    implementation(project(":shared"))

    testImplementation(libs.junit)
}
