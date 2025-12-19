package org.gradle.profiler.fixtures

import org.gradle.profiler.fixtures.compatibility.gradle.GradleVersionCompatibility
import spock.lang.Shared

abstract class AbstractProfilerIntegrationTest extends AbstractBaseProfilerIntegrationTest {

    @Shared
    List<String> supportedGradleVersions =
        GradleVersionCompatibility.gradleVersionsSupportedOnCurrentJvm(GradleVersionCompatibility.testedGradleVersions).collect {
            it.version
        }

    @Shared
    String minimalSupportedGradleVersion = supportedGradleVersions.first()
    @Shared
    String latestSupportedGradleVersion = supportedGradleVersions.last()

    boolean isCurrentJvmSupportedGradleVersionRange() {
        return minimalSupportedGradleVersion != latestSupportedGradleVersion
    }

    /**
     * Returns the range of Gradle versions supported by the current JVM,
     * ensuring range bounds are distinct.
     */
    List<String> currentJvmSupportedGradleVersionRange() {
        assert isCurrentJvmSupportedGradleVersionRange()
        [minimalSupportedGradleVersion, latestSupportedGradleVersion]
    }

    def instrumentedBuildScript() {
        buildFile.text = """
apply plugin: BasePlugin
println "<gradle-version: " + gradle.gradleVersion + ">"
println "<tasks: " + gradle.startParameter.taskNames + ">"
println "<daemon: " + gradle.services.get(org.gradle.internal.environment.GradleBuildEnvironment).longLivingProcess + ">"
println "<invocations: " + (++Counter.invocations) + ">"

class Counter {
    static int invocations = 0
}
"""
    }

    def writeBuckw() {
        def buckw = file("buckw")
        buckw.text = '''
#!/usr/bin/env bash

echo "[-] PARSING BUCK FILES...FINISHED 0.3s [100%]"
if [ $1 = "targets" ]
then
    if [ "$2" = "--type" ]
    then
        echo "//target:$3_1"
        echo "//target:$3_2"
        echo "//target/child:$3_3"
        echo "//target/child:$3_4"
    else
        echo "//target:android_binary"
        echo "//target:java_library"
        echo "//target:cpp_library"
        echo "//target/child:android_library"
        echo "//target/child:cpp_library"
    fi
else
    echo "building $@"
fi
'''
        buckw.executable = true
    }

    def createSimpleBazelWorkspace() {
        new File(projectDir, "WORKSPACE").createNewFile()
        new File(projectDir, "BUILD").text = '''
genrule(
  name = "hello",
  outs = ["hello_world.txt"],
  cmd = "echo Hello World > $@",
)'''
    }
}
