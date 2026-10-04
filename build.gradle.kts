buildscript {
    dependencies {
        constraints {
            // Raises vulnerable dependencies of the build tools (AGP) to patched versions. Build time only: none of them
            // ship in the APK.
            classpath(libs.build.bouncycastle.bcpkix)
            classpath(libs.build.bouncycastle.bcprov)
            classpath(libs.build.bouncycastle.bcutil)
            classpath(libs.build.commons.lang3)
            classpath(libs.build.httpclient)
            classpath(libs.build.jdom2)
            classpath(libs.build.jose4j)
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
