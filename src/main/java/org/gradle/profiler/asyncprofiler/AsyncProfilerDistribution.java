package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model an async-profiler distribution.
 * <p>
 * Async-Profiler executable was as shell script up to version 2.x, also
 * the async profile library had the `.so` extension even on macOS, (a .dyli symlink
 * was introduced in 2.8). TODO check that <===
 * </p>
 * <p>
 * Starting from async-profiler 3.0, the executable is a binary.
 * </p>
 *
 * <p>
 * Here's the different layouts: TODO add linux example
 * <pre><code>
 * async-profiler-2.9-macos
 * ├── build
 * │   ├── async-profiler.jar
 * │   ├── converter.jar
 * │   ├── jattach
 * │   ├── libasyncProfiler.dylib -> libasyncProfiler.so
 * │   └── libasyncProfiler.so
 * ├── CHANGELOG.md
 * ├── LICENSE
 * ├── profiler.sh
 * └── README.md
 * </code></pre>
 *
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
 *
 * </code></pre>
 *
 * <pre><code>
 * async-profiler-4.1-macos
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
    private final String source;
    private final File executable;
    private final File library;
    private final Version version;

    AsyncProfilerDistribution(File home, String source) {
        this.source = source;

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
            // Fallback to async-profiler 2.x
            executable = new File(home, "profiler.sh");
            library = new File(home, "build/libasyncProfiler.so");
        }
        System.out.println(asprofBinary.getAbsolutePath());
        if (!executable.isFile() // || !Files.isExecutable(executable.toPath())
            || !library.isFile() // || !Files.isExecutable(library.toPath())
        ) {
            throw new IllegalStateException("Invalid async-profiler distribution at: " + home);
        }

        try {
            executable.setExecutable(true);
            library.setExecutable(true);
        } catch (Exception e) {
            throw new IllegalStateException("Could not set executable permissions");
        }

        String output = new CommandExec().runAndCollectOutput(
            Arrays.asList(executable.getAbsolutePath(), "--version")
        );
        Matcher matcher = Pattern.compile("Async-profiler ([\\d.]+).+").matcher(output);
        if (matcher.find()) {
            version = new Version(matcher.group(1).trim());
        } else {
            version = Version.UNKNOWN;
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
        public static final Version UNKNOWN = new Version("unknown") {
            @Override
            public int compareTo(Version other) {
                return 0;
            }
        };
        public static final Version AP_3_0 = new Version("4.0");
        public static final Version AP_4_0 = new Version("4.0");
        public static final Version AP_4_1 = new Version("4.1");

        public final int major;
        public final int minor;
        public final String stringVersion;

        public Version(String stringVersion) {
            if (stringVersion.equals("unknown")) {
                this.major = -1;
                this.minor = -1;
                this.stringVersion = stringVersion;
                return;
            }
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
            if (this == UNKNOWN || other == UNKNOWN) {
                // Make UNKNOWN always above to parseable versions
                return this == other ? 0 : (this == UNKNOWN ? 1 : -1);
            }
            int majorDiff = Integer.compare(this.major, other.major);
            if (majorDiff != 0) {
                return majorDiff;
            }
            return Integer.compare(this.minor, other.minor);
        }
    }
}
