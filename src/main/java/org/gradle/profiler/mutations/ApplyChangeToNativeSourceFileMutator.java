package org.gradle.profiler.mutations;

import org.gradle.profiler.BuildContext;

import java.io.File;

public class ApplyChangeToNativeSourceFileMutator extends AbstractFileChangeMutator {

    public ApplyChangeToNativeSourceFileMutator(File file) {
        super(file);
        if (!sourceFile.getName().endsWith(".cpp") && !sourceFile.getName().endsWith(".h") && !sourceFile.getName().endsWith(".hpp")) {
            throw new IllegalArgumentException("Can only modify C++ source or header files");
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        int insertPos;
        if (sourceFile.getName().endsWith(".cpp")) {
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
