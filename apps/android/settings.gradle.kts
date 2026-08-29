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

rootProject.name = "RakyzuMusicAndroid"

include(":app")
include(":core:data")
include(":core:database")
include(":core:designsystem")
include(":core:model")
include(":feature:auth")
include(":feature:home")
include(":feature:profile")
