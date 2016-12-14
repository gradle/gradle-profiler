package org.gradle.profiler.mutations

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApplyChangetoAndroidManifestFileMutatorTest extends Specification
{
  @Rule
  TemporaryFolder tmpDir = new TemporaryFolder()

  def "adds and removes fake permission to end of android manifest file"()
  {
    def sourceFile = tmpDir.newFile( "AndroidManifest.xml" )
    sourceFile.text = "<manifest></manifest>"
    def mutator = new ApplyChangetoAndroidManifestFileMutator( sourceFile )

    when:
    mutator.beforeBuild()

    then:
    sourceFile.text == '<manifest><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'

    when:
    mutator.beforeBuild()

    then:
    sourceFile.text == "<manifest></manifest>"

    when:
    mutator.beforeBuild()

    then:
    sourceFile.text == '<manifest><permission android:name="com.acme.SOME_PERMISSION"/></manifest>'
  }

  def "reverts changes when nothing has been applied"()
  {
    def sourceFile = tmpDir.newFile( "strings.xml" )
    sourceFile.text = "<manifest></manifest>"
    def mutator = new ApplyChangetoAndroidManifestFileMutator( sourceFile )

    when:
    mutator.cleanup()

    then:
    sourceFile.text == "<manifest></manifest>"
  }

  def "reverts changes when changes has been applied"()
  {
    def sourceFile = tmpDir.newFile( "strings.xml" )
    sourceFile.text = "<manifest></manifest>"
    def mutator = new ApplyChangetoAndroidManifestFileMutator( sourceFile )

    when:
    mutator.beforeBuild()
    mutator.cleanup()

    then:
    sourceFile.text == "<manifest></manifest>"
  }
}
