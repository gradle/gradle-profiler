package org.gradle.profiler.mutations;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FilenameUtils;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.Locale;

public class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {

    // Both lists taken from https://gcc.gnu.org/onlinedocs/gcc/Overall-Options.html
    private static final ImmutableSet<String> nativeSourcecodeFileEndings = ImmutableSet.of(
        "c", "cc", "cp", "cxx", "cpp", "c++"
    );

    private static final ImmutableSet<String> nativeHeaderFileEndings = ImmutableSet.of(
        "h", "hh", "hp", "hxx", "hpp", "h++", "tcc"
    );

    public ApplyChangeToNativeSourceFileMutator(File file) {
        super(file);
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
        text.insert(insertPos, "\nint " + getFieldName(context) + " () { }");
    }

    private void applyHeaderChangeAt(BuildContext context, StringBuilder text, int insertPos) {
        text.insert(insertPos, "int " + getFieldName(context) + "();\n");
    }
}
