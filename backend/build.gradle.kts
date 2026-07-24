dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")
    }

    implementation(project(":shared"))
    // IntelliJ Platform already provides kotlinx.serialization. Bundling a second
    // runtime copy makes shared RPC serializers and backend cache code load
    // KSerializer from different plugin classloaders.
    compileOnly(libs.kotlin.serialization.json.jvm)

    testImplementation(kotlin("test"))
    testImplementation(libs.jol.core)
}
