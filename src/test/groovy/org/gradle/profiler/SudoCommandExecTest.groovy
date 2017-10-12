package org.gradle.profiler

import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class SudoCommandExecTest extends Specification {

    def "should add sudo when running as non-root"() {
        given:
        System.setProperty("user.name", "non-root")
        SudoCommandExec sudoCommandExec = new SudoCommandExec()
        ProcessBuilder processBuilder = new ProcessBuilder()
        processBuilder.command(commandLine)

        when:
        sudoCommandExec.maybeAddSudo(processBuilder)

        then:
        processBuilder.command() == (["sudo", "-n"] + commandLine)

        where:
        commandLine << [["perf", "record"], ["sudoedit"], "visudo"]
    }

    def "should not add sudo when sudo is already part of the command"() {
        given:
        System.setProperty("user.name", "non-root")
        SudoCommandExec sudoCommandExec = new SudoCommandExec()
        ProcessBuilder processBuilder = new ProcessBuilder()
        processBuilder.command(commandLine)

        when:
        sudoCommandExec.maybeAddSudo(processBuilder)

        then:
        processBuilder.command() == commandLine

        where:
        commandLine << [["sudo", "perf", "record"], ["sudo perf record"], ["sudo perf", "record"]]
    }

    def "should not add sudo when running as root"() {
        given:
        System.setProperty("user.name", "root")
        SudoCommandExec sudoCommandExec = new SudoCommandExec()
        ProcessBuilder processBuilder = new ProcessBuilder()
        processBuilder.command("perf", "record")

        when:
        sudoCommandExec.maybeAddSudo(processBuilder)

        then:
        processBuilder.command() == ["perf", "record"]
    }
}
