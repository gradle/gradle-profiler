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

    def "should parse linux classpath correctly from studio sh file"() {
        given:
        new File(studioInstallDir, "bin/studio.sh") << """
some text

CLASS_PATH="\$IDE_HOME/lib/lib1.jar"
CLASS_PATH="\$CLASS_PATH:\$IDE_HOME/lib/lib2.jar"
CLASS_PATH="\$CLASS_PATH:\$IDE_HOME/lib/lib1/lib1.jar"

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
    }

    def "should parse windows classpath correctly from studio bat file"() {
        given:
        new File(studioInstallDir, "bin/studio.bat") << """
some text

SET "CLASS_PATH=%IDE_HOME%\\lib\\lib1.jar"
SET "CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\lib2.jar"
SET "CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\lib1\\lib1.jar"

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
    }

}
