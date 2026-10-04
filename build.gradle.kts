// Vulnerable dependencies of the build tools (AGP, lint…) are raised to patched versions, both on the build classpath
// and in what the build tools resolve themselves (e.g. the lint classpath). Build time only: none of them ship in the APK.
buildscript {
    dependencies {
        constraints {
            libs.bundles.build.patched.get().forEach { classpath("${it.module}:${it.version}") }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

/** Adds [constraints] to every resolved module, so that they also apply to the configurations created by plugins. */
@CacheableRule
abstract class AddConstraints @Inject constructor(private val constraints: List<String>) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) = context.details.allVariants {
        withDependencyConstraints { constraints.forEach { add(it) } }
    }
}

val patched = libs.bundles.build.patched.get().map { "${it.module}:${it.version}" }
subprojects {
    dependencies.components.all<AddConstraints> { params(patched) }
}
