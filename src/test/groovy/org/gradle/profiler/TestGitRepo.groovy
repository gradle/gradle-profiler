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
        run "git", "add", "-A"
        run "git", "commit", "-m", "Initial import"
        originalCommit = currentCommit

        file.text = "Modified"
        run "git", "add", "-A"
        run "git", "commit", "-m", "Modification"
        modifiedCommit = currentCommit

        file.text = "Final"
        run "git", "add", "-A"
        run "git", "commit", "-m", "Final change"
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

    private void run(String... commandLine) {
        assert commandLine.execute([], directory).waitFor() == 0
    }

    private String getCurrentCommit() {
        def result = ["git", "rev-parse", "HEAD"].execute([], directory)
        assert result.waitFor() == 0
        def resultLines = result.inputStream.readLines()
        return resultLines.get(0).trim()
    }
}
