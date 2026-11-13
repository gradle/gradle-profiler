package org.gradle.profiler.perfetto.jfr;

import java.io.File;

/**
 * Command-line entry point for converting a single JFR recording into a Perfetto trace.
 *
 * <p>Handles argument validation and delegates the conversion itself to {@link JfrToPerfettoConverter}.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        if (args.length == 0 || args.length > 2) {
            System.err.println("Usage: Main <input_file> [output_file]");
            System.err.println("  <input_file>: Path to the .jfr recording to convert");
            System.err.println("  [output_file]: Optional path for the .perfetto output (defaults to the same location as the input file)");
            return 1;
        }

        File inputFile = new File(args[0]);
        if (!inputFile.isFile()) {
            System.err.println("File not found: " + inputFile.getAbsolutePath());
            return 1;
        }
        if (!inputFile.getName().endsWith(".jfr")) {
            System.err.println("Expected a .jfr input file: " + inputFile.getAbsolutePath());
            return 1;
        }

        File outputFile = args.length == 2 ? new File(args[1]) : defaultOutputFile(inputFile);
        JfrToPerfettoConverter.convert(inputFile, outputFile);
        System.out.println("Written trace to " + outputFile.getAbsolutePath());
        return 0;
    }

    private static File defaultOutputFile(File inputFile) {
        return new File(inputFile.getParentFile(), inputFile.getName().replaceFirst("\\.jfr$", ".perfetto"));
    }
}
