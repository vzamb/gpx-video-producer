pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "gpx-video-producer"

include(":app")

include(":core:model")
include(":core:database")
include(":core:common")
include(":core:ui")
include(":core:overlay-renderer")

include(":feature:home")
include(":feature:project")
include(":feature:timeline")
include(":feature:preview")
include(":feature:export")
include(":feature:gpx")
include(":feature:overlays")
include(":feature:templates")

include(":lib:gpx-parser")
include(":lib:media-utils")
include(":lib:strava")
