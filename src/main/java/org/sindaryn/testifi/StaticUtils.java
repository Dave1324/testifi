package org.sindaryn.testifi;

import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.val;
import lombok.var;
import org.checkerframework.checker.units.qual.A;
import org.sindaryn.apifi.annotations.GraphQLApiEntity;
import org.sindaryn.datafi.annotations.FuzzySearchBy;
import org.sindaryn.datafi.annotations.FuzzySearchByFields;
import org.sindaryn.datafi.annotations.WithResolver;
import org.sindaryn.datafi.persistence.Archivable;
import org.sindaryn.datafi.reflection.CachedEntityField;
import org.sindaryn.datafi.reflection.CachedEntityType;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.ArchivableDataManager;
import org.sindaryn.datafi.service.BaseDataManager;
import org.sindaryn.datafi.service.DataManager;
import org.sindaryn.mockeri.generator.EntityMocker;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Id;
import javax.tools.Diagnostic;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;


import static org.sindaryn.datafi.StaticUtils.*;
import static org.sindaryn.datafi.reflection.ReflectionCache.getClassFields;
import static org.sindaryn.mockeri.generator.TestDataGenerator.randomFrom;
@SuppressWarnings("unchecked")
public abstract class StaticUtils {
    public static String pluralPascalCaseName(Element entity) {
        return toPlural(toPascalCase(entity.getSimpleName().toString()));
    }
    public static String pluralCamelCaseName(Class<?> clazz) {
        return toPlural(toPascalCase(clazz.getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    public static <T> T randomInstance(Class<?> clazz, BaseDataManager<T> dataManager) {
        return randomFrom(dataManager.findAll((Class<T>) clazz));
    }

    public static void setField(Object entity, Object value, String fieldName){
        try {
            fieldName = toCamelCase(fieldName);
            for (Field field : getClassFields(entity.getClass())){
                field.setAccessible(true);
                if(field.getName().equals(fieldName)){
                    try {
                        field.setAccessible(true);
                        field.set(entity, value);
                    }catch (IllegalAccessException e){
                        throw new RuntimeException(e);
                    }
                    return;
                }
            }
            throw new RuntimeException("cannot find field '" + fieldName + "' in " + entity.getClass().getSimpleName());
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    public static void setCollectionField(Object entity, Collection value, String fieldName, EntityMocker entityMocker){
        final Class<?> collectableType = value.iterator().next().getClass();
        Class<? extends Collection> collectionType = collectionType(entity.getClass(), fieldName, collectableType);
        Collection<Object> collectionOfCorrectType =
                entityMocker.getCollectionInstantiator()
                .instantiateCollection(collectionType, collectableType);
        collectionOfCorrectType.addAll(value);
        setField(entity, collectionOfCorrectType, fieldName);
    }
    private static Map<String, Field> fieldNamesCache = new HashMap<>();
    public static Class<? extends Collection> collectionType(Class<?> hasTsClazz, String fieldName, Class<?> tClazz) {
        Field field = null;
        final String key = hasTsClazz.getSimpleName() + "_" + fieldName;
        if(fieldNamesCache.get(key) != null) field = fieldNamesCache.get(key);
        else {
            for(Field aField : getClassFields(hasTsClazz)) {
                if(aField.getName().equals(fieldName)){
                    field = aField;
                    break;
                }
            }
            if(field == null)
                throw new RuntimeException("field by name of " + fieldName + " not found in entity " + hasTsClazz.getSimpleName());
        }
        fieldNamesCache.put(key, field);
        return (Class<? extends Collection>) field.getType();
    }

    @SuppressWarnings("unchecked")
    public static <T> int totalCount(Class<?> clazz, BaseDataManager<T> dataManager) {
        return Math.toIntExact(dataManager.count((Class<T>) clazz));
    }
    public static int randomCount(Class<?> clazz, BaseDataManager dataManager) {
        return ThreadLocalRandom.current().nextInt(1, totalCount(clazz, dataManager));
    }
    @SuppressWarnings("unchecked")
    public static <T> List<T> firstRandomN(Class<?> clazz, BaseDataManager<T> dataManager) {
        return dataManager
                .findAll((Class<T>) clazz)
                .stream().limit(randomCount(clazz, dataManager)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static <T, HasTs> Collection<T> firstRandomEmbeddedN(HasTs owner, String fieldName, ReflectionCache reflectionCache) {
        var collection =
                (Collection<T>)reflectionCache
                .getEntitiesCache()
                .get(owner.getClass().getSimpleName())
                .invokeGetter(owner, fieldName);
        long limit = ThreadLocalRandom.current().nextLong(1, collection.size());
        return collection.stream().limit(limit).collect(Collectors.toList());
    }

    //@SuppressWarnings("unchecked")
    public static <T> List<T> persistCollectionOf(Class<?> tClazz, EntityMocker entityMocker) {
        List<T> toAdd = new ArrayList<>();
        int amount = ThreadLocalRandom.current().nextInt(5, 10);
        for (int i = 0; i < amount; i++)
            toAdd.add(entityMocker.instantiateEntity(tClazz));
        return toAdd;
    }

    public static <T> List<T> transientlyInstantiateCollectionOf(Class<?> tClazz, EntityMocker entityMocker) {
        List<T> toAdd = new ArrayList<>();
        int amount = ThreadLocalRandom.current().nextInt(5, 10);
        for (int i = 0; i < amount; i++)
            toAdd.add(entityMocker.instantiateTransientEntity(tClazz));
        return toAdd;
    }

    @SuppressWarnings("unchecked")
    public static Set<? extends TypeElement> getGraphQLApiEntities(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        Set<TypeElement> entities = new HashSet<>();
        for (TypeElement annotationType : annotations) {
            if (annotationType.getQualifiedName().toString().equals(GraphQLApiEntity.class.getCanonicalName())) {
                entities.addAll((Collection<? extends TypeElement>) roundEnvironment.getElementsAnnotatedWith(annotationType));
            }
        }
        return Sets.newHashSet(entities);
    }

    public static String collectionTypeString(VariableElement embedded) {
        String typeNameString = embedded.asType().toString();
        typeNameString = typeNameString.replaceAll("^.+<", "");
        typeNameString = typeNameString.replaceAll(">", "");
        int lastDot = typeNameString.lastIndexOf(46);
        String packageName = typeNameString.substring(0, lastDot);
        String simpleClassName = typeNameString.substring(lastDot + 1);
        return simpleClassName;
    }


    public static void checkForUniquenessConstraints(TypeElement entity, String resolverName, String[] args, ProcessingEnvironment processingEnvironment) {
        List<String> uniqueArgTypes = new ArrayList<>();
        Map<String, VariableElement> fieldsMap = getFieldsOfTypeElement(entity);
        for(String arg : args){
            VariableElement correspondingField = fieldsMap.get(arg);
            Column columnAnnotation = correspondingField.getAnnotation(Column.class);
            Id idAnnotation = correspondingField.getAnnotation(Id.class);
            EmbeddedId embeddedIdAnnotation = correspondingField.getAnnotation(EmbeddedId.class);
            if((columnAnnotation != null && columnAnnotation.unique()) ||
                idAnnotation != null || embeddedIdAnnotation != null){
                   uniqueArgTypes.add(arg);
            }
        }
        if(!uniqueArgTypes.isEmpty()){
            processingEnvironment.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                            "Error generating test for custom resolver \"" + resolverName + "\": " +
                                    "The arguments " + uniqueArgTypes.toString() + " are bound by uniqueness constraints, " +
                                    "whereas the return type is java.util.List");
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, VariableElement> getFieldsOfTypeElement(TypeElement entity) {
        return (Map<String, VariableElement>)
        entity.getEnclosedElements().stream()
        .filter(element -> element.getKind().isField())
        .collect(Collectors.toMap(field -> field.getSimpleName().toString(), field -> field));
    }

    public static String entityMocker(){
        return "entityMocker";
    }

    public static <T> List<Object> fieldValues(String fieldName, List<T> collection, CachedEntityType entityType) {
        List<Object> result = new ArrayList<>();
        for(T item : collection) result.add(entityType.invokeGetter(item, fieldName));
        return result;
    }

    public static <T> Map<Object, T> firstRandomNIdMap(Class<?> clazz, BaseDataManager<T> dataManager, ReflectionCache reflectionCache) {
        return firstRandomN(clazz, dataManager).stream().collect(
                Collectors.toMap(instance -> getId(instance, reflectionCache), instance -> instance));
    }

    public static Field resolveFieldToFuzzySearchBy(Class<?> clazz, ReflectionCache reflectionCache) {
        CachedEntityType entityType = reflectionCache
                .getEntitiesCache()
                .get(clazz.getSimpleName());

        List<String> classLevelFuzzySearchByFields = new ArrayList<>();
        if(clazz.isAnnotationPresent(FuzzySearchByFields.class))
            classLevelFuzzySearchByFields =
                    new ArrayList<>(Arrays.asList(clazz.getAnnotation(FuzzySearchByFields.class).fields()));

        List<String> finalClassLevelFuzzySearchByFields = classLevelFuzzySearchByFields;
        List<Field> fuzzySearchableFields =
                entityType
                .getFields()
                .values()
                .stream()
                .filter(
                        field -> field.getField().isAnnotationPresent(FuzzySearchBy.class) ||
                        finalClassLevelFuzzySearchByFields.contains(field.getField().getName())
                )
                .map(CachedEntityField::getField)
                .collect(Collectors.toList());

        return fuzzySearchableFields.get(
                ThreadLocalRandom.current().nextInt(0, fuzzySearchableFields.size() - 1)
        );
    }
}
