dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.kernel.backend")
        bundledModule("intellij.platform.rpc.backend")
        bundledModule("intellij.platform.backend")

        bundledModule("com.intellij.modules.json")
        bundledModule("org.jetbrains.plugins.yaml")
        bundledModule("com.intellij.properties")
        compatiblePlugin("com.jetbrains.php")
    }

    implementation(project(":shared"))
}
