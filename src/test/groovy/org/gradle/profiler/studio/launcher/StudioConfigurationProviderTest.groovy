package org.gradle.profiler.studio.launcher

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class StudioConfigurationProviderTest extends Specification {

    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()
    File studioInstallDir

    def setup() {
        tmpDir.newFolder("Contents/jre/Contents/Home/bin").mkdirs()
        tmpDir.newFolder("jre/bin").mkdirs()
        tmpDir.newFolder("bin").mkdirs()
        studioInstallDir = tmpDir.root
        new File(studioInstallDir, "Contents/jre/Contents/Home/bin/java").createNewFile()
        new File(studioInstallDir, "jre/bin/java.exe").createNewFile()
        new File(studioInstallDir, "jre/bin/java").createNewFile()
    }

    def "should parse linux classpath correctly from studio sh file with #classpathKeyword"() {
        given:
        new File(studioInstallDir, "bin/studio.sh") << """
some text

$classpathKeyword="\$IDE_HOME/lib/lib1.jar"
$classpathKeyword="\$$classpathKeyword:\$IDE_HOME/lib/lib2.jar"
$classpathKeyword="\$$classpathKeyword:\$IDE_HOME/lib/lib1/lib1.jar"

some other text
"""

        when:
        def configuration = StudioConfigurationProvider.getLinuxConfiguration(studioInstallDir.toPath())

        then:
        configuration.classpath == [
            studioInstallDir.toPath().resolve("lib/lib1.jar"),
            studioInstallDir.toPath().resolve("lib/lib2.jar"),
            studioInstallDir.toPath().resolve("lib/lib1/lib1.jar")
        ]

        where:
        classpathKeyword << ["CLASS_PATH", "CLASSPATH"]
    }

    def "should parse windows classpath correctly from studio bat file with #classpathKeyword"() {
        given:
        new File(studioInstallDir, "bin/studio.bat") << """
some text

SET "$classpathKeyword=%IDE_HOME%\\lib\\lib1.jar"
SET "$classpathKeyword=%$classpathKeyword%;%IDE_HOME%\\lib\\lib2.jar"
SET "$classpathKeyword=%$classpathKeyword%;%IDE_HOME%\\lib\\lib1\\lib1.jar"

some other text
"""

        when:
        def configuration = StudioConfigurationProvider.getWindowsConfiguration(studioInstallDir.toPath())

        then:
        configuration.classpath == [
            studioInstallDir.toPath().resolve("lib\\lib1.jar"),
            studioInstallDir.toPath().resolve("lib\\lib2.jar"),
            studioInstallDir.toPath().resolve("lib\\lib1\\lib1.jar")
        ]

        where:
        classpathKeyword << ["CLASS_PATH", "CLASSPATH"]
    }
}
