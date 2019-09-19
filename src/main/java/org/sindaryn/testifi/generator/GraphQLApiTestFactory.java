package org.sindaryn.testifi.generator;

import com.squareup.javapoet.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.junit.runner.RunWith;
import org.sindaryn.apifi.annotations.GraphQLApiEntity;
import org.sindaryn.apifi.generator.EntitiesInfoCache;
import org.sindaryn.apifi.generator.FieldSpecs;
import org.sindaryn.datafi.annotations.GetAllBy;
import org.sindaryn.datafi.annotations.GetBy;
import org.sindaryn.datafi.annotations.GetByUnique;
import org.sindaryn.datafi.annotations.WithResolver;
import org.sindaryn.mockeri.generator.EntityMocker;
import org.sindaryn.testifi.service.TestMethodSpecs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.transaction.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.sindaryn.apifi.StaticUtils.getFields;
import static org.sindaryn.apifi.StaticUtils.isArchivable;
import static org.sindaryn.datafi.StaticUtils.writeToJavaFile;
import static org.sindaryn.testifi.StaticUtils.entityMocker;

@RequiredArgsConstructor
public class GraphQLApiTestFactory {
    @NonNull
    private ProcessingEnvironment processingEnvironment;
    @NonNull
    private EntitiesInfoCache entitiesInfoCache;
    private FieldSpecs fieldSpecs;
    private TestMethodSpecs testMethodSpecs;
    private Map<TypeName, FieldSpec> additionalDataManagers;

    protected void generateGraphQLServiceTest(TypeElement entity) {
        //we'll need these helpers
        fieldSpecs = new FieldSpecs(processingEnvironment, entitiesInfoCache);
        testMethodSpecs = new TestMethodSpecs(processingEnvironment);
        additionalDataManagers = new HashMap<>();
        //get package, class & file names for the graphql api bean to generate
        String className = entity.getQualifiedName().toString();
        int lastDot = className.lastIndexOf('.');
        String packageName = className.substring(0, lastDot);
        String simpleClassName = className.substring(lastDot + 1);
        String serviceName = simpleClassName + "GraphQLServiceTest";
        //lay out the skeletal structure..
        TypeSpec.Builder builder = TypeSpec
                .classBuilder(serviceName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(RunWith.class)
                        .addMember("value", "$T.class", SpringRunner.class)
                        .build())
                .addAnnotation(SpringBootTest.class)
                .addAnnotation(Transactional.class);
        //.. fetch the annotation
        GraphQLApiEntity apiEntityAnnotation = entity.getAnnotation(GraphQLApiEntity.class);


        //and begin!

        //if this
        if(apiEntityAnnotation.exposeDirectly()){
            builder
                    .addMethod(testMethodSpecs.generateGetAllEndpointTest(entity))
                    .addMethod(testMethodSpecs.generateFuzzySearchEndpointTest(entity))
                    .addMethod(testMethodSpecs.generateGetByIdEndpointTest(entity))
                    .addMethod(testMethodSpecs.generateGetCollectionByIdEndpointTest(entity));

            if(!apiEntityAnnotation.readOnly()){
                builder
                        .addMethod(testMethodSpecs.generateAddEndpointTest(entity))
                        .addMethod(testMethodSpecs.generateUpdateEndpointTest(entity))
                        .addMethod(testMethodSpecs.generateDeleteEndpointTest(entity))
                        .addMethod(testMethodSpecs.generateAddCollectionEndpointTest(entity))
                        .addMethod(testMethodSpecs.generateUpdateCollectionEndpointTest(entity))
                        .addMethod(testMethodSpecs.generateDeleteCollectionEndpointTest(entity));

                if(isArchivable(entity, processingEnvironment)){
                    builder.addMethod(testMethodSpecs.generateArchiveEndpointTest(entity))
                            .addMethod(testMethodSpecs.generateDeArchiveEndpointTest(entity))
                            .addMethod(testMethodSpecs.generateArchiveCollectionEndpointTest(entity))
                            .addMethod(testMethodSpecs.generateDeArchiveCollectionEndpointTest(entity));
                }
            }
        }
        builder
                .addField(fieldSpecs.metaOps(apiEntityAnnotation, entity))
                .addField(fieldSpecs.reflectionCache())
                .addField(fieldSpecs.dataManager(entity))
                .addField(FieldSpec.builder(EntityMocker.class, entityMocker(), Modifier.PRIVATE)
                        .addAnnotation(Autowired.class)
                        .build());

        for(VariableElement field : getFields(entity)){
            if(isForeignKeyOrKeys(field))
                addEmbeddedFieldResolvers(entity, field, builder);
        }
        addGetByAndGetAllByResolverTests(entity, builder);
        addCustomResolverTests(entity, builder);
        writeToJavaFile(simpleClassName, packageName, builder, processingEnvironment, "GraphQL Api Test Class");
    }

