plugins {
    `kotlin-dsl`
}

repositories {
    jcenter()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:5.9.0") {
        exclude(group = "org.codehaus.groovy", module = "groovy-xml")
    }
}
