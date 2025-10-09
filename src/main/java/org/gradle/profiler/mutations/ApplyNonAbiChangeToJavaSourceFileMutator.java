package org.gradle.profiler.mutations;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.List;

public class ApplyNonAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    public ApplyNonAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile, "non-ABI change");
    }

    @Override
    protected void applyChangeTo(BuildContext context, CompilationUnit compilationUnit) {
        MethodDeclaration existingMethod = getExistingMethod(compilationUnit);
        existingMethod.getBody()
                .orElseThrow(() -> new RuntimeException("Method body not found"))
                .addStatement(0, new JavaParser().parseStatement("System.out.println(\"" + context.getUniqueBuildId() + "\");").getResult().get());
    }

    private MethodDeclaration getExistingMethod(CompilationUnit compilationUnit) {
        NodeList<TypeDeclaration<?>> types = compilationUnit.getTypes();
        if (types.isEmpty()){
            throw new IllegalArgumentException("No types to change in " + sourceFile);
        }
        TypeDeclaration<?> type = types.get(0);
        List<MethodDeclaration> methods = type.getMethods();
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No methods to change in " + sourceFile);
        }
        return methods.get(0);
    }
}
