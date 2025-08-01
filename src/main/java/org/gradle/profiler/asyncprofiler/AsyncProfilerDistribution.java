package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.CommandExec;
import org.gradle.profiler.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
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
    private final String version;

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
            version = matcher.group(1).trim();
        } else {
            version = "unknown";
        }
    }

    public File getExecutable() {
        return executable;
    }

    public File getLibrary() {
        return library;
    }

    public String getVersion(File asyncProfilerHome) {
        return version;
    }
}
