package org.gradle.profiler

class TestGitRepo {
    final File directory
    private final File file
    final String originalCommit
    final String modifiedCommit
    final String finalCommit

    TestGitRepo(File directory) {
        this.directory = directory
        directory.mkdirs()

        file = new File(directory, "file.txt")
        file.text = "Original"
        run "git", "init"
        run "git", "config", "user.name", "Test User"
        run "git", "config", "user.email", "test@gradle.com"
        commit "Initial import"
        originalCommit = currentCommit

        file.text = "Modified"
        commit "Modification"
        modifiedCommit = currentCommit

        file.text = "Final"
        commit "Final change"
        finalCommit = currentCommit

        file.text = "Local change"
    }

    void hasOriginalContent() {
        assert file.text == "Original"
    }

    void atOriginalCommit() {
        assert currentCommit == originalCommit
    }

    void hasModifiedContent() {
        assert file.text == "Modified"
    }

    void atModifiedCommit() {
        assert currentCommit == modifiedCommit
    }

    void hasFinalContent() {
        assert file.text == "Final"
    }

    void atFinalCommit() {
        assert currentCommit == finalCommit
    }

    private String getCurrentCommit() {
        def resultLines = run "git", "rev-parse", "HEAD"
        return resultLines.get(0).trim()
    }

    private void commit(String message) {
        run "git", "add", "-A"
        run "git", "commit", "-m", message
    }

    private List<String> run(String... commandLine) {
        println ">> Running ${commandLine.join(" ")}"
        def process = commandLine.execute([], directory)
        def exitValue = process.waitFor()
        def resultLines = process.inputStream.readLines()
        if (exitValue != null) {
            println "<< Exit value: $exitValue"
        }
        resultLines.each {
            println "<< $it"
        }
        process.errorStream.readLines().each {
            println "<! $it"
        }
        assert exitValue == 0
        return resultLines
    }
}
