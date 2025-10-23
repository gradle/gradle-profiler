package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model an async-profiler distribution, from 3+.
 * <p>
 * Note, that Async-Profiler executable was as shell script up to version 2.x, also
 * the async profile library had the `.so` extension even on macOS, (a .dylib symlink
 * was introduced in 2.8). Starting from async-profiler 3.0, the executable is a binary.
 * </p>
 *
 * <p>
 * <pre><code>
 * async-profiler-3.0-macos
 * ├── bin
 * │   └── asprof
 * ├── CHANGELOG.md
 * ├── lib
 * │   ├── async-profiler.jar
 * │   ├── converter.jar
 * │   └── libasyncProfiler.dylib
 * ├── LICENSE
 * └── README.md
 * </code></pre>
 *
 * <pre><code>
 * async-profiler-4.2-macos
 * ├── bin
 * │   ├── asprof
 * │   └── jfrconv
 * ├── include
 * │   └── asprof.h
 * ├── lib
 * │   └── libasyncProfiler.dylib
 * ├── LICENSE
 * └── README.md
 * </code></pre>
 * </p>
 */
public class AsyncProfilerDistribution {
    private final File executable;
    private final File library;
    private final Version version;

    AsyncProfilerDistribution(File home, String source) {
        // Handles async-profiler 3.0+
        File asprofBinary = new File(home, "bin/asprof");
        if (asprofBinary.isFile()) {
            executable = asprofBinary;
            String libExtension;
            // if async profiler 3+
            if (OperatingSystem.isLinuxX86()) {
                libExtension = ".so";
            } else if (OperatingSystem.isMacOS()) {
                libExtension = ".dylib";
            } else {
                throw new IllegalStateException("Unsupported operating system: " + OperatingSystem.getId());
            }

            library = new File(home, "lib/libasyncProfiler" + libExtension);
        } else {
            // Understand async-profiler 2.x, so we can at least report the version used
            executable = new File(home, "profiler.sh");
            library = new File(home, "build/libasyncProfiler.so");
        }

        String output = new CommandExec().runAndCollectOutput(
            Arrays.asList(executable.getAbsolutePath(), "--version")
        );
        Matcher matcher = Pattern.compile("Async-profiler ([\\d.]+).+").matcher(output);
        if (matcher.find()) {
            version = new Version(matcher.group(1).trim());
            if (version.major < 3) {
                throw new IllegalStateException("Async-profiler version " + version.stringVersion + " found at " + home + ", but version 3.0 or higher is required." + " (source: " + source + ")");
            }
        } else {
            throw new IllegalStateException("Unknown async-profiler distribution at: " + home + " (source: " + source + ")");
        }
    }

    public File getExecutable() {
        return executable;
    }

    public File getLibrary() {
        return library;
    }

    public Version getVersion() {
        return version;
    }

    /**
     * Async Profiler Version. Async profiler versions are usually in the form <code>major.minor</code>, but nothing
     * prevents to move to <code>major.minor.patch</code> or else. At this point, async-profiler had versions
     * <em>2.9</em>, <em>3.0</em>, <em>4.0</em>, <em>4.1</em>, so this code knows about major and minor only.
     */
    public static class Version implements Comparable<Version> {
        public final int major;
        public final int minor;
        public final String stringVersion;

        public Version(String stringVersion) {
            String[] parts = stringVersion.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid version: " + stringVersion);
            }
            try {
                major = Integer.parseInt(parts[0]);
                minor = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid version: " + stringVersion, e);
            }
            this.stringVersion = stringVersion;
        }


        @Override
        public int compareTo(Version other) {
            int majorDiff = Integer.compare(this.major, other.major);
            if (majorDiff != 0) {
                return majorDiff;
            }
            return Integer.compare(this.minor, other.minor);
        }
    }
}
