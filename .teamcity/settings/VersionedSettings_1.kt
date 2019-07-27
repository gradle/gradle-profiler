import jetbrains.buildServer.configs.kotlin.v2018_2.vcs.GitVcsRoot

object VersionedSettings_1 : GitVcsRoot({
    id("VersionedSettings")
    name = "Gradle Profiler Versioned Settings"
    url = "git@github.com:gradle/gradle-profiler.git"
    branch = "teamcity-versioned-settings"
    authMethod = uploadedKey {
        uploadedKey = "id_rsa_gradlewaregitbot"
    }
})