    private void addCustomResolverTests(TypeElement entity, TypeSpec.Builder builder) {
        WithResolver[] resolvers = entity.getAnnotationsByType(WithResolver.class);
        if(resolvers == null || resolvers.length <= 0) return;
        for(WithResolver resolver : resolvers){
            builder.addMethod(testMethodSpecs.generateCustomResolverEndpointTest(entity, resolver));
        }
    }

    private void addGetByAndGetAllByResolverTests(TypeElement entity, TypeSpec.Builder builder) {
        for(VariableElement field : getFields(entity)){
            if(field.getAnnotation(GetAllBy.class) != null)
                builder.addMethod(testMethodSpecs.generateGetAllByEndpointTest(entity, field));
            if(field.getAnnotation(GetBy.class) != null)
                builder.addMethod(testMethodSpecs.generateGetByEndpointTest(entity, field));
            else if(field.getAnnotation(GetByUnique.class) != null)
                builder.addMethod(testMethodSpecs.generateGetByUniqueEndpointTest(entity, field));
        }
    }

    private boolean isForeignKeyOrKeys(VariableElement field) {
        return
                field.getAnnotation(OneToMany.class) != null ||
                        field.getAnnotation(ManyToOne.class) != null ||
                        field.getAnnotation(OneToOne.class) != null ||
                        field.getAnnotation(ManyToMany.class) != null;
    }

    private void addEmbeddedFieldResolvers(TypeElement entity, VariableElement field, TypeSpec.Builder builder) {
        //if field is iterable
        if(field.getAnnotation(OneToMany.class) != null || field.getAnnotation(ManyToMany.class) != null){
            //get as embedded entity collection
            builder.addMethod(testMethodSpecs.generateGetAsEmbeddedEntityCollectionTest(field, entity));
            if(additionalDataManagers.get(ClassName.get(field.asType())) == null){
                var typeArgs = ((DeclaredType)field.asType()).getTypeArguments();
                addDataManager(field, builder, typeArgs);
            }
            //add to collection
            if(entitiesInfoCache.isStrongEntity(field)){
                builder.addMethod(testMethodSpecs.generateAttachExistingToEmbeddedCollectionTest(field, entity));
            }else {
                builder.addMethod(testMethodSpecs.generateAddNewToEmbeddedCollectionTest(field, entity));
            }
            //update in collection
            builder.addMethod(testMethodSpecs.generateUpdateEmbeddedCollectionTest(field, entity));
            //remove from collection
            builder.addMethod(testMethodSpecs.generateRemoveFromEmbeddedCollectionTest(field, entity));
            //inject autowired metaops for embedded entity collection type
            builder.addField(fieldSpecs.embeddedCollectionMetaOps(field));
        }else {
            if(additionalDataManagers.get(ClassName.get(field.asType())) == null){
                addDataManager(field, builder, Collections.singletonList(field.asType()));
            }
            builder.addMethod(testMethodSpecs.generateGetAsEmbeddedEntityTest(field, entity));
        }
    }

    private void addDataManager(VariableElement field, TypeSpec.Builder builder, List<? extends TypeMirror> typeArgs) {
        FieldSpec additionalDataManager = fieldSpecs.dataManager(typeArgs.iterator().next(), field.getSimpleName().toString());
        builder.addField(additionalDataManager);
        additionalDataManagers.put(ClassName.get(field.asType()), additionalDataManager);
    }
}