pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HisabBoi"

include(
    ":app",
    ":core:model",
    ":core:ui",
    ":core:data",
    ":core:security",
    ":feature:ledger",
    ":feature:cashbox",
    ":feature:payments",
    ":feature:catalog",
    ":feature:shop",
    ":feature:trust",
    ":feature:reminders",
    ":feature:account",
)
