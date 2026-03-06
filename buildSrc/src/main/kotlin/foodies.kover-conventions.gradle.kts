plugins {
    id("org.jetbrains.kotlinx.kover")
}

kover {
    reports {
        total {
            verify {
                rule {
                    minBound(60)
                }
            }
        }
    }
}
