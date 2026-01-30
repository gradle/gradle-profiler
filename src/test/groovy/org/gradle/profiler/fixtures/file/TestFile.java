package org.gradle.profiler.fixtures.file;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A File subclass that provides testing utilities for file operations.
 */
public class TestFile extends File {

    public TestFile(File file, Object... path) {
        super(join(file, path).getAbsolutePath());
    }

    public TestFile(URI uri) {
        this(new File(uri));
    }

    public TestFile(String path) {
        this(new File(path));
    }

    public TestFile(URL url) {
        this(toUri(url));
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static File join(File file, Object... path) {
        File current = file.getAbsoluteFile();
        for (Object segment : path) {
            current = new File(current, segment.toString());
        }
        try {
            return current.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not canonicalize '%s'.", current), e);
        }
    }

    public TestFile file(Object... path) {
        return new TestFile(this, path);
    }

    public TestFile createFile(Object... path) {
        TestFile file = file(path);
        file.createFile();
        return file;
    }

    public TestFile createDir(Object... path) {
        TestFile dir = new TestFile(this, path);
        dir.mkdirs();
        return dir;
    }

    public TestFile createFile() {
        try {
            assertTrue(isFile() || createNewFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public TestFile write(Object content) {
        try {
            getParentFile().mkdirs();
            Files.write(toPath(), content.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write to test file '%s'", this), e);
        }
        return this;
    }

    public TestFile setText(String content) {
        return write(content);
    }

    public String getText() {
        assertIsFile();
        try {
            return new String(Files.readAllBytes(toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from test file '%s'", this), e);
        }
    }

    public TestFile leftShift(Object content) {
        try {
            getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(this, true)) {
                writer.write(content.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not append to test file '%s'", this), e);
        }
        return this;
    }

    public List<String> readLines() {
        assertIsFile();
        try {
            return Files.readAllLines(toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read lines from test file '%s'", this), e);
        }
    }

    public void copyTo(File target) {
        if (isFile()) {
            copyFile(this, target);
        } else if (isDirectory()) {
            copyDir(this, target);
        } else {
            throw new RuntimeException(String.format("Don't know how to copy '%s'", this));
        }
    }

    private static void copyFile(File source, File target) {
        try {
            target.getParentFile().mkdirs();
            Files.copy(source.toPath(), target.toPath());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not copy test file '%s' to '%s'", source, target), e);
        }
    }

    private static void copyDir(File source, File target) {
        target.mkdirs();
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(target, file.getName());
                if (file.isDirectory()) {
                    copyDir(file, targetFile);
                } else {
                    copyFile(file, targetFile);
                }
            }
        }
    }

    public boolean deleteDir() {
        if (isDirectory()) {
            File[] files = listFiles();
            if (files != null) {
                for (File file : files) {
                    new TestFile(file).deleteDir();
                }
            }
        }
        return delete();
    }

    public void forceDeleteDir() {
        if (isDirectory()) {
            File[] files = listFiles();
            if (files != null) {
                for (File file : files) {
                    new TestFile(file).forceDeleteDir();
                }
            }
        }
        if (exists()) {
            setWritable(true);
            if (!delete()) {
                throw new RuntimeException(String.format("Could not delete '%s'", this));
            }
        }
    }

    public TestFile assertExists() {
        assertTrue(String.format("Expected '%s' to exist", this), exists());
        return this;
    }

    public TestFile assertDoesNotExist() {
        assertFalse(String.format("Expected '%s' to not exist", this), exists());
        return this;
    }

    public TestFile assertIsFile() {
        assertTrue(String.format("Expected '%s' to be a file", this), isFile());
        return this;
    }

    public TestFile assertIsDir() {
        assertTrue(String.format("Expected '%s' to be a directory", this), isDirectory());
        return this;
    }

    public TestFile assertContents(String expected) {
        assertEquals(expected, getText());
        return this;
    }

    public TestFile assertHasDescendants(String... descendants) {
        return assertHasDescendants(Arrays.asList(descendants));
    }

    public TestFile assertHasDescendants(Iterable<String> descendants) {
        assertIsDir();
        Set<String> expected = new TreeSet<>();
        for (String d : descendants) {
            expected.add(d.replace('/', File.separatorChar));
        }

        Set<String> actual = new TreeSet<>();
        collectDescendants(this, "", actual);

        assertEquals(expected, actual);
        return this;
    }

    private void collectDescendants(File dir, String prefix, Set<String> descendants) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String relativePath = prefix.isEmpty() ? file.getName() : prefix + File.separator + file.getName();
                if (file.isFile()) {
                    descendants.add(relativePath);
                } else if (file.isDirectory()) {
                    collectDescendants(file, relativePath, descendants);
                }
            }
        }
    }

    public Set<String> allDescendants() {
        Set<String> descendants = new TreeSet<>();
        collectDescendants(this, "", descendants);
        return descendants;
    }

    public void moveToDirectory(File target) {
        if (target.exists() && !target.isDirectory()) {
            throw new RuntimeException(String.format("Target '%s' is not a directory", target));
        }
        target.mkdirs();
        File destination = new File(target, getName());
        if (!renameTo(destination)) {
            throw new RuntimeException(String.format("Could not move '%s' to '%s'", this, destination));
        }
    }

    public List<TestFile> listFilesAsTestFiles() {
        File[] files = listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(files)
            .map(TestFile::new)
            .collect(Collectors.toList());
    }

    public TestFile touch() {
        try {
            if (!exists()) {
                getParentFile().mkdirs();
                createNewFile();
            } else {
                setLastModified(System.currentTimeMillis());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

}
