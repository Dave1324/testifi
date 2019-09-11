package org.sindaryn.testifi.generator;

import com.google.auto.service.AutoService;
import org.sindaryn.apifi.generator.EntitiesInfoCache;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

import static org.sindaryn.testifi.StaticUtils.getGraphQLApiEntities;


@SuppressWarnings("unchecked")
@SupportedAnnotationTypes({"org.sindaryn.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        Set<? extends TypeElement> entities = getGraphQLApiEntities(annotations, roundEnvironment);
        EntitiesInfoCache entitiesInfoCache = new EntitiesInfoCache(processingEnv);
        entitiesInfoCache.setTypeElementMap(entities);
        GraphQLApiTestFactory testFactory = new GraphQLApiTestFactory(processingEnv, entitiesInfoCache);
        entities.forEach(testFactory::generateGraphQLServiceTest);
        return false;
    }
}
