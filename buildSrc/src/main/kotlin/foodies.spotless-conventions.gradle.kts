import com.diffplug.spotless.kotlin.KtfmtStep.TrailingCommaManagementStrategy.COMPLETE

plugins { id("com.diffplug.spotless") }

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val ktfmtVersion = libs.findVersion("ktfmt").get().requiredVersion

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt(ktfmtVersion).kotlinlangStyle().configure {
            it.setRemoveUnusedImports(true)
            it.setTrailingCommaManagementStrategy(COMPLETE)
        }
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt(ktfmtVersion).kotlinlangStyle().configure {
            it.setRemoveUnusedImports(true)
            it.setTrailingCommaManagementStrategy(COMPLETE)
        }
    }
}
