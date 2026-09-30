pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net")
        maven("https://maven.parchmentmc.org")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven("https://maven.minecraftforge.net")
        maven("https://maven.parchmentmc.org")
        mavenCentral()
    }
}

rootProject.name = "better-spawns"
