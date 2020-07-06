import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

object VersionedSettings_1 : GitVcsRoot({
    id("VersionedSettings")
    name = "Gradle Profiler Versioned Settings"
    url = "https://github.com/gradle/gradle-profiler.git"
    branch = "teamcity-versioned-settings"
    authMethod = password {
        userName = "bot-teamcity"
        password = "%github.bot-teamcity.token%"
    }
})
