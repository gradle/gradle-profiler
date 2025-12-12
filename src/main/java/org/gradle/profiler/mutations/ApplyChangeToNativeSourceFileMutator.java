package org.gradle.profiler.mutations;

import org.apache.commons.io.FilenameUtils;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {

    // Both lists taken from https://gcc.gnu.org/onlinedocs/gcc/Overall-Options.html
    private static final List<String> nativeSourcecodeFileEndings = Arrays.asList(
        "c", "cc", "cp", "cxx", "cpp", "c++"
    );

    private static final List<String> nativeHeaderFileEndings = Arrays.asList(
        "h", "hh", "hp", "hxx", "hpp", "h++", "tcc"
    );

    public ApplyChangeToNativeSourceFileMutator(File file) {
        super(file, "native source file change");
        String fileExtension = getSourceFileExtension();
        boolean isSupportedExtension = nativeSourcecodeFileEndings.contains(fileExtension) || nativeHeaderFileEndings.contains(fileExtension);

        if (isSupportedExtension) {
            return;
        }
        throw new IllegalArgumentException("Can only modify C/C++ source or header files");
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos;
        if (nativeSourcecodeFileEndings.contains(getSourceFileExtension())) {
            insertPos = text.length();
            applyChangeAt(context, text, insertPos);
        } else {
            insertPos = text.lastIndexOf("#endif");
            if (insertPos < 0) {
                throw new IllegalArgumentException("Cannot parse header file " + sourceFile + " to apply changes");
            }
            applyHeaderChangeAt(context, text, insertPos);
        }
    }

    private String getSourceFileExtension() {
        return FilenameUtils.getExtension(sourceFile.getName()).toLowerCase(Locale.US);
    }

    protected String getFieldName(BuildContext context) {
        return "_m" + context.getUniqueBuildId();
    }

    private void applyChangeAt(BuildContext context, StringBuilder text, int insertPos) {
        // The code need to be confusing enough so the compiler and linker doesn't decide to simply inline it away.
        //   The compiler generally doesn't try to optimize away `volatile` variable.
        //   To make extra-sure, we return a value that can't be statically inferred from code analysis.
        text.insert(insertPos, "\nint " + getFieldName(context) + "(void) { volatile int dummy = 0; return (int)((unsigned long)&dummy & 0x7FFFFFFFul); }");
    }

    private void applyHeaderChangeAt(BuildContext context, StringBuilder text, int insertPos) {
        text.insert(insertPos, "int " + getFieldName(context) + "(void);\n");
    }
}
