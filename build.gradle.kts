import com.diffplug.spotless.kotlin.KtfmtStep

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.assert)
}

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle().configure {
            it.setTrailingCommaManagementStrategy(
                KtfmtStep.TrailingCommaManagementStrategy.ONLY_ADD
            )
            it.setRemoveUnusedImports(true)
        }
    }
}
