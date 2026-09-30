import org.jetbrains.intellij.platform.gradle.TestFrameworkType
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
        // Dialog-level regression tests (for example issue #18) need a headless IDE application.
        testFramework(TestFrameworkType.Platform)

        compileOnly(libs.kotlin.serialization.core.jvm)
        compileOnly(libs.kotlin.serialization.json.jvm)
    }

    implementation(project(":shared"))
    testImplementation(kotlin("test"))
}
