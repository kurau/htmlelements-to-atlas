package io.kurau.atlas.utils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Optional;

public class PsiUtils {

    public static PsiAnnotation createAnnotation(String annotationText, PsiElement context) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
        return factory.createAnnotationFromText(annotationText, context);
    }

    public static void optimizeImports(final PsiJavaFile file) {
        JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences(file);
        JavaCodeStyleManager.getInstance(file.getProject()).removeRedundantImports(file);
    }

    public static void addImport(final PsiFile file, final String qualifiedName) {
        if (file instanceof PsiJavaFile) {
            addImport((PsiJavaFile) file, qualifiedName);
        }
    }

    public static void addImport(final PsiJavaFile file, final String qualifiedName) {
        final Project project = file.getProject();
        Optional<PsiClass> possibleClass = Optional.ofNullable(JavaPsiFacade.getInstance(project)
                .findClass(qualifiedName, GlobalSearchScope.everythingScope(project)));
        possibleClass.ifPresent(psiClass -> JavaCodeStyleManager.getInstance(project).addImport(file, psiClass));
    }

}
