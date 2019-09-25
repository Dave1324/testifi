package org.sindaryn.testifi.service;

import com.google.common.collect.Lists;
import com.maximeroussy.invitrode.WordGenerator;
import org.sindaryn.apifi.service.ApiLogic;
import org.sindaryn.apifi.service.ApiMetaOperations;
import org.sindaryn.apifi.service.EmbeddedCollectionMetaOperations;
import org.sindaryn.datafi.persistence.Archivable;
import org.sindaryn.datafi.reflection.CachedEntityType;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.ArchivableDataManager;
import org.sindaryn.datafi.service.BaseDataManager;
import org.sindaryn.datafi.service.DataManager;
import org.sindaryn.mockeri.generator.EntityMocker;
import org.sindaryn.mockeri.generator.TestDataGenerator;
import org.sindaryn.testifi.StaticUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.hibernate.internal.util.collections.ArrayHelper.toList;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThat;
import static org.sindaryn.datafi.StaticUtils.*;
import static org.sindaryn.datafi.reflection.ReflectionCache.getClassFields;
import static org.sindaryn.mockeri.StaticUtils.isId;

import static org.sindaryn.mockeri.generator.TestDataGenerator.randomFrom;
import static org.sindaryn.testifi.StaticUtils.*;
import static org.sindaryn.testifi.service.EquivalencyMatcher.isEqualTo;

@SuppressWarnings("unchecked")
public interface TestLogic {

    //test methods
    static <T, E extends ApiMetaOperations<T>> void
    getAllTest(Class<?> clazz, BaseDataManager<T> dataManager, ReflectionCache reflectionCache, E metaOps){
        int offset = 0;
        int limit = totalCount(clazz, dataManager);
        Collection<T> allTs = dataManager.findAll();
        Collection<T> allApiFetchedTs = ApiLogic
                .getAll(clazz, dataManager, reflectionCache, metaOps, offset, limit, null, null);
        assertThat(
            "result of api call to 'getAll" + pluralCamelCaseName(clazz) + 
                   "()' " + " equals original entries in database",
                allTs,
                isEqualTo(allApiFetchedTs));
    }

    static <T, E extends ApiMetaOperations<T>> void
    fuzzySearchTest(
            Class<?> clazz, BaseDataManager<T> dataManager, ReflectionCache reflectionCache, E metaOps){
        int offset = 0;
        int limit = totalCount(clazz, dataManager);
        Collection<T> allTs = dataManager.findAll();
        Field toSearchBy = resolveFieldToFuzzySearchBy(clazz, reflectionCache);
        WordGenerator wordGenerator = new WordGenerator();
        String searchTerm = wordGenerator.newWord(ThreadLocalRandom.current().nextInt(3, 5));
        String prefix = wordGenerator.newWord(ThreadLocalRandom.current().nextInt(3, 5));
        String suffix = wordGenerator.newWord(ThreadLocalRandom.current().nextInt(3, 5));
        String testValue = prefix + searchTerm + suffix;
        allTs.forEach(t -> setField(t, testValue, toSearchBy.getName()));
        dataManager.saveAll(allTs);
        Collection<T> allApiFuzzySearchFetchedTs = ApiLogic
                .fuzzySearch(clazz, dataManager, metaOps, offset, limit, searchTerm, null, null);
        assertThat(
                "result of api call to " + pluralCamelCaseName(clazz) + "FuzzySearch" +
                        "(...)' " + " equals original entries in database",
                allTs,
                isEqualTo(allApiFuzzySearchFetchedTs));
    }


    static <T, E extends ApiMetaOperations<T>> void
    getByIdTest(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps, ReflectionCache reflectionCache){
        T toGetById = randomFrom(dataManager.findAll());
        Object id = getId(toGetById, reflectionCache);
        T fetchedById = ApiLogic.getById(clazz, dataManager, metaOps, id);
        assertThat(clazz.getSimpleName() + " successfully fetched by id",
                toGetById,
                isEqualTo(fetchedById));
    }

    static <T, E extends ApiMetaOperations<T>> void
    getByUniqueTest(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps, String fieldName, ReflectionCache reflectionCache){
        T toGet = randomFrom(dataManager.findAll());
        Object uniqueValue = reflectionCache.getEntitiesCache().get(clazz.getSimpleName()).invokeGetter(toGet, fieldName);
        T fetched = ApiLogic.getByUnique(clazz, dataManager, metaOps, fieldName, uniqueValue);
        assertThat(
                "Successfully fetched a " + toPascalCase(clazz.getSimpleName()) +
                      " by the unique value of " + fieldName + " = " + uniqueValue.toString(),
                toGet, isEqualTo(fetched));
    }

