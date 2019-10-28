package io.kurau.atlas;

import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import io.kurau.atlas.utils.PsiUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class DescriptionMigrationAction extends AnAction {

    private static final String DESCRIPTION_HTMLELEMENTS_ANNOTATION = "io.qameta.htmlelements.annotation.Description";
    private static final String NAME_ATLAS_ANNOTATION = "io.qameta.atlas.webdriver.extension.Name";

    private static final String FIND_BY_HTMLELEMENTS_ANNOTATION = "io.qameta.htmlelements.annotation.FindBy";
    private static final String FIND_BY_ATLAS_ANNOTATION = "io.qameta.atlas.webdriver.extension.FindBy";

    private static final String HTML_ELEMENT = "io.qameta.htmlelements.element.HtmlElement";
    private static final String ATLAS_WEB_ELEMENT = "io.qameta.atlas.webdriver.AtlasWebElement";

    private static final String HTMLELEMENTS_PARAM = "io.qameta.htmlelements.annotation.Param";
    private static final String ATLAS_PARAM = "io.qameta.atlas.webdriver.extension.Param";

    private static final String ATLAS_WEB_PAGE = "io.qameta.atlas.webdriver.WebPage";

    private static final String HTMLELEMENTS_EXTENDED_LIST = "io.qameta.htmlelements.element.ExtendedList";
    private static final String ATLAS_ELEMENTS_COLLECTION = "io.qameta.atlas.webdriver.ElementsCollection";

    private Project project;
    private String currentDir;

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        final PsiElement element = event.getData(PlatformDataKeys.PSI_ELEMENT);
        project = element.getProject();

        Object nav = event.getData(CommonDataKeys.NAVIGATABLE);
        if (nav instanceof PsiDirectory) {
            currentDir =  ((PsiDirectory) nav).getVirtualFile().getPath();
        } else {
            return;
        }

        Processor<PsiClass> processor = psiClass -> {
            migrate(psiClass);
            return true;
        };

        AllClassesGetter.processJavaClasses(
                new PlainPrefixMatcher(""),
                project,
                GlobalSearchScope.projectScope(project),
                processor
        );
    }

    private void migrate(PsiClass element) {

        if (!element.getContainingFile().getVirtualFile().getPath().contains(currentDir)) {
            return;
        }

        Arrays.stream(element.getMethods())
                .filter(m -> m.hasAnnotation(DESCRIPTION_HTMLELEMENTS_ANNOTATION))
                .forEach(this::migrateDescriptionAnnotation);

        Arrays.stream(element.getMethods())
                .filter(m -> m.hasAnnotation(FIND_BY_HTMLELEMENTS_ANNOTATION))
                .forEach(this::migrateFindByAnnotation);

        Arrays.stream(element.getMethods())
                .forEach(this::migrateHtmlElement);

        Arrays.stream(element.getMethods())
                .forEach(this::migrateExtendsList);

        Arrays.stream(element.getMethods())
                .forEach(this::migrateParamAnnotation);

        migrateWebPage(element);

    }

    private void migrateDescriptionAnnotation(final PsiMethod pageObjectMethod) {
        Optional.ofNullable(pageObjectMethod.getAnnotation(DESCRIPTION_HTMLELEMENTS_ANNOTATION))
                .ifPresent(oldDescription -> {

                    PsiAnnotation annotation = createAnnotation(oldDescription, NAME_ATLAS_ANNOTATION);

                    write("Migrate Description Annotation", () -> {
                        PsiUtils.addImport(pageObjectMethod.getContainingFile(), NAME_ATLAS_ANNOTATION);

                        pageObjectMethod.getModifierList().addAfter(annotation, oldDescription);
                        oldDescription.delete();

                        PsiUtils.optimizeImports((PsiJavaFile) pageObjectMethod.getContainingFile());
                    });
                });
    }

    private void migrateFindByAnnotation(final PsiMethod pageObjectMethod) {
        Optional.ofNullable(pageObjectMethod.getAnnotation(FIND_BY_HTMLELEMENTS_ANNOTATION))
                .ifPresent(oldDescription -> {

                    PsiAnnotation annotation = createAnnotation(oldDescription, FIND_BY_ATLAS_ANNOTATION);

                    write("Migrate FindBy Annotation", () -> {
                        PsiUtils.addImport(pageObjectMethod.getContainingFile(), FIND_BY_ATLAS_ANNOTATION);

                        pageObjectMethod.getModifierList().addAfter(annotation, oldDescription);
                        oldDescription.delete();

                        PsiUtils.optimizeImports((PsiJavaFile) pageObjectMethod.getContainingFile());
                    });
                });
    }

    private void migrateHtmlElement(final PsiMethod pageObjectMethod) {
        if (pageObjectMethod.getReturnType() == null) {
            return;
        }
        if (pageObjectMethod.getReturnType().getCanonicalText().equals(HTML_ELEMENT)) {

            PsiTypeElement psiReturnStatement = elementFactory()
                    .createTypeElementFromText(ATLAS_WEB_ELEMENT, pageObjectMethod);

            write("Migrate HtmlElement Element Type", () -> {
                PsiTypeElement refs = pageObjectMethod.getReturnTypeElement();
                Objects.requireNonNull(refs).replace(psiReturnStatement);
            });
        }

        Stream.of(pageObjectMethod.getParameterList().getParameters())
                .filter(param -> param.getType().getCanonicalText().equals(HTML_ELEMENT))
                .forEach(param -> {

                    PsiTypeElement newParamType = elementFactory()
                            .createTypeElementFromText(ATLAS_WEB_ELEMENT, pageObjectMethod);

                    write("Migrate HtmlElement Method Param", () -> {
                        PsiUtils.addImport(pageObjectMethod.getContainingFile(), ATLAS_WEB_ELEMENT);

                        param.getTypeElement().replace(newParamType);
                        PsiUtils.optimizeImports((PsiJavaFile) pageObjectMethod.getContainingFile());
                    });
                });
    }

    private void migrateExtendsList(final PsiMethod pageObjectMethod) {
        if (pageObjectMethod.getReturnType()==null) {
            return;
        }
        if (pageObjectMethod.getReturnType().getCanonicalText()
                .contains(HTMLELEMENTS_EXTENDED_LIST)) {

            PsiElementFactory factory = elementFactory();
            PsiTypeElement newReturnType = factory.createTypeElementFromText(String
                    .format("%s<%s>", ATLAS_ELEMENTS_COLLECTION, ATLAS_WEB_ELEMENT), pageObjectMethod);

            write("Migrate ExtendedList<HtmlElement> Method Return Type", () -> {
                PsiTypeElement refs = pageObjectMethod.getReturnTypeElement();
                Objects.requireNonNull(refs).replace(newReturnType);
            });
        }

        Stream.of(pageObjectMethod.getParameterList().getParameters())
                .filter(this::isExtendedList)
                .forEach(param -> {

                    PsiElementFactory factory = elementFactory();
                    PsiTypeElement newParamType = factory.createTypeElementFromText(String
                            .format("%s<%s>", ATLAS_ELEMENTS_COLLECTION, ATLAS_WEB_ELEMENT), pageObjectMethod);

                    write("Migrate ExtendedList<HtmlElement> Method Param", () -> {
                        PsiUtils.addImport(pageObjectMethod.getContainingFile(), ATLAS_WEB_ELEMENT);
                        PsiUtils.addImport(pageObjectMethod.getContainingFile(), ATLAS_ELEMENTS_COLLECTION);

                        param.getTypeElement().replace(newParamType);
                        PsiUtils.optimizeImports((PsiJavaFile) pageObjectMethod.getContainingFile());
                    });
                });
    }

    private void migrateParamAnnotation(final PsiMethod pageObjectMethod) {
        Stream.of(pageObjectMethod.getParameterList().getParameters()).forEach(psiParameter ->
                Optional.ofNullable(psiParameter.getAnnotation(HTMLELEMENTS_PARAM)).ifPresent(annotation -> {

                    PsiAnnotation ann = createAnnotation(annotation, ATLAS_PARAM);

                    write("Migrate Param Annotation", () -> {
                        PsiUtils.addImport(pageObjectMethod.getContainingFile(), ATLAS_PARAM);

                        Objects.requireNonNull(annotation).replace(ann);

                        PsiUtils.optimizeImports((PsiJavaFile) pageObjectMethod.getContainingFile());
                    });
                }));
    }

    private void migrateWebPage(final PsiClass javaClass) {
        Stream.of(javaClass.getExtendsList().getReferenceElements())
                .filter(el -> el.getReferenceName().equals("WebPage"))
                .forEach(el -> {

                    PsiElementFactory factory = elementFactory();
                    PsiClassType psiClass = factory.createTypeByFQClassName(ATLAS_WEB_PAGE);
                    PsiJavaCodeReferenceElement referenceElement1 = factory.createReferenceElementByType(psiClass);

                    write("Migrate WebPage", () -> {
                        PsiUtils.addImport(javaClass.getContainingFile(), ATLAS_WEB_PAGE);
                        el.replace(referenceElement1);
                        PsiUtils.optimizeImports((PsiJavaFile) javaClass.getContainingFile());
                    });
                });

        Stream.of(javaClass.getExtendsList().getReferenceElements())
                .filter(el -> el.getReferenceName().equals("HtmlElement"))
                .forEach(el -> {

                    PsiElementFactory factory = elementFactory();
                    PsiClassType psiClass = factory.createTypeByFQClassName(ATLAS_WEB_ELEMENT);
                    PsiJavaCodeReferenceElement atlasElement = factory.createReferenceElementByType(psiClass);

                    write("Migrate HtmlElement Interface Type", () -> {
                        PsiUtils.addImport(javaClass.getContainingFile(), ATLAS_WEB_ELEMENT);

                        el.replace(atlasElement);
                        PsiUtils.optimizeImports((PsiJavaFile) javaClass.getContainingFile());
                    });
                });
    }

    private void write(String comment, Runnable action) {
        CommandProcessor.getInstance().executeCommand(project, () ->
                ApplicationManager.getApplication().runWriteAction(action), comment, null);
    }

    private PsiAnnotation createAnnotation(PsiAnnotation old, String newFromText) {
        String paramValue = old.findAttributeValue("value").getText();
        String annotationText = String.format("@%s(%s)", newFromText, paramValue);
        return PsiUtils.createAnnotation(annotationText, old);
    }

    private boolean isExtendedList(PsiParameter parametr) {
        return parametr.getType().getCanonicalText().contains(HTMLELEMENTS_EXTENDED_LIST);
    }

    private PsiElementFactory elementFactory() {
        return JavaPsiFacade.getInstance(project).getElementFactory();
    }
}
