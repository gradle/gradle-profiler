package org.gradle.profiler.mutations

trait FileTreeFixture {

    /**
     * Creates a File instance for a child relative to a parent directory.
     */
    File file(File parent, String child) {
        new File(parent, child)
    }

    /**
     * Normalises file separators in a path to forward slashes.
     * This is necessary for Windows paths to avoid invalid escape sequences in configuration strings.
     * <p>
     * Example: {@code C:\Users\test} becomes {@code C:/Users/test}
     */
    String normaliseFileSeparators(String path) {
        path.replace(File.separatorChar, '/' as char)
    }

    /**
     * Creates a directory (including parent directories) and returns it for chaining.
     */
    File mkdirs(File dir) {
        dir.mkdirs()
        return dir
    }

    /**
     * Reads a directory structure into a nested map representation.
     * <p>
     * Returns:
     * <ul>
     * <li>{@code null} for non-existent paths</li>
     * <li>String content for files</li>
     * <li>Map for directories, where keys are file/directory names and values are their content (strings for files, nested maps for subdirectories)</li>
     * </ul>
     * <p>
     * Example: {@code ["file.txt": "content", "subdir": ["nested.txt": "nested content"]]}
     */
    Object tree(File dir) {
        if (!dir.exists()) {
            return null
        }
        if (!dir.isDirectory()) {
            return dir.text
        }
        def result = [:]
        dir.listFiles().each { file ->
            if (file.isDirectory()) {
                result[file.name] = tree(file)
            } else {
                result[file.name] = file.text
            }
        }
        return result
    }

    /**
     * Writes a nested map structure to disk.
     * <p>
     * The structure map should have:
     * <ul>
     * <li>Keys as file/directory names (String)</li>
     * <li>Values as either strings (file content) or nested maps (subdirectories)</li>
     * </ul>
     * <p>
     * Example: {@code writeTree(dir, ["file.txt": "content", "subdir": ["nested.txt": "nested content"]])}
     */
    void writeTree(File dir, Map structure) {
        mkdirs(dir)
        structure.each { name, content ->
            def target = file(dir, name as String)
            if (content instanceof Map) {
                writeTree(target, content as Map)
            } else if (content instanceof CharSequence) {
                target << content
            } else {
                throw new IllegalArgumentException("Expected a map or string, got: " + content)
            }
        }
    }
}