    static <T, E extends ApiMetaOperations<T>> void
    getByTest(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps, String fieldName, ReflectionCache reflectionCache){

        T toGet = randomFrom(dataManager.findAll());
        final CachedEntityType entityType = reflectionCache.getEntitiesCache().get(clazz.getSimpleName());
        Object value = entityType.invokeGetter(toGet, fieldName);
        Collection<T> fetched = ApiLogic.getBy(dataManager, metaOps, fieldName, value);
        for(T instance : fetched){
            assertThat(
                    "successfully fetched instance of " + clazz.getSimpleName() +
                    " by value of " + fieldName + " = " + value.toString(),
                    entityType.invokeGetter(instance, fieldName),
                    isEqualTo(value)
            );
        }
    }

    static <T, E extends ApiMetaOperations<T>> void
    getAllByTest(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps, String fieldName, ReflectionCache reflectionCache){
        Map<Object, T> toGet = firstRandomNIdMap(clazz, dataManager, reflectionCache);
        final CachedEntityType entityType = reflectionCache.getEntitiesCache().get(clazz.getSimpleName());
        List<?> valuesList = fieldValues(fieldName, Arrays.asList(toGet.values().toArray()), entityType);
        Collection<T> fetched = ApiLogic.getAllBy(dataManager, metaOps, fieldName, valuesList);
        assertTrue(fetched.size() >= toGet.size());
        for(T fetchedInstance : fetched){
            T toGetInstance = toGet.get(getId(fetchedInstance, reflectionCache));
            if(toGetInstance != null)
                assertThat(fetchedInstance, isEqualTo(toGetInstance));
        }
    }

    static <T, E extends ApiMetaOperations<T>> void
    selectByTest(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps,
                 String resolverName, Collection<String> fieldNames, EntityMocker entityMocker, ReflectionCache reflectionCache){

        Map<Object, T> toSelect = firstRandomNIdMap(clazz, dataManager, reflectionCache);

        final CachedEntityType entityType = reflectionCache.getEntitiesCache().get(clazz.getSimpleName());
        Map<String, Object> args = new HashMap<>();
        for(String fieldName : fieldNames)
            args.put(fieldName, entityMocker.mockFieldValue(clazz, fieldName));
        Collection<Field> fields = getClassFields(clazz);
        for(Field field : fields){
            if(fieldNames.contains(field.getName())){
                for(Map.Entry<Object, T> instanceEntry : toSelect.entrySet()) {
                    entityType.invokeSetter(instanceEntry.getValue(), field.getName(), args.get(field.getName()));
                }
            }
        }
        Collection<T> selected = ApiLogic.selectBy(dataManager, metaOps, resolverName, new ArrayList<>(args.values()));
        assertTrue(selected.size() >= toSelect.size());
        for (T selectedInstance : selected){
            T toSelectInstance = toSelect.get(getId(selectedInstance, reflectionCache));
            if(toSelectInstance != null)
                assertThat(selectedInstance, isEqualTo(toSelectInstance));
        }
    }

    static <T, E extends ApiMetaOperations<T>> void
    addTest(Class<?> clazz, BaseDataManager<T> dataManager, EntityMocker entityMocker, E metaOps){
        T toAdd = entityMocker.instantiateEntity(clazz);
        T added = ApiLogic.add(dataManager, toAdd, metaOps);
        assertThat(clazz.getSimpleName() + " successfully added", toAdd, isEqualTo(added));
    }

