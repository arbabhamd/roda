/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.roda.core.common.LdapUtilityException;
import org.roda.core.common.UserUtility;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.common.iterables.CloseableIterables;
import org.roda.core.common.validation.ValidationUtils;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.EmailAlreadyExistsException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.GroupAlreadyExistsException;
import org.roda.core.data.exceptions.IllegalOperationException;
import org.roda.core.data.exceptions.InvalidTokenException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.exceptions.UserAlreadyExistsException;
import org.roda.core.data.v2.IdUtils;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.AIPPermissions;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.ip.metadata.DescriptiveMetadata;
import org.roda.core.data.v2.ip.metadata.OtherMetadata;
import org.roda.core.data.v2.ip.metadata.PreservationMetadata;
import org.roda.core.data.v2.ip.metadata.PreservationMetadata.PreservationMetadataType;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.JobReport;
import org.roda.core.data.v2.log.LogEntry;
import org.roda.core.data.v2.user.Group;
import org.roda.core.data.v2.user.User;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.data.v2.validation.ValidationReport;
import org.roda.core.model.utils.JsonUtils;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.model.utils.ResourceParseUtils;
import org.roda.core.storage.Binary;
import org.roda.core.storage.BinaryVersion;
import org.roda.core.storage.ContentPayload;
import org.roda.core.storage.DefaultStoragePath;
import org.roda.core.storage.Directory;
import org.roda.core.storage.EmptyClosableIterable;
import org.roda.core.storage.Resource;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.StringContentPayload;
import org.roda.core.storage.fs.FSPathContentPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that "relates" Model & Storage
 * 
 * FIXME questions:
 * 
 * 1) how to undo things created/changed upon exceptions??? if using fedora
 * perhaps with transactions
 * 
 * @author Luis Faria <lfaria@keep.pt>
 * @author Hélder Silva <hsilva@keep.pt>
 */
public class ModelService extends ModelObservable {

  private static final String AIP_METADATA_FILENAME = "aip.json";

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelService.class);

  private final StorageService storage;
  private Path logFile;
  private static final boolean FAIL_IF_NO_DESCRIPTIVE_METADATA_SCHEMA = false;

  public ModelService(StorageService storage) {
    super();
    this.storage = storage;
    ensureAllContainersExist();
    ensureAllDiretoriesExist();
  }

  private void ensureAllContainersExist() {
    try {
      createContainerIfNotExists(RodaConstants.STORAGE_CONTAINER_AIP);
      createContainerIfNotExists(RodaConstants.STORAGE_CONTAINER_PRESERVATION);
      createContainerIfNotExists(RodaConstants.STORAGE_CONTAINER_ACTIONLOG);
      createContainerIfNotExists(RodaConstants.STORAGE_CONTAINER_JOB);
      createContainerIfNotExists(RodaConstants.STORAGE_CONTAINER_JOB_REPORT);
    } catch (RequestNotValidException | GenericException | AuthorizationDeniedException e) {
      LOGGER.error("Error while ensuring that all containers exist", e);
    }

  }

  private void createContainerIfNotExists(String containerName)
    throws RequestNotValidException, GenericException, AuthorizationDeniedException {
    try {
      storage.createContainer(DefaultStoragePath.parse(containerName));
    } catch (AlreadyExistsException e) {
      // do nothing
    }
  }

  private void ensureAllDiretoriesExist() {
    try {
      createDirectoryIfNotExists(
        DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_PRESERVATION, RodaConstants.STORAGE_DIRECTORY_AGENTS));
    } catch (RequestNotValidException | GenericException | AuthorizationDeniedException e) {
      LOGGER.error("Error initializing directories", e);
    }
  }

  private void createDirectoryIfNotExists(StoragePath directoryPath)
    throws GenericException, AuthorizationDeniedException {
    try {
      storage.createDirectory(directoryPath);
    } catch (AlreadyExistsException e) {
      // do nothing
    }

  }

  public StorageService getStorage() {
    return storage;
  }

  private void createAIPMetadata(AIP aip) throws RequestNotValidException, GenericException, AlreadyExistsException,
    AuthorizationDeniedException, NotFoundException {
    createAIPMetadata(aip, ModelUtils.getAIPStoragePath(aip.getId()));
  }

  private void createAIPMetadata(AIP aip, StoragePath storagePath) throws RequestNotValidException, GenericException,
    AlreadyExistsException, AuthorizationDeniedException, NotFoundException {
    String json = JsonUtils.getJsonFromObject(aip);
    DefaultStoragePath metadataStoragePath = DefaultStoragePath.parse(storagePath, AIP_METADATA_FILENAME);
    boolean asReference = false;
    storage.createBinary(metadataStoragePath, new StringContentPayload(json), asReference);
  }

  private void updateAIPMetadata(AIP aip)
    throws GenericException, NotFoundException, RequestNotValidException, AuthorizationDeniedException {
    updateAIPMetadata(aip, ModelUtils.getAIPStoragePath(aip.getId()));
  }

  private void updateAIPMetadata(AIP aip, StoragePath storagePath)
    throws GenericException, NotFoundException, RequestNotValidException, AuthorizationDeniedException {
    String json = JsonUtils.getJsonFromObject(aip);
    DefaultStoragePath metadataStoragePath = DefaultStoragePath.parse(storagePath, AIP_METADATA_FILENAME);
    boolean asReference = false;
    boolean createIfNotExists = true;
    storage.updateBinaryContent(metadataStoragePath, new StringContentPayload(json), asReference, createIfNotExists);
  }

  private AIP getAIPMetadata(String aipId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return getAIPMetadata(aipId, ModelUtils.getAIPStoragePath(aipId));
  }

  private AIP getAIPMetadata(StoragePath storagePath)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return getAIPMetadata(storagePath.getName(), storagePath);
  }

  private AIP getAIPMetadata(String aipId, StoragePath storagePath)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    DefaultStoragePath metadataStoragePath = DefaultStoragePath.parse(storagePath, AIP_METADATA_FILENAME);
    Binary binary = storage.getBinary(metadataStoragePath);

    String json;
    AIP aip;
    InputStream inputStream = null;
    try {
      inputStream = binary.getContent().createInputStream();
      json = IOUtils.toString(inputStream);
      aip = JsonUtils.getObjectFromJson(json, AIP.class);
    } catch (IOException | GenericException e) {
      throw new GenericException("Could not parse AIP metadata of " + aipId + " at " + metadataStoragePath, e);
    } finally {
      IOUtils.closeQuietly(inputStream);
    }

    // Setting information that does not come in JSON
    aip.setId(aipId);

    return aip;
  }

  public CloseableIterable<AIP> listAIPs()
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    CloseableIterable<AIP> aipsIterable;

    final boolean recursive = false;
    final CloseableIterable<Resource> resourcesIterable = storage
      .listResourcesUnderContainer(ModelUtils.getAIPcontainerPath(), recursive);
    Iterator<Resource> resourcesIterator = resourcesIterable.iterator();

    aipsIterable = new CloseableIterable<AIP>() {

      @Override
      public Iterator<AIP> iterator() {
        return new Iterator<AIP>() {

          @Override
          public boolean hasNext() {
            if (resourcesIterator == null) {
              return false;
            }
            return resourcesIterator.hasNext();
          }

          @Override
          public AIP next() {
            try {
              Resource next = resourcesIterator.next();
              return getAIPMetadata(next.getStoragePath());
            } catch (NoSuchElementException | NotFoundException | GenericException | RequestNotValidException
              | AuthorizationDeniedException e) {
              LOGGER.error("Error while listing AIPs", e);
              return null;
            }
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }

      @Override
      public void close() throws IOException {
        resourcesIterable.close();
      }
    };

    return aipsIterable;
  }

  public AIP retrieveAIP(String aipId)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    return getAIPMetadata(aipId);
  }

  /**
   * Create a new AIP
   * 
   * @param aipId
   *          Suggested ID for the AIP, if <code>null</code> then an ID will be
   *          automatically generated. If ID cannot be allowed because it
   *          already exists or is not valid, another ID will be provided.
   * @param sourceStorage
   * @param sourceContainer
   * @param sourcePath
   * @param sourceName
   * @return
   * @throws RequestNotValidException
   * @throws GenericException
   * @throws NotFoundException
   * @throws AuthorizationDeniedException
   * @throws AlreadyExistsException
   * @throws ValidationException
   */
  public AIP createAIP(String aipId, StorageService sourceStorage, StoragePath sourcePath, boolean notify)
    throws RequestNotValidException, GenericException, AuthorizationDeniedException, AlreadyExistsException,
    NotFoundException, ValidationException {
    // XXX possible optimization would be to allow move between storage
    // TODO support asReference
    ModelService sourceModelService = new ModelService(sourceStorage);
    AIP aip;

    Directory sourceDirectory = sourceStorage.getDirectory(sourcePath);
    ValidationReport validationReport = isAIPvalid(sourceModelService, sourceDirectory,
      FAIL_IF_NO_DESCRIPTIVE_METADATA_SCHEMA);
    if (validationReport.isValid()) {

      storage.copy(sourceStorage, sourcePath, ModelUtils.getAIPStoragePath(aipId));
      Directory newDirectory = storage.getDirectory(ModelUtils.getAIPStoragePath(aipId));

      aip = getAIPMetadata(newDirectory.getStoragePath());
      if (notify) {
        notifyAipCreated(aip);
      }
    } else {
      throw new ValidationException(validationReport);
    }

    return aip;
  }

  public AIP createAIP(String parentId) throws RequestNotValidException, NotFoundException, GenericException,
    AlreadyExistsException, AuthorizationDeniedException {
    boolean active = true;
    AIPPermissions permissions = new AIPPermissions();
    boolean notify = true;
    return createAIP(active, parentId, permissions, notify);
  }

  public AIP createAIP(boolean active, String parentId, AIPPermissions permissions) throws RequestNotValidException,
    NotFoundException, GenericException, AlreadyExistsException, AuthorizationDeniedException {
    boolean notify = true;
    return createAIP(active, parentId, permissions, notify);
  }

  public AIP createAIP(boolean active, String parentId, AIPPermissions permissions, boolean notify)
    throws RequestNotValidException, NotFoundException, GenericException, AlreadyExistsException,
    AuthorizationDeniedException {

    Directory directory = storage.createRandomDirectory(DefaultStoragePath.parse(RodaConstants.STORAGE_CONTAINER_AIP));

    String id = directory.getStoragePath().getName();
    List<DescriptiveMetadata> descriptiveMetadata = new ArrayList<>();
    List<Representation> representations = new ArrayList<>();

    AIP aip = new AIP(id, parentId, active, permissions, descriptiveMetadata, representations);

    createAIPMetadata(aip, directory.getStoragePath());

    if (notify) {
      notifyAipCreated(aip);
    }

    return aip;
  }

  public AIP createAIP(String aipId, StorageService sourceStorage, StoragePath sourcePath)
    throws RequestNotValidException, GenericException, AuthorizationDeniedException, AlreadyExistsException,
    NotFoundException, ValidationException {
    return createAIP(aipId, sourceStorage, sourcePath, true);
  }

  public AIP notifyAIPCreated(String aipId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    AIP aip = getAIPMetadata(aipId);
    notifyAipCreated(aip);
    return aip;
  }

  public AIP notifyAIPUpdated(String aipId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    AIP aip = getAIPMetadata(aipId);
    notifyAipUpdated(aip);
    return aip;
  }

  // TODO support asReference
  public AIP updateAIP(String aipId, StorageService sourceStorage, StoragePath sourcePath)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException,
    AlreadyExistsException, ValidationException {
    // TODO verify structure of source AIP and update it in the storage
    ModelService sourceModelService = new ModelService(sourceStorage);
    AIP aip;

    Directory sourceDirectory = sourceStorage.getDirectory(sourcePath);
    ValidationReport validationReport = isAIPvalid(sourceModelService, sourceDirectory,
      FAIL_IF_NO_DESCRIPTIVE_METADATA_SCHEMA);
    if (validationReport.isValid()) {
      StoragePath aipPath = ModelUtils.getAIPStoragePath(aipId);

      // XXX possible optimization only creating new files, updating changed and
      // removing deleted ones.
      storage.deleteResource(aipPath);

      storage.copy(sourceStorage, sourcePath, aipPath);
      Directory directoryUpdated = storage.getDirectory(aipPath);

      aip = getAIPMetadata(directoryUpdated.getStoragePath());
      notifyAipUpdated(aip);
    } else {
      throw new ValidationException(validationReport);
    }

    return aip;
  }

  public AIP updateAIP(AIP aip)
    throws GenericException, NotFoundException, RequestNotValidException, AuthorizationDeniedException {
    updateAIPMetadata(aip);
    notifyAipUpdated(aip);

    return aip;
  }

  public void deleteAIP(String aipId)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    StoragePath aipPath = ModelUtils.getAIPStoragePath(aipId);
    storage.deleteResource(aipPath);
    notifyAipDeleted(aipId);
  }

  public Binary retrieveDescriptiveMetadataBinary(String aipId, String descriptiveMetadataId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    Binary binary;
    StoragePath binaryPath = ModelUtils.getDescriptiveMetadataPath(aipId, descriptiveMetadataId);
    binary = storage.getBinary(binaryPath);

    return binary;
  }

  public DescriptiveMetadata retrieveDescriptiveMetadata(String aipId, String descriptiveMetadataId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    AIP aip = getAIPMetadata(aipId);

    DescriptiveMetadata ret = null;
    for (DescriptiveMetadata descriptiveMetadata : aip.getDescriptiveMetadata()) {
      if (descriptiveMetadata.getId().equals(descriptiveMetadataId)) {
        ret = descriptiveMetadata;
        break;
      }
    }

    if (ret == null) {
      throw new NotFoundException("Could not find descriptive metadata: " + descriptiveMetadataId);
    }

    return ret;
  }

  public DescriptiveMetadata createDescriptiveMetadata(String aipId, String descriptiveMetadataId,
    ContentPayload payload, String descriptiveMetadataType) throws RequestNotValidException, GenericException,
      AlreadyExistsException, AuthorizationDeniedException, NotFoundException {
    return createDescriptiveMetadata(aipId, descriptiveMetadataId, payload, descriptiveMetadataType, true);
  }

  public DescriptiveMetadata createDescriptiveMetadata(String aipId, String descriptiveMetadataId,
    ContentPayload payload, String descriptiveMetadataType, boolean notify) throws RequestNotValidException,
      GenericException, AlreadyExistsException, AuthorizationDeniedException, NotFoundException {
    DescriptiveMetadata descriptiveMetadataBinary = null;

    // StoragePath binaryPath = binary.getStoragePath();
    StoragePath binaryPath = ModelUtils.getDescriptiveMetadataPath(aipId, descriptiveMetadataId);
    boolean asReference = false;

    storage.createBinary(binaryPath, payload, asReference);
    descriptiveMetadataBinary = new DescriptiveMetadata(descriptiveMetadataId, aipId, descriptiveMetadataType);

    AIP aip = getAIPMetadata(aipId);
    aip.getDescriptiveMetadata().add(descriptiveMetadataBinary);
    updateAIPMetadata(aip);

    if (notify) {
      notifyDescriptiveMetadataCreated(descriptiveMetadataBinary);
    }

    return descriptiveMetadataBinary;
  }

  public DescriptiveMetadata updateDescriptiveMetadata(String aipId, String descriptiveMetadataId,
    ContentPayload descriptiveMetadataPayload, String descriptiveMetadataType, String message)
      throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException,
      ValidationException {
    DescriptiveMetadata ret = null;

    StoragePath binaryPath = ModelUtils.getDescriptiveMetadataPath(aipId, descriptiveMetadataId);
    boolean asReference = false;
    boolean createIfNotExists = false;

    // Create version snapshot
    storage.createBinaryVersion(binaryPath, message);

    // Update
    storage.updateBinaryContent(binaryPath, descriptiveMetadataPayload, asReference, createIfNotExists);

    // set descriptive metadata type
    AIP aip = getAIPMetadata(aipId);
    List<DescriptiveMetadata> descriptiveMetadata = aip.getDescriptiveMetadata();
    Optional<DescriptiveMetadata> odm = descriptiveMetadata.stream()
      .filter(dm -> dm.getId().equals(descriptiveMetadataId)).findFirst();
    if (odm.isPresent()) {
      ret = odm.get();
      ret.setType(descriptiveMetadataType);
    } else {
      ret = new DescriptiveMetadata(descriptiveMetadataId, aipId, descriptiveMetadataType);
      descriptiveMetadata.add(ret);
    }
    updateAIP(aip);

    notifyDescriptiveMetadataUpdated(ret);

    return ret;
  }

  public void deleteDescriptiveMetadata(String aipId, String descriptiveMetadataId)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    StoragePath binaryPath = ModelUtils.getDescriptiveMetadataPath(aipId, descriptiveMetadataId);

    storage.deleteResource(binaryPath);

    // update AIP metadata
    AIP aip = getAIPMetadata(aipId);
    for (Iterator<DescriptiveMetadata> it = aip.getDescriptiveMetadata().iterator(); it.hasNext();) {
      DescriptiveMetadata descriptiveMetadata = it.next();
      if (descriptiveMetadata.getId().equals(descriptiveMetadataId)) {
        it.remove();
        break;
      }
    }
    updateAIPMetadata(aip);

    notifyDescriptiveMetadataDeleted(aipId, descriptiveMetadataId);

  }

  public CloseableIterable<BinaryVersion> listDescriptiveMetadataVersions(String aipId, String descriptiveMetadataId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    StoragePath binaryPath = ModelUtils.getDescriptiveMetadataPath(aipId, descriptiveMetadataId);
    return storage.listBinaryVersions(binaryPath);
  }

  public BinaryVersion revertDescriptiveMetadataVersion(String aipId, String descriptiveMetadataId, String versionId,
    String message) throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    StoragePath binaryPath = ModelUtils.getDescriptiveMetadataPath(aipId, descriptiveMetadataId);

    BinaryVersion currentVersion = storage.createBinaryVersion(binaryPath, message);
    storage.revertBinaryVersion(binaryPath, versionId);

    return currentVersion;
  }

  public Representation retrieveRepresentation(String aipId, String representationId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    AIP aip = getAIPMetadata(aipId);

    Representation ret = null;
    for (Representation representation : aip.getRepresentations()) {
      if (representation.getId().equals(representationId)) {
        ret = representation;
        break;
      }
    }

    if (ret == null) {
      throw new NotFoundException("Could not find representation: " + representationId);
    }

    return ret;
  }

  public Representation createRepresentation(String aipId, String representationId, boolean original, boolean notify)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException,
    AlreadyExistsException {
    Representation representation = new Representation(representationId, aipId, original);

    // update AIP metadata
    AIP aip = getAIPMetadata(aipId);
    aip.getRepresentations().add(representation);
    updateAIPMetadata(aip);

    if (notify) {
      notifyRepresentationCreated(representation);
    }

    return representation;

  }

  // TODO support asReference
  public Representation createRepresentation(String aipId, String representationId, boolean original,
    StorageService sourceStorage, StoragePath sourcePath) throws RequestNotValidException, GenericException,
      NotFoundException, AuthorizationDeniedException, AlreadyExistsException, ValidationException {
    Representation representation;

    StoragePath directoryPath = ModelUtils.getRepresentationStoragePath(aipId, representationId);

    // verify structure of source representation
    Directory sourceDirectory = sourceStorage.getDirectory(sourcePath);
    ValidationReport validationReport = isRepresentationValid(sourceDirectory);
    if (validationReport.isValid()) {
      storage.copy(sourceStorage, sourcePath, directoryPath);

      representation = new Representation(representationId, aipId, original);

      // update AIP metadata
      AIP aip = getAIPMetadata(aipId);
      aip.getRepresentations().add(representation);
      updateAIPMetadata(aip);

      notifyRepresentationCreated(representation);
    } else {
      throw new ValidationException(validationReport);
    }

    return representation;
  }

  public Representation updateRepresentation(String aipId, String representationId, boolean original,
    StorageService sourceStorage, StoragePath sourcePath) throws RequestNotValidException, NotFoundException,
      GenericException, AuthorizationDeniedException, ValidationException {
    Representation representation;

    // verify structure of source representation
    Directory sourceDirectory = sourceStorage.getDirectory(sourcePath);
    ValidationReport validationReport = isRepresentationValid(sourceDirectory);

    if (validationReport.isValid()) {
      // XXX possible optimization only creating new files, updating changed and
      // removing deleted

      StoragePath representationPath = ModelUtils.getRepresentationStoragePath(aipId, representationId);
      storage.deleteResource(representationPath);
      try {
        storage.copy(sourceStorage, sourcePath, representationPath);
      } catch (AlreadyExistsException e) {
        throw new GenericException("Copying after delete gave an unexpected already exists exception", e);
      }

      // build return object
      representation = new Representation(representationId, aipId, original);

      notifyRepresentationUpdated(representation);
    } else {
      throw new ValidationException(validationReport);
    }

    return representation;
  }

  public void deleteRepresentation(String aipId, String representationId)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    StoragePath representationPath = ModelUtils.getRepresentationStoragePath(aipId, representationId);

    storage.deleteResource(representationPath);

    // update AIP metadata
    AIP aip = getAIPMetadata(aipId);
    for (Iterator<Representation> it = aip.getRepresentations().iterator(); it.hasNext();) {
      Representation representation = it.next();
      if (representation.getId().equals(representationId)) {
        it.remove();
        break;
      }
    }
    updateAIPMetadata(aip);

    notifyRepresentationDeleted(aipId, representationId);

  }

  public CloseableIterable<File> listFilesUnder(String aipId, String representationId, boolean recursive)
    throws NotFoundException, GenericException, RequestNotValidException, AuthorizationDeniedException {

    final StoragePath storagePath = ModelUtils.getRepresentationDataStoragePath(aipId, representationId);
    CloseableIterable<File> ret;
    try {
      final CloseableIterable<Resource> iterable = storage.listResourcesUnderDirectory(storagePath, recursive);
      ret = ResourceParseUtils.convert(iterable, File.class);
    } catch (NotFoundException e) {
      // check if AIP exists
      storage.getDirectory(ModelUtils.getAIPStoragePath(aipId));
      // if no exception was sent by above method, return empty list
      ret = new EmptyClosableIterable<File>();
    }

    return ret;

  }

  public CloseableIterable<File> listFilesUnder(File f, boolean recursive)
    throws NotFoundException, GenericException, RequestNotValidException, AuthorizationDeniedException {
    return listFilesUnder(f.getAipId(), f.getRepresentationId(), f.getPath(), f.getId(), recursive);
  }

  public CloseableIterable<File> listFilesUnder(String aipId, String representationId, List<String> directoryPath,
    String fileId, boolean recursive)
      throws NotFoundException, GenericException, RequestNotValidException, AuthorizationDeniedException {
    final StoragePath filePath = ModelUtils.getFileStoragePath(aipId, representationId, directoryPath, fileId);
    final CloseableIterable<Resource> iterable = storage.listResourcesUnderDirectory(filePath, recursive);
    return ResourceParseUtils.convert(iterable, File.class);
  }

  public File retrieveFile(String aipId, String representationId, List<String> directoryPath, String fileId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    File file;
    StoragePath filePath = ModelUtils.getFileStoragePath(aipId, representationId, directoryPath, fileId);
    Binary binary = storage.getBinary(filePath);
    file = ResourceParseUtils.convertResourceTo(binary, File.class);

    return file;
  }

  public File createFile(String aipId, String representationId, List<String> directoryPath, String fileId,
    ContentPayload contentPayload) throws RequestNotValidException, GenericException, AlreadyExistsException,
      AuthorizationDeniedException, NotFoundException {
    return createFile(aipId, representationId, directoryPath, fileId, contentPayload, true);

  }

  public File createFile(String aipId, String representationId, List<String> directoryPath, String fileId,
    ContentPayload contentPayload, boolean notify) throws RequestNotValidException, GenericException,
      AlreadyExistsException, AuthorizationDeniedException, NotFoundException {
    File file;
    // FIXME how to set this?
    boolean asReference = false;

    StoragePath filePath = ModelUtils.getFileStoragePath(aipId, representationId, directoryPath, fileId);

    final Binary createdBinary = storage.createBinary(filePath, contentPayload, asReference);
    file = ResourceParseUtils.convertResourceTo(createdBinary, File.class);

    if (notify) {
      notifyFileCreated(file);
    }

    return file;
  }

  public File updateFile(String aipId, String representationId, List<String> directoryPath, String fileId,
    Binary binary, boolean createIfNotExists, boolean notify)
      throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    File file = null;
    // FIXME how to set this?
    boolean asReference = false;

    StoragePath filePath = ModelUtils.getFileStoragePath(aipId, representationId, directoryPath, fileId);

    storage.updateBinaryContent(filePath, binary.getContent(), asReference, createIfNotExists);
    Binary binaryUpdated = storage.getBinary(filePath);
    file = ResourceParseUtils.convertResourceTo(binaryUpdated, File.class);
    if (notify) {
      notifyFileUpdated(file);
    }

    return file;
  }

  public void deleteFile(String aipId, String representationId, List<String> directoryPath, String fileId,
    boolean notify) throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {

    StoragePath filePath = ModelUtils.getFileStoragePath(aipId, representationId, directoryPath, fileId);
    storage.deleteResource(filePath);
    if (notify) {
      notifyFileDeleted(aipId, representationId, directoryPath, fileId);
    }

  }

  private ValidationReport isAIPvalid(ModelService model, Directory directory,
    boolean failIfNoDescriptiveMetadataSchema)
      throws GenericException, RequestNotValidException, NotFoundException, AuthorizationDeniedException {
    ValidationReport report = new ValidationReport();

    // validate metadata (against schemas)
    ValidationReport descriptiveMetadataValidationReport = ValidationUtils.isAIPDescriptiveMetadataValid(model,
      directory.getStoragePath().getName(), failIfNoDescriptiveMetadataSchema);

    report.setValid(descriptiveMetadataValidationReport.isValid());
    report.setIssues(descriptiveMetadataValidationReport.getIssues());

    // FIXME validate others aspects

    return report;
  }

  private ValidationReport isRepresentationValid(Directory directory) {
    return new ValidationReport();
  }

  public Binary retrievePreservationRepresentation(String aipId, String representationId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    StoragePath path = ModelUtils.getPreservationMetadataStoragePath(representationId,
      PreservationMetadataType.OBJECT_REPRESENTATION, aipId, representationId);
    return storage.getBinary(path);
  }

  public Binary retrievePreservationFile(String aipId, String representationId, List<String> fileDirectoryPath,
    String fileId) throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    String id = IdUtils.getPreservationMetadataId(PreservationMetadataType.OBJECT_FILE, aipId, representationId,
      fileDirectoryPath, fileId);
    StoragePath filePath = ModelUtils.getPreservationMetadataStoragePath(id, PreservationMetadataType.OBJECT_FILE,
      aipId, representationId, fileDirectoryPath, fileId);
    return storage.getBinary(filePath);
  }

  public Binary retrievePreservationFile(File file)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return retrievePreservationFile(file.getAipId(), file.getRepresentationId(), file.getPath(), file.getId());
  }

  public Binary retrievePreservationEvent(String aipId, String representationId, String preservationID)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    StoragePath storagePath = ModelUtils.getPreservationMetadataStoragePath(preservationID,
      PreservationMetadataType.EVENT, aipId, representationId);
    return storage.getBinary(storagePath);
  }

  public Binary retrievePreservationAgent(String preservationID)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    StoragePath storagePath = ModelUtils.getPreservationMetadataStoragePath(preservationID,
      PreservationMetadataType.AGENT);
    return storage.getBinary(storagePath);
  }

  // FIXME this should be synchronized (at least access to logFile)
  public void addLogEntry(LogEntry logEntry, Path logDirectory, boolean notify)
    throws GenericException, RequestNotValidException, AuthorizationDeniedException, NotFoundException {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String date = sdf.format(new Date()) + ".log";
    logFile = logDirectory.resolve(date);

    // verify if file exists and if not, if older files exist (in that case,
    // move them to storage)
    if (!Files.exists(logFile)) {
      findOldLogsAndMoveThemToStorage(logDirectory, logFile);
      try {
        Files.createFile(logFile);
      } catch (IOException e) {
        throw new GenericException("Error creating file to write log into", e);
      }
    }

    // write to log file
    JsonUtils.appendObjectToFile(logEntry, logFile);

    // emit event
    if (notify) {
      notifyLogEntryCreated(logEntry);
    }
  }

  public void addLogEntry(LogEntry logEntry, Path logDirectory)
    throws GenericException, RequestNotValidException, AuthorizationDeniedException, NotFoundException {
    addLogEntry(logEntry, logDirectory, true);
  }

  // FIXME this should be synchronized (at least access to logFile)
  public synchronized void findOldLogsAndMoveThemToStorage(Path logDirectory, Path currentLogFile)
    throws RequestNotValidException, AuthorizationDeniedException, NotFoundException {
    try {
      final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(logDirectory);

      for (Path path : directoryStream) {
        if (!path.equals(currentLogFile)) {
          try {
            StoragePath logPath = ModelUtils.getLogStoragePath(path.getFileName().toString());
            storage.createBinary(logPath, new FSPathContentPayload(path), false);
            Files.delete(path);
          } catch (IOException | GenericException | AlreadyExistsException e) {
            LOGGER.error("Error archiving log file", e);
          }
        }
      }
      directoryStream.close();

    } catch (IOException e) {
      LOGGER.error("Error listing directory for log files", e);
    }
  }

  public void registerUser(User user, String password, boolean useModel, boolean notify)
    throws GenericException, UserAlreadyExistsException, EmailAlreadyExistsException {
    boolean success = true;
    try {
      if (useModel) {
        UserUtility.getLdapUtility().registerUser(user, password);
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error registering user to LDAP", e);
    } catch (UserAlreadyExistsException e) {
      success = false;
      throw new UserAlreadyExistsException("User already exists", e);
    } catch (EmailAlreadyExistsException e) {
      success = false;
      throw new EmailAlreadyExistsException("Email already exists", e);
    }
    if (success && notify) {
      notifyUserCreated(user);
    }
  }

  public void addUser(User user, boolean useModel, boolean notify) throws GenericException, EmailAlreadyExistsException,
    UserAlreadyExistsException, IllegalOperationException, NotFoundException {
    addUser(user, null, useModel, notify);
  }

  public void addUser(User user, String password, boolean useModel, boolean notify) throws GenericException,
    EmailAlreadyExistsException, UserAlreadyExistsException, IllegalOperationException, NotFoundException {
    boolean success = true;
    try {
      if (useModel) {
        UserUtility.getLdapUtility().addUser(user);

        if (password != null) {
          UserUtility.getLdapUtility().setUserPassword(user.getId(), password);
        }
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error adding user to LDAP", e);
    } catch (UserAlreadyExistsException e) {
      success = false;
      throw new UserAlreadyExistsException("User already exists", e);
    } catch (EmailAlreadyExistsException e) {
      success = false;
      throw new EmailAlreadyExistsException("Email already exists", e);
    }
    if (success && notify) {
      notifyUserCreated(user);
    }
  }

  public void modifyUser(User user, boolean useModel, boolean notify)
    throws GenericException, AlreadyExistsException, NotFoundException, AuthorizationDeniedException {
    modifyUser(user, null, useModel, notify);
  }

  public void modifyUser(User user, String password, boolean useModel, boolean notify)
    throws GenericException, AlreadyExistsException, NotFoundException, AuthorizationDeniedException {
    boolean success = true;
    try {
      if (useModel) {
        if (password != null) {
          UserUtility.getLdapUtility().setUserPassword(user.getId(), password);
        }

        UserUtility.getLdapUtility().modifyUser(user);
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error modifying user to LDAP", e);
    } catch (EmailAlreadyExistsException e) {
      success = false;
      throw new AlreadyExistsException("User already exists", e);
    } catch (NotFoundException e) {
      success = false;
      throw new NotFoundException("User doesn't exist", e);
    } catch (IllegalOperationException e) {
      success = false;
      throw new AuthorizationDeniedException("Illegal operation", e);
    }
    if (success && notify) {
      notifyUserUpdated(user);
    }
  }

  public void removeUser(String id, boolean useModel, boolean notify)
    throws GenericException, AuthorizationDeniedException {
    boolean success = true;
    try {
      if (useModel) {
        UserUtility.getLdapUtility().removeUser(id);
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error removing user from LDAP", e);
    } catch (IllegalOperationException e) {
      success = false;
      throw new AuthorizationDeniedException("Illegal operation", e);
    }
    if (success && notify) {
      notifyUserDeleted(id);
    }
  }

  public void addGroup(Group group, boolean useModel, boolean notify) throws GenericException, AlreadyExistsException {
    boolean success = true;
    try {
      if (useModel) {
        UserUtility.getLdapUtility().addGroup(group);
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error adding group to LDAP", e);
    } catch (GroupAlreadyExistsException e) {
      success = false;
      throw new AlreadyExistsException("Group already exists", e);
    }
    if (success && notify) {
      notifyGroupCreated(group);
    }
  }

  public void modifyGroup(Group group, boolean useModel, boolean notify)
    throws GenericException, NotFoundException, AuthorizationDeniedException {
    boolean success = true;
    try {
      if (useModel) {
        UserUtility.getLdapUtility().modifyGroup(group);
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error modifying group to LDAP", e);
    } catch (NotFoundException e) {
      success = false;
      throw new NotFoundException("Group doesn't exist", e);
    } catch (IllegalOperationException e) {
      success = false;
      throw new AuthorizationDeniedException("Illegal operation", e);
    }
    if (success && notify) {
      notifyGroupUpdated(group);
    }
  }

  public void removeGroup(String id, boolean useModel, boolean notify)
    throws GenericException, AuthorizationDeniedException {
    boolean success = true;
    try {
      if (useModel) {
        UserUtility.getLdapUtility().removeGroup(id);
      }
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error removing group from LDAP", e);
    } catch (IllegalOperationException e) {
      success = false;
      throw new AuthorizationDeniedException("Illegal operation", e);
    }
    if (success && notify) {
      notifyGroupDeleted(id);
    }
  }

  public User confirmUserEmail(String username, String email, String emailConfirmationToken, boolean useModel,
    boolean notify) throws NotFoundException, InvalidTokenException, GenericException {
    User user = null;
    boolean success = true;
    try {
      if (useModel) {
        user = UserUtility.getLdapUtility().confirmUserEmail(username, email, emailConfirmationToken);
      }
      success = true;
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error on password reset", e);
    } catch (NotFoundException e) {
      success = false;
      throw new NotFoundException("User doesn't exist", e);
    } catch (InvalidTokenException e) {
      success = false;
      throw new InvalidTokenException("Token exception", e);
    }
    if (success && user != null && notify) {
      notifyUserUpdated(user);
    }
    return user;
  }

  public User requestPasswordReset(String username, String email, boolean useModel, boolean notify)
    throws IllegalOperationException, NotFoundException, GenericException {
    User user = null;
    boolean success = true;
    try {
      if (useModel) {
        user = UserUtility.getLdapUtility().requestPasswordReset(username, email);
      }
      success = true;
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error requesting password reset", e);
    } catch (NotFoundException e) {
      success = false;
      throw new NotFoundException("User doesn't exist", e);
    } catch (IllegalOperationException e) {
      success = false;
      throw new IllegalOperationException("Illegal operation", e);
    }
    if (success && user != null && notify) {
      notifyUserUpdated(user);
    }
    return user;
  }

  public User resetUserPassword(String username, String password, String resetPasswordToken, boolean useModel,
    boolean notify) throws NotFoundException, InvalidTokenException, IllegalOperationException, GenericException {
    User user = null;
    boolean success = true;
    try {
      if (useModel) {
        user = UserUtility.getLdapUtility().resetUserPassword(username, password, resetPasswordToken);
      }
      success = true;
    } catch (LdapUtilityException e) {
      success = false;
      throw new GenericException("Error on password reset", e);
    } catch (NotFoundException e) {
      success = false;
      throw new NotFoundException("User doesn't exist", e);
    } catch (InvalidTokenException e) {
      success = false;
      throw new InvalidTokenException("Token exception", e);
    } catch (IllegalOperationException e) {
      success = false;
      throw new IllegalOperationException("Illegal operation", e);
    }
    if (success && user != null && notify) {
      notifyUserUpdated(user);
    }
    return user;
  }

  public Binary retrieveOtherMetadataBinary(OtherMetadata om)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    return retrieveOtherMetadataBinary(om.getAipId(), om.getRepresentationId(), om.getFileDirectoryPath(),
      om.getFileId(), om.getFileSuffix(), om.getType());
  }

  public Binary retrieveOtherMetadataBinary(String aipId, String representationId, List<String> fileDirectoryPath,
    String fileId, String fileSuffix, String type)
      throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    Binary binary;
    StoragePath binaryPath = ModelUtils.getOtherMetadataStoragePath(aipId, representationId, fileDirectoryPath, fileId,
      fileSuffix, type);
    binary = storage.getBinary(binaryPath);

    return binary;
  }

  public OtherMetadata createOtherMetadata(String aipId, String representationId, List<String> fileDirectoryPath,
    String fileId, String fileSuffix, String type, ContentPayload payload, boolean notify)
      throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {
    OtherMetadata om = null;

    StoragePath binaryPath = ModelUtils.getOtherMetadataStoragePath(aipId, representationId, fileDirectoryPath, fileId,
      fileSuffix, type);
    boolean asReference = false;
    boolean createIfNotExists = true;
    try {
      storage.createBinary(binaryPath, payload, asReference);
    } catch (AlreadyExistsException e) {
      storage.updateBinaryContent(binaryPath, payload, asReference, createIfNotExists);
    }

    String id = IdUtils.getOtherMetadataId(type, aipId, representationId, fileDirectoryPath, fileId);

    om = new OtherMetadata(id, type, aipId, representationId, fileDirectoryPath, fileId, fileSuffix);

    if (notify) {
      notifyOtherMetadataCreated(om);
    }

    return om;
  }

  public void createOrUpdateJob(Job job) throws GenericException {
    // create or update job in storage
    try {
      String jobAsJson = JsonUtils.getJsonFromObject(job);
      StoragePath jobPath = ModelUtils.getJobStoragePath(job.getId());
      storage.updateBinaryContent(jobPath, new StringContentPayload(jobAsJson), false, true);
    } catch (GenericException | RequestNotValidException | AuthorizationDeniedException | NotFoundException e) {
      LOGGER.error("Error creating/updating job in storage", e);
    }

    // index it
    notifyJobCreatedOrUpdated(job);
  }

  public void updateFile(File file) {
    // TODO

    notifyFileUpdated(file);
  }

  public JobReport retrieveJobReport(String jobId, String aipId)
    throws RequestNotValidException, GenericException, NotFoundException, AuthorizationDeniedException {

    StoragePath jobReportPath = ModelUtils.getJobReportStoragePath(IdUtils.getJobReportId(jobId, aipId));
    Binary binary = storage.getBinary(jobReportPath);
    JobReport ret;
    InputStream inputStream = null;
    try {
      inputStream = binary.getContent().createInputStream();
      ret = JsonUtils.getObjectFromJson(inputStream, JobReport.class);
    } catch (IOException e) {
      throw new GenericException("Error reading job report", e);
    } finally {
      IOUtils.closeQuietly(inputStream);
    }
    return ret;
  }

  public void createOrUpdateJobReport(JobReport jobReport) throws GenericException {
    // create job report in storage
    try {
      String jobReportAsJson = JsonUtils.getJsonFromObject(jobReport);
      StoragePath jobReportPath = ModelUtils.getJobReportStoragePath(jobReport.getId());
      storage.updateBinaryContent(jobReportPath, new StringContentPayload(jobReportAsJson), false, true);
    } catch (GenericException | RequestNotValidException | AuthorizationDeniedException | NotFoundException e) {
      LOGGER.error("Error creating/updating job report in storage", e);
    }

    // index it
    notifyJobReportCreatedOrUpdated(jobReport);
  }

  public PreservationMetadata createPreservationMetadata(PreservationMetadataType type, String aipId,
    String representationId, List<String> fileDirectoryPath, String fileId, ContentPayload payload, boolean notify)
      throws GenericException, NotFoundException, RequestNotValidException, AuthorizationDeniedException,
      ValidationException, AlreadyExistsException {
    String id = IdUtils.getPreservationMetadataId(type, aipId, representationId, fileDirectoryPath, fileId);
    return createPreservationMetadata(type, id, aipId, representationId, fileDirectoryPath, fileId, payload, notify);
  }

  public PreservationMetadata createPreservationMetadata(PreservationMetadataType type, String id, String aipId,
    String representationId, ContentPayload payload, boolean notify) throws GenericException, NotFoundException,
      RequestNotValidException, AuthorizationDeniedException, ValidationException, AlreadyExistsException {
    return createPreservationMetadata(type, id, aipId, representationId, null, null, payload, notify);
  }

  public PreservationMetadata createPreservationMetadata(PreservationMetadataType type, String id,
    ContentPayload payload, boolean notify) throws GenericException, NotFoundException, RequestNotValidException,
      AuthorizationDeniedException, ValidationException, AlreadyExistsException {
    return createPreservationMetadata(type, id, null, null, null, null, payload, notify);
  }

  public PreservationMetadata createPreservationMetadata(PreservationMetadataType type, String id, String aipId,
    String representationId, List<String> fileDirectoryPath, String fileId, ContentPayload payload, boolean notify)
      throws GenericException, NotFoundException, RequestNotValidException, AuthorizationDeniedException,
      ValidationException, AlreadyExistsException {
    PreservationMetadata pm = new PreservationMetadata();
    pm.setId(id);
    pm.setAipId(aipId);
    pm.setRepresentationId(representationId);
    pm.setFileDirectoryPath(fileDirectoryPath);
    pm.setFileId(fileId);
    pm.setType(type);
    StoragePath binaryPath = ModelUtils.getPreservationMetadataStoragePath(pm);
    boolean asReference = false;
    storage.createBinary(binaryPath, payload, asReference);

    if (notify) {
      notifyPreservationMetadataCreated(pm);
    }
    return pm;
  }

  public void updatePreservationMetadata(String id, PreservationMetadataType type, String aipId,
    String representationId, List<String> fileDirectoryPath, String fileId, ContentPayload payload, boolean notify)
      throws GenericException, NotFoundException, RequestNotValidException, AuthorizationDeniedException {
    PreservationMetadata pm = new PreservationMetadata();
    pm.setId(id);
    pm.setType(type);
    pm.setAipId(aipId);
    pm.setRepresentationId(representationId);
    pm.setFileDirectoryPath(fileDirectoryPath);
    pm.setFileId(fileId);

    StoragePath binaryPath = ModelUtils.getPreservationMetadataStoragePath(pm);
    storage.updateBinaryContent(binaryPath, payload, false, true);

    if (notify) {
      notifyPreservationMetadataUpdated(pm);
    }
  }

  public void deletePreservationMetadata(PreservationMetadataType type, String aipId, String representationId,
    String id, boolean notify)
      throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    PreservationMetadata pm = new PreservationMetadata();
    pm.setAipId(aipId);
    pm.setId(id);
    pm.setRepresentationId(representationId);
    pm.setType(type);

    StoragePath binaryPath = ModelUtils.getPreservationMetadataStoragePath(pm);
    storage.deleteResource(binaryPath);

    if (notify) {
      notifyPreservationMetadataDeleted(pm);
    }
  }

  public CloseableIterable<PreservationMetadata> listPreservationMetadata(String aipId, boolean includeRepresentations)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    StoragePath storagePath = ModelUtils.getAIPPreservationMetadataStoragePath(aipId);

    CloseableIterable<PreservationMetadata> aipPreservationMetadata;
    try {
      boolean recursive = true;
      CloseableIterable<Resource> resources = storage.listResourcesUnderDirectory(storagePath, recursive);
      aipPreservationMetadata = ResourceParseUtils.convert(resources, PreservationMetadata.class);
    } catch (NotFoundException e) {
      // check if AIP exists
      storage.getDirectory(ModelUtils.getAIPStoragePath(aipId));
      // if no exception was sent by above method, return empty list
      aipPreservationMetadata = new EmptyClosableIterable<PreservationMetadata>();
    }

    if (includeRepresentations) {
      List<CloseableIterable<PreservationMetadata>> list = new ArrayList<>();
      list.add(aipPreservationMetadata);
      // list from all representations
      AIP aip = retrieveAIP(aipId);
      for (Representation representation : aip.getRepresentations()) {
        CloseableIterable<PreservationMetadata> rpm = listPreservationMetadata(aipId, representation.getId());
        list.add(rpm);
      }
      return CloseableIterables.concat(list);
    } else {
      return aipPreservationMetadata;
    }

  }

  public CloseableIterable<PreservationMetadata> listPreservationMetadata(String aipId, String representationId)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    StoragePath storagePath = ModelUtils.getRepresentationPreservationMetadataStoragePath(aipId, representationId);

    boolean recursive = true;
    CloseableIterable<PreservationMetadata> ret;
    try {
      CloseableIterable<Resource> resources = storage.listResourcesUnderDirectory(storagePath, recursive);
      ret = ResourceParseUtils.convert(resources, PreservationMetadata.class);
    } catch (NotFoundException e) {
      // check if Representation exists
      storage.getDirectory(ModelUtils.getRepresentationStoragePath(aipId, representationId));
      // if no exception was sent by above method, return empty list
      ret = new EmptyClosableIterable<PreservationMetadata>();
    }

    return ret;
  }

  public CloseableIterable<OtherMetadata> listOtherMetadata(String aipId, String type, boolean includeRepresentations)
    throws RequestNotValidException, NotFoundException, GenericException, AuthorizationDeniedException {
    StoragePath storagePath = ModelUtils.getAIPOtherMetadataStoragePath(aipId, type);

    boolean recursive = true;
    CloseableIterable<OtherMetadata> aipOtherMetadata;
    try {
      CloseableIterable<Resource> resources = storage.listResourcesUnderDirectory(storagePath, recursive);
      aipOtherMetadata = ResourceParseUtils.convert(resources, OtherMetadata.class);
    } catch (NotFoundException e) {
      // check if AIP exists
      storage.getDirectory(ModelUtils.getAIPStoragePath(aipId));
      // if no exception was sent by above method, return empty list
      aipOtherMetadata = new EmptyClosableIterable<OtherMetadata>();
    }

    if (includeRepresentations) {
      List<CloseableIterable<OtherMetadata>> list = new ArrayList<>();
      list.add(aipOtherMetadata);
      // list from all representations
      AIP aip = retrieveAIP(aipId);
      for (Representation representation : aip.getRepresentations()) {
        CloseableIterable<OtherMetadata> representationOtherMetadata = listOtherMetadata(aipId, representation.getId(),
          type);
        list.add(representationOtherMetadata);
      }
      return CloseableIterables.concat(list);
    } else {
      return aipOtherMetadata;
    }

  }

  public CloseableIterable<OtherMetadata> listOtherMetadata(String aipId, String representationId, String type)
    throws NotFoundException, GenericException, AuthorizationDeniedException, RequestNotValidException {
    StoragePath storagePath = ModelUtils.getRepresentationOtherMetadataStoragePath(aipId, representationId, type);

    boolean recursive = true;
    CloseableIterable<OtherMetadata> ret;
    try {
      CloseableIterable<Resource> resources = storage.listResourcesUnderDirectory(storagePath, recursive);
      ret = ResourceParseUtils.convert(resources, OtherMetadata.class);
    } catch (NotFoundException e) {
      // check if Representation exists
      storage.getDirectory(ModelUtils.getRepresentationStoragePath(aipId, representationId));
      // if no exception was sent by above method, return empty list
      ret = new EmptyClosableIterable<OtherMetadata>();
    }
    return ret;
  }

}
