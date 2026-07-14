import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

kotlin {
    compilerOptions {
        // ToolWindowFactory is a Kotlin interface. Compatibility mode emits subclass
        // bridges to deprecated/experimental default methods that Plugin Verifier
        // reports as usages even though this factory never calls them.
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
    }

    implementation(project(":shared"))
    testImplementation(kotlin("test"))
}
