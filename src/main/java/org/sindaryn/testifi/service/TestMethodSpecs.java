package org.sindaryn.testifi.service;


import com.squareup.javapoet.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.junit.Test;
import org.sindaryn.datafi.annotations.GetByUnique;
import org.sindaryn.datafi.annotations.WithResolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.sindaryn.apifi.StaticUtils.*;
import static org.sindaryn.datafi.StaticUtils.toPascalCase;
import static org.sindaryn.datafi.StaticUtils.toPlural;
import static org.sindaryn.testifi.StaticUtils.*;
@RequiredArgsConstructor
public class TestMethodSpecs {

    @NonNull
    private ProcessingEnvironment processingEnvironment;

    private static final Class<?> testLogic = TestLogic.class;
    
    public MethodSpec generateGetAllEndpointTest(TypeElement entity) {
        String testName = "all" + pluralPascalCaseName(entity) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getAllTest($T.class, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity)
                )
                .returns(void.class)
                .build();
    }

    /*public MethodSpec generateGetAllSortedByEndpointTest(TypeElement entity){
        String testName = "all" + toPlural(camelcaseNameOf(entity)) + "SortedByTest";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getAllTest($T.class, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity))
                .returns(void.class)
                .build();
    }*/

    public MethodSpec generateGetByIdEndpointTest(TypeElement entity) {
        String testName = "get" + pascalCaseNameOf(entity) + "ById" + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getByIdTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity),
                        reflectionCache)
                .returns(void.class)
                .build();
    }

    public MethodSpec generateGetByUniqueEndpointTest(TypeElement entity, VariableElement field) {
        String resolverName = "get" + pascalCaseNameOf(entity) + "ByUnique" + pascalCaseNameOf(field);
        String testName = resolverName + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getByUniqueTest($T.class, $L, $L, $S, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity),
                        field.getSimpleName(),
                        reflectionCache)
                .returns(void.class)
                .build();
    }

    public MethodSpec generateGetByEndpointTest(TypeElement entity, VariableElement field) {
        String resolverName = "get" + toPlural(pascalCaseNameOf(entity)) + "By" + pascalCaseNameOf(field);
        String testName = resolverName + "Test";
        checkForUniquenessConstraints(entity, resolverName, field.getSimpleName().toString().split(" "), processingEnvironment);
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getByTest($T.class, $L, $L, $S, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity),
                        field.getSimpleName(),
                        reflectionCache)
                .returns(void.class)
                .build();
    }

    public MethodSpec generateGetAllByEndpointTest(TypeElement entity, VariableElement field) {
        String resolverName = "getAll" + toPlural(pascalCaseNameOf(entity)) + "By" + toPlural(pascalCaseNameOf(field));
        String testName = resolverName + "Test";
        checkForUniquenessConstraints(entity, resolverName, field.getSimpleName().toString().split(" "), processingEnvironment);
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getAllByTest($T.class, $L, $L, $S, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity),
                        field.getSimpleName(),
                        reflectionCache)
                .returns(void.class)
                .build();
    }

    public MethodSpec generateCustomResolverEndpointTest(TypeElement entity, WithResolver withResolver) {

        String resolverName = withResolver.name();
        String testName = resolverName + "Test";
        MethodSpec.Builder builder = MethodSpec.methodBuilder(testName);
        checkForUniquenessConstraints(entity, withResolver.name(), withResolver.args(), processingEnvironment);
        argFieldNames(withResolver, builder);
        builder
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.selectByTest($T.class, $L, $L, $S, args, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity),
                        resolverName,
                        entityMocker(),
                        reflectionCache)
                .returns(void.class);
        return builder.build();
    }

    private void argFieldNames(WithResolver withResolver, MethodSpec.Builder builder) {
        String args = "\"" + String.join("\",\"", withResolver.args()) + "\"";
        TypeName listOfStrings = ParameterizedTypeName.get(List.class, String.class);
        CodeBlock.Builder block = CodeBlock.builder()
                .add("$T args = $T.asList($L)", listOfStrings, Arrays.class, args);
        builder.addStatement(block.build());
    }

    public MethodSpec generateArchiveEndpointTest(TypeElement entity) {
        String testName = "archive" + pascalCaseNameOf(entity) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.archiveTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        reflectionCache,
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateArchiveCollectionEndpointTest(TypeElement entity) {
        String testName = "archive" + toPlural(pascalCaseNameOf(entity)) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.archiveCollectionTest($T.class, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateDeArchiveCollectionEndpointTest(TypeElement entity) {
        String testName = "deArchive" + toPlural(pascalCaseNameOf(entity)) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.deArchiveCollectionTest($T.class, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateDeArchiveEndpointTest(TypeElement entity) {
        String testName = "deArchive" + pascalCaseNameOf(entity) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.deArchiveTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        reflectionCache,
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateAddEndpointTest(TypeElement entity) {
        String testName = "add" + pascalCaseNameOf(entity) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.addTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        entityMocker(),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateUpdateEndpointTest(TypeElement entity) {
        String testName = "update" + pascalCaseNameOf(entity) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.updateTest($T.class, $L, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        entityMocker(),
                        reflectionCache,
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateDeleteEndpointTest(TypeElement entity) {
        String testName = "delete" + pascalCaseNameOf(entity) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.deleteTest($T.class, $L, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        reflectionCache,
                        entityMocker(),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateGetCollectionByIdEndpointTest(TypeElement entity) {
        String testName = "get" + toPlural(pascalCaseNameOf(entity)) + "ByIdTest";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getCollectionByIdTest($T.class, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateAddCollectionEndpointTest(TypeElement entity) {
        String testName = "add" + toPlural(pascalCaseNameOf(entity)) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.addCollectionTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        entityMocker(),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateUpdateCollectionEndpointTest(TypeElement entity) {
        String testName = "update" + toPlural(pascalCaseNameOf(entity)) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.updateCollectionTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        entityMocker(),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateDeleteCollectionEndpointTest(TypeElement entity) {
        String testName = "delete" + toPlural(pascalCaseNameOf(entity)) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.deleteCollectionTest($T.class, $L, $L, $L)",
                        testLogic,
                        ClassName.get(entity),
                        dataManagerName(entity),
                        entityMocker(),
                        metaOpsName(entity))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateGetAsEmbeddedEntityTest(VariableElement embedded, TypeElement owner) {
        String testName = "get" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(owner) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getAsEmbeddedEntityTest($T.class, $T.class, $L, $L, $S, $L, $L)",
                        testLogic,
                        ClassName.get(embedded.asType()),
                        ClassName.get(owner),
                        dataManagerName(embedded),
                        dataManagerName(owner),
                        embedded.getSimpleName(),
                        entityMocker(),
                        reflectionCache)
                .returns(void.class)
                .build();
    }

    public MethodSpec generateGetAsEmbeddedEntityCollectionTest(VariableElement embedded, TypeElement owner) {
        String testName = "get" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(owner) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.getAsEmbeddedEntityCollectionTest($T.class, $T.class, $L, $L, $S, $L, $L)",
                        testLogic,
                        collectionTypeName(embedded),
                        ClassName.get(owner),
                        dataManagerName(embedded),
                        dataManagerName(owner),
                        embedded.getSimpleName(),
                        entityMocker(),
                        reflectionCache)
                .returns(void.class)
                .build();
    }

    public MethodSpec generateAttachExistingToEmbeddedCollectionTest(VariableElement embedded, TypeElement owner) {
        String testName =
                "attachExisting" + pascalCaseNameOf(embedded) + "To" + pascalCaseNameOf(owner) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.attachExistingToEmbeddedCollectionTest($T.class, $T.class, $L, $L, $S, $L, $L, $L)",
                        testLogic,
                        collectionTypeName(embedded),
                        ClassName.get(owner),
                        dataManagerName(embedded),
                        dataManagerName(owner),
                        embedded.getSimpleName(),
                        entityMocker(),
                        reflectionCache,
                        embeddedCollectionMetaOpsName(embedded))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateRemoveFromEmbeddedCollectionTest(VariableElement embedded, TypeElement owner) {
        String testName =
                "remove" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(owner) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.removeFromEmbeddedCollectionTest($T.class, $T.class, $L, $L, $S, $L, $L, $L)",
                        testLogic,
                        collectionTypeName(embedded),
                        ClassName.get(owner),
                        dataManagerName(embedded),
                        dataManagerName(owner),
                        embedded.getSimpleName(),
                        entityMocker(),
                        reflectionCache,
                        embeddedCollectionMetaOpsName(embedded))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateAddNewToEmbeddedCollectionTest(VariableElement embedded, TypeElement owner) {
        String testName =
                "addNew" + pascalCaseNameOf(embedded) + "To" + pascalCaseNameOf(owner) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.addNewToEmbeddedCollectionTest($T.class, $T.class, $L, $L, $S, $L, $L, $L)",
                        testLogic,
                        collectionTypeName(embedded),
                        ClassName.get(owner),
                        dataManagerName(embedded),
                        dataManagerName(owner),
                        embedded.getSimpleName(),
                        entityMocker(),
                        reflectionCache,
                        embeddedCollectionMetaOpsName(embedded))
                .returns(void.class)
                .build();
    }

    public MethodSpec generateUpdateEmbeddedCollectionTest(VariableElement embedded, TypeElement owner) {
        String testName =
                "update" + pascalCaseNameOf(embedded) + "In" + pascalCaseNameOf(owner) + "Test";
        return MethodSpec.methodBuilder(testName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Test.class)
                .addStatement("$T.updateEmbeddedCollectionTest($T.class, $T.class, $L, $L, $S, $L, $L, $L)",
                        testLogic,
                        collectionTypeName(embedded),
                        ClassName.get(owner),
                        dataManagerName(embedded),
                        dataManagerName(owner),
                        embedded.getSimpleName(),
                        entityMocker(),
                        reflectionCache,
                        embeddedCollectionMetaOpsName(embedded))
                .returns(void.class)
                .build();
    }
}
