package org.gradle.profiler.asyncprofiler;

import org.gradle.profiler.CommandExec;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.profiler.asyncprofiler.AsyncProfilerCompatibility.MINIMUM_SUPPORTED_VERSION;

/**
 * Metainfo and layout of an Async Profiler installation.
 * Requires Async Profiler 3.0 or later.
 * <p>
 * Note, that Async-Profiler executable was as shell script up to version 2.x, also
 * the async profile library had the `.so` extension even on macOS, (a .dylib symlink
 * was introduced in 2.8). Starting from async-profiler 3.0, the executable is a binary.
 *
 * <pre><code>
 * async-profiler-3.0
 * ├── bin
 * │   └── asprof
 * ├── CHANGELOG.md
 * ├── lib
 * │   ├── async-profiler.jar
 * │   ├── converter.jar
 * │   └── libasyncProfiler.so (.dylib on macOS)
 * ├── LICENSE
 * └── README.md
 * </code></pre>
 *
 * <pre><code>
 * async-profiler-4.2
 * ├── bin
 * │   ├── asprof
 * │   └── jfrconv
 * ├── include
 * │   └── asprof.h
 * ├── lib
 * │   └── libasyncProfiler.so (.dylib on macOS)
 * ├── LICENSE
 * └── README.md
 * </code></pre>
 */
public class AsyncProfilerDistribution {

    private final File executable;
    private final File library;
    private final AsyncProfilerVersion version;

    private AsyncProfilerDistribution(File executable, File library, AsyncProfilerVersion version) {
        this.executable = executable;
        this.library = library;
        this.version = version;
    }

    public File getExecutable() {
        return executable;
    }

    public File getLibrary() {
        return library;
    }

    public AsyncProfilerVersion getVersion() {
        return version;
    }

    /**
     * Detect distribution layout on disk. Requires Async Profiler 3.0 or later.
     */
    public static AsyncProfilerDistribution of(AsyncProfilerPlatform platform, File home, String sourceDisplayName) {
        File executable;
        File library;
        // Handles async-profiler 3.0+
        File asprofBinary = new File(home, "bin/asprof");
        if (asprofBinary.isFile()) {
            executable = asprofBinary;
            library = new File(home, "lib/libasyncProfiler" + platform.getDynamicLibraryExtension());
        } else {
            // Understand async-profiler 2.x, so we can at least report the version used
            executable = new File(home, "profiler.sh");
            library = new File(home, "build/libasyncProfiler.so");
        }

        String printedVersion = new CommandExec().runAndCollectOutput(
            Arrays.asList(executable.getAbsolutePath(), "--version")
        );
        AsyncProfilerVersion version;
        Matcher matcher = Pattern.compile("Async-profiler ([\\d.]+).+").matcher(printedVersion);
        if (matcher.find()) {
            version = new AsyncProfilerVersion(matcher.group(1).trim());
        } else {
            throw new IllegalStateException("Unknown async-profiler distribution at: " + home + " (source: " + sourceDisplayName + ")");
        }

        if (version.compareTo(MINIMUM_SUPPORTED_VERSION) < 0) {
            throw new IllegalStateException(String.format(
                "Async-profiler version %s or higher is required, but got version %s located at %s (source: %s)",
                MINIMUM_SUPPORTED_VERSION.getAsString(), version.getAsString(), home, sourceDisplayName));
        }

        return new AsyncProfilerDistribution(executable, library, version);
    }
}
