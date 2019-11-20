package org.gradle.profiler.mutations;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.gradle.profiler.BuildContext;

import java.io.File;
import java.util.List;

public class ApplyAbiChangeToJavaSourceFileMutator extends AbstractJavaSourceFileMutator {
    public ApplyAbiChangeToJavaSourceFileMutator(File sourceFile) {
        super(sourceFile);
    }

    @Override
    protected void applyChangeTo(BuildContext context, CompilationUnit compilationUnit) {
        NodeList<TypeDeclaration<?>> types = compilationUnit.getTypes();
        if (types.isEmpty()) {
            throw new IllegalArgumentException("No types to change in " + sourceFile);
        }
        TypeDeclaration<?> type = types.get(0);
        List<MethodDeclaration> methods = type.getMethods();
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No methods to change in " + sourceFile);
        }

        String newMethodName = "_m" + context.getUniqueBuildId();
        MethodDeclaration existingMethod = methods.get(0);
        existingMethod.getBody().get().addStatement(0, new MethodCallExpr(null, newMethodName));

        type.addMethod(newMethodName, Modifier.PUBLIC, Modifier.STATIC);
    }
}
