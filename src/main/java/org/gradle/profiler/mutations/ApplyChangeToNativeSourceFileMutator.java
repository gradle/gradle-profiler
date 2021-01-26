package org.gradle.profiler.mutations;

import org.apache.commons.io.FilenameUtils;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {

    // Both lists taken from https://gcc.gnu.org/onlinedocs/gcc/Overall-Options.html
    private final List<String> nativeSourcecodeFileEndings = Arrays.asList(
        ".c", ".cc", ".cp", ".cxx", ".cpp", ".CPP", ".c++", ".C"
    );

    private final List<String> nativeHeaderFileEndings = Arrays.asList(
        ".h", ".hh", ".H", ".hp", ".hxx", ".hpp", ".HPP", ".h++", ".tcc"
    );

    public ApplyChangeToNativeSourceFileMutator(File file) {
        super(file);
        String fileExtension = "." + FilenameUtils.getExtension(sourceFile.getName());
        List<String> extensionList = Stream.concat(nativeSourcecodeFileEndings.stream(), nativeHeaderFileEndings.stream())
            .collect(Collectors.toList());
        if (extensionList.contains(fileExtension)) {
            return;
        }

        throw new IllegalArgumentException("Can only modify C/C++ source or header files");
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos;
        String fileExtension = "." + FilenameUtils.getExtension(sourceFile.getName());
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
