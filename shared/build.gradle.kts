// The shared module loads in both the frontend (JetBrains Client) and the backend (host).
// It carries only the RPC contract and its serializable payloads; the RPC compiler plugin and
// kotlinx.serialization plugin are applied by the root build's `subprojects {}` block.
dependencies {
    intellijPlatform {
        // Carries ProjectRemoteTopic, used for the backend → frontend "open the dialog" request.
        bundledModule("intellij.platform.rpc.topics")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
    }
}