    static <T, E extends ApiMetaOperations<T>>  void
    updateTest(Class<?> clazz, BaseDataManager<T> dataManager, EntityMocker entityMocker,
               ReflectionCache reflectionCache, E metaOps){
        T original = randomInstance(clazz, dataManager);
        T updated = entityMocker.mockUpdate(original);
        setField(updated, getId(original, reflectionCache), "id");
        T updatedOriginal = ApiLogic.update(dataManager, updated, reflectionCache, metaOps);
        assertThat("successfully updated " + clazz.getSimpleName(),
                   updated,  isEqualTo(updatedOriginal));
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>>  void
    archiveTest(Class<?> clazz, ArchivableDataManager<T> dataManager, ReflectionCache reflectionCache, E metaOps){
        T instance = randomInstance(clazz, dataManager);
        final String simpleName = clazz.getSimpleName();
        assertFalse("Default state of " + simpleName + " is non archived", instance.getIsArchived());
        T archivedInstance = ApiLogic.archive(dataManager, instance, reflectionCache, metaOps);
        assertTrue("Instance of " + simpleName + " successfully archived", archivedInstance.getIsArchived());
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>>  void
    archiveCollectionTest(Class<?> clazz, ArchivableDataManager<T> dataManager, E metaOps){
        List<T> instances = firstRandomN(clazz, dataManager);
        int amountToArchive = instances.size();
        final String simpleName = clazz.getSimpleName();
        boolean defaultStateIsNonArchived = true;
        for (T instance : instances)
            if(instance.getIsArchived()){
                defaultStateIsNonArchived = false;
                break;
            }
        assertTrue("Default state of " + simpleName + " is non archived", defaultStateIsNonArchived);
        List<T> archivedInstances = ApiLogic.archiveCollection(dataManager, instances, metaOps);
        boolean successfullyArchivedAllInstances = true;
        for (T instance : archivedInstances)
            if(!instance.getIsArchived()){
                successfullyArchivedAllInstances = false;
                break;
            }
        assertTrue("Successfully archived " + amountToArchive + " instances of " + simpleName, successfullyArchivedAllInstances);
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>>  void
    deArchiveTest(Class<?> clazz, ArchivableDataManager<T> dataManager,
                  ReflectionCache reflectionCache, E metaOps){
        T instance = randomInstance(clazz, dataManager);
        final String simpleName = clazz.getSimpleName();
        assertFalse("Default state of " + simpleName + " is non archived", instance.getIsArchived());
        T archivedInstance = dataManager.archive(instance);
        T deArchivedInstance = ApiLogic.deArchive(dataManager, archivedInstance, reflectionCache, metaOps);
        assertFalse("Instance of " + simpleName + " successfully de archived", deArchivedInstance.getIsArchived());
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>>  void
    deArchiveCollectionTest(Class<?> clazz, ArchivableDataManager<T> dataManager, E metaOps){
        List<T> instances = firstRandomN(clazz, dataManager);
        int amountToArchive = instances.size();
        final String simpleName = clazz.getSimpleName();
        boolean defaultStateIsNonArchived = true;
        for (T instance : instances)
            if(instance.getIsArchived()){
                defaultStateIsNonArchived = false;
                break;
            }
        assertTrue("Default state of " + simpleName + " is non archived", defaultStateIsNonArchived);
        List<T> archivedInstances = dataManager.archiveCollection(instances);
        List<T> deArchivedInstances = ApiLogic.deArchiveCollection(dataManager, archivedInstances, metaOps);
        boolean successfullyDeArchivedAllInstances = true;
        for (T instance : deArchivedInstances)
            if(instance.getIsArchived()){
                successfullyDeArchivedAllInstances = false;
                break;
            }
        assertTrue("Successfully de archived " + amountToArchive + " instances of " + simpleName, successfullyDeArchivedAllInstances);
    }

    static <T, E extends ApiMetaOperations<T>>  void
    deleteTest(Class<?> clazz, BaseDataManager<T> dataManager, ReflectionCache reflectionCache, EntityMocker entityMocker, E metaOps){
        T toDelete = randomInstance(clazz, dataManager);
        T deleted = ApiLogic.delete(dataManager, reflectionCache, toDelete, metaOps);
        Optional<T> shouldNotBePresent = dataManager.findById(getId(deleted, reflectionCache));
        assertFalse(clazz.getSimpleName() + " successfully deleted", shouldNotBePresent.isPresent());
        entityMocker.instantiateEntity(clazz);
    }

    static <T>  void
    getCollectionByIdTest(Class<?> clazz, BaseDataManager<T> dataManager){
        Collection<T> present = dataManager.findAll();
        List<?> ids = dataManager.idList(present);
        Collection<T> fetched = ApiLogic.getCollectionById(dataManager, ids);
        assertThat( "successfully fetched " + present.size() + " " + toPlural(clazz.getSimpleName()) + " by id",
                present, isEqualTo(fetched));
    }

    static <T, E extends ApiMetaOperations<T>>  void
    addCollectionTest(Class<?> clazz, BaseDataManager<T> dataManager, EntityMocker entityMocker, E metaOps){
        int amountToAdd = ThreadLocalRandom.current().nextInt(5, 10);
        List<T> toAdd = new ArrayList<>();
        for (int i = 0; i < amountToAdd; i++)
            toAdd.add(entityMocker.instantiateTransientEntity(clazz));
        Collection<T> added = ApiLogic.addCollection(dataManager, toAdd, metaOps);
        assertThat( "successfully added " + amountToAdd + " " + toPlural(clazz.getSimpleName()),
                toAdd,
                isEqualTo(added));
    }

    static <T, E extends ApiMetaOperations<T>>  void
    updateCollectionTest(Class<?> clazz, BaseDataManager<T> dataManager, EntityMocker entityMocker, E metaOps){
        List<T> updated = firstRandomN(clazz, dataManager);
        int amountToUpdate = updated.size();
        updated.forEach(entityMocker::mockUpdate);
        Collection<T> updatedViaApi = ApiLogic.updateCollection(dataManager, updated, metaOps);
        assertThat("successfully updated " + amountToUpdate + " " + toPlural(clazz.getSimpleName()),
                updated,  isEqualTo(updatedViaApi));
    }

    static <T, E extends ApiMetaOperations<T>>  void
    deleteCollectionTest(Class<?> clazz, BaseDataManager<T> dataManager, EntityMocker entityMocker, E metaOps){
        List<T> toDelete = firstRandomN(clazz, dataManager);
        int amountToDelete = toDelete.size();
        ApiLogic.deleteCollection(dataManager, toDelete, metaOps);
        Collection<?> ids = dataManager.idList(toDelete);
        Collection<T> shouldBeEmpty = dataManager.findAllById(ids);
        assertTrue(amountToDelete + " " + toPlural(clazz.getSimpleName()) + " successfully deleted",
                shouldBeEmpty.isEmpty());
        for (int i = 0; i < amountToDelete; i++) entityMocker.instantiateEntity(clazz);
    }

    static <T, HasT> void getAsEmbeddedEntityTest(Class<?> tClazz,
                                                  Class<?> hasTClazz,
                                                  BaseDataManager<T> tDataManager,
                                                  BaseDataManager<HasT> hasTDataManager,
                                                  String fieldName,
                                                  EntityMocker entityMocker,
                                                  ReflectionCache reflectioncache) {
        List<HasT> owners = firstRandomN(hasTClazz, hasTDataManager);
        Collection<T> embeddedEntities = new ArrayList<>();
        T embeddedEntity;
        for (HasT owner : owners) {
            embeddedEntity = entityMocker.instantiateEntity(tClazz);
            setField(owner, embeddedEntity, fieldName);
            embeddedEntities.add(embeddedEntity);
        }
        owners = hasTDataManager.saveAll(owners);
        Collection<T> fetchedAsEmbedded = ApiLogic.getAsEmbeddedEntity(tDataManager, owners, fieldName, reflectioncache);
        assertThat(
                "successfully fetched " + fetchedAsEmbedded.size() + " " + toPlural(fieldName) +
                        " embedded within " + owners.size() + " " + toPlural(hasTClazz.getSimpleName()),
                embeddedEntities,
                isEqualTo(fetchedAsEmbedded));
    }

    static <T, HasTs> void getAsEmbeddedEntityCollectionTest(Class<?> tClazz,
                                                             Class<?> hasTsClazz,
                                                             BaseDataManager<T> tDataManager,
                                                             BaseDataManager<HasTs> hasTsDataManager,
                                                             String fieldName,
                                                             EntityMocker entityMocker,
                                                             ReflectionCache reflectioncache) {
        List<HasTs> owners = firstRandomN(hasTsClazz, hasTsDataManager);
        Collection<Collection<T>> embeddedEntityCollections = new ArrayList<>();
        Collection<T> embeddedEntityCollection;
        for (HasTs owner : owners) {
            embeddedEntityCollection = persistCollectionOf(tClazz, entityMocker);
            setCollectionField(owner, embeddedEntityCollection, fieldName, entityMocker);
            embeddedEntityCollections.add(embeddedEntityCollection);
        }
        owners = hasTsDataManager.saveAll(owners);
        List<List<T>> fetchedAsEmbedded = ApiLogic
                .getAsEmbeddedCollection(tDataManager, owners, fieldName, reflectioncache);

        assertThat(
                "Successfully fetched " + embeddedEntityCollections.size() +
                      " collections of " + toPlural(tClazz.getSimpleName()) + " from " + owners.size() +
                      " " + toPlural(hasTsClazz.getSimpleName()),
                embeddedEntityCollections, isEqualTo(fetchedAsEmbedded));
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    void addNewToEmbeddedCollectionTest(Class<?> tClazz,
                                        Class<?> hasTsClazz,
                                                  BaseDataManager<T> tDataManager,
                                                  BaseDataManager<HasTs> hasTsDataManager,
                                        String fieldName,
                                        EntityMocker entityMocker,
                                        ReflectionCache reflectioncache,
                                        E metaOps) {
        HasTs toAddTo = entityMocker.instantiateEntity(hasTsClazz);
        List<T> toAdd = transientlyInstantiateCollectionOf(tClazz, entityMocker);
        List<T> added = ApiLogic.addNewToEmbeddedCollection(hasTsDataManager, tDataManager, toAddTo,
                                                            fieldName, toAdd, metaOps, reflectioncache);
        assertThat("successfully added " + added.size() +
                    " " + toPlural(tClazz.getSimpleName()) + " to " +
                    hasTsClazz.getSimpleName(),
                    toAdd, isEqualTo(added));
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    void attachExistingToEmbeddedCollectionTest(Class<?> tClazz,
                                                Class<?> hasTsClazz,
                                                  BaseDataManager<T> tDataManager,
                                                  BaseDataManager<HasTs> hasTsDataManager,
                                                String fieldName,
                                                EntityMocker entityMocker,
                                                ReflectionCache reflectioncache,
                                                E metaOps) {
        HasTs toAttachTo = entityMocker.instantiateEntity(hasTsClazz);
        List<T> toAttach = persistCollectionOf(tClazz, entityMocker);
        List<T> attached = ApiLogic.attachExistingToEmbeddedCollection(hasTsDataManager, tDataManager, toAttachTo,
                                                                       fieldName, toAttach, metaOps, reflectioncache);
        assertThat("successfully attached " + attached.size() +
                        " pre-existing " + toPlural(tClazz.getSimpleName()) + " to " +
                        hasTsClazz.getSimpleName(),
                toAttach, isEqualTo(attached));
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    void updateEmbeddedCollectionTest(Class<?> tClazz,
                                      Class<?> hasTsClazz,
                                                  BaseDataManager<T> tDataManager,
                                                  BaseDataManager<HasTs> hasTsDataManager,
                                      String fieldName,
                                      EntityMocker entityMocker,
                                      ReflectionCache reflectioncache,
                                      E metaOps) {
        HasTs owner = entityMocker.instantiateEntity(hasTsClazz);
        List<T> originalEmbeddedCollection = persistCollectionOf(tClazz, entityMocker);
        setCollectionField(owner, originalEmbeddedCollection, fieldName, entityMocker);
        owner = hasTsDataManager.saveAndFlush(owner);

        Iterable<T> updatedEmbeddedCollection = Lists.newArrayList(originalEmbeddedCollection);
        updatedEmbeddedCollection.forEach(entityMocker::mockUpdate);

        Collection<T> fetchedEmbeddedCollection = ApiLogic.updateEmbeddedCollection(
                                                            hasTsDataManager, tDataManager, owner,
                                                            updatedEmbeddedCollection, metaOps, reflectioncache);
        assertThat(
            "successfully updated " + fetchedEmbeddedCollection.size() + " " +
                    fieldName + " in " + hasTsClazz.getSimpleName(),
                    updatedEmbeddedCollection, isEqualTo(fetchedEmbeddedCollection));
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    void removeFromEmbeddedCollectionTest(Class<?> tClazz,
                                          Class<?> hasTsClazz,
                                          BaseDataManager<T> tDataManager,
                                          BaseDataManager<HasTs> hasTsDataManager,
                                          String fieldName,
                                          EntityMocker entityMocker,
                                          ReflectionCache reflectioncache,
                                          E metaOps) {
        HasTs owner = entityMocker.instantiateEntity(hasTsClazz);
        List<T> originalEmbeddedCollection = persistCollectionOf(tClazz, entityMocker);
        setCollectionField(owner, originalEmbeddedCollection, fieldName, entityMocker);
        tDataManager.saveAll(originalEmbeddedCollection);
        owner = hasTsDataManager.saveAndFlush(owner);

        Collection<T> toRemoveFromCollection = firstRandomEmbeddedN(owner, fieldName, reflectioncache);

        Collection<T> removedFromEmbeddedCollection = ApiLogic.removeFromEmbeddedCollection(
                        hasTsDataManager, tDataManager, owner,
                        fieldName, new ArrayList<>(toRemoveFromCollection),
                        metaOps, reflectioncache);
        int expectedCollectionSize = originalEmbeddedCollection.size() - toRemoveFromCollection.size();
        int actualCollectionSize =
                ((Collection<T>)reflectioncache.getEntitiesCache()
                .get(owner.getClass().getSimpleName())
                .invokeGetter(owner, fieldName)).size();

        assertEquals(
                "successfully removed " + removedFromEmbeddedCollection.size() + " " +
                        fieldName + " from " + hasTsClazz.getSimpleName(),
                expectedCollectionSize, actualCollectionSize);
    }
}
