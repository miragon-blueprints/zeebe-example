pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "zeebe-example"

include("service:app")
include("service:common-architecture-tests")
include("service:common-zeebe")
include("service:common-zeebe-test")
