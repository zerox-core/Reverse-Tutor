pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ReverseTutorNative"

include(":app")
include(":core:protocol")
include(":core:model")
include(":core:design")
include(":core:domain")
include(":core:data")
include(":core:llm")
include(":core:remote")
include(":feature:chat")
include(":feature:memory")
include(":feature:sources")
include(":feature:settings")
