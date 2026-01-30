rootProject.name = "LegendaryStormseeker"

includeBuild("vendor/LegendaryCore") {
    dependencySubstitution {
        substitute(module("com.example:LegendaryCore")).using(project(":"))
    }
}
