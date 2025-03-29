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
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
        repositories {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "Women Safety"
include(":app")
 