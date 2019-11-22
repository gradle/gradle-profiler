package org.gradle.profiler.mutations;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.gradle.profiler.BuildContext;

import java.io.File;

public abstract class AbstractJavaSourceFileMutator extends AbstractFileChangeMutator {
    public AbstractJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
        if (!sourceFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("Can only modify Java source files");
        }
    }

    @Override
    protected void applyChangeTo(BuildContext context, StringBuilder text) {
        CompilationUnit compilationUnit = JavaParser.parse(text.toString());
        applyChangeTo(context, compilationUnit);
        text.replace(0, text.length(), compilationUnit.toString());
    }

    protected abstract void applyChangeTo(BuildContext context, CompilationUnit compilationUnit);
}
