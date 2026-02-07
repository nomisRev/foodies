plugins { id("dev.detekt") }

detekt {
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = true
}
