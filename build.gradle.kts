import com.diffplug.spotless.kotlin.KtfmtStep

plugins { alias(libs.plugins.spotless) }

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
