package org.gradle.profiler.mutations;

import com.google.common.collect.ImmutableSet;

import org.apache.commons.io.FilenameUtils;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.stream.Stream;

public class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {

    // Both lists taken from https://gcc.gnu.org/onlinedocs/gcc/Overall-Options.html
    private static final ImmutableSet<String> nativeSourcecodeFileEndings = ImmutableSet.of(
        "c", "cc", "cp", "cxx", "cpp", "CPP", "c++", "C"
    );

    private static final ImmutableSet<String> nativeHeaderFileEndings = ImmutableSet.of(
        "h", "hh", "H", "hp", "hxx", "hpp", "HPP", "h++", "tcc"
    );

    public ApplyChangeToNativeSourceFileMutator(File file) {
        super(file);
        String fileExtension = FilenameUtils.getExtension(sourceFile.getName());
        boolean isSupportedExtension = Stream.concat(nativeSourcecodeFileEndings.stream(), nativeHeaderFileEndings.stream())
            .anyMatch(fileExtension::equals);

        if (isSupportedExtension) {
            return;
        }
        throw new IllegalArgumentException("Can only modify C/C++ source or header files");
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos;
        String fileExtension = FilenameUtils.getExtension(sourceFile.getName());
        if (nativeSourcecodeFileEndings.contains(fileExtension)) {
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
