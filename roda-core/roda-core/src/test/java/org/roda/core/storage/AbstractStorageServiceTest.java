/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Test;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.storage.fs.FSUtils;

import jersey.repackaged.com.google.common.collect.Iterables;

/**
 * 
 * @author Luis Faria <lfaria@keep.pt>
 * @author Helder Silva <hsilva@keep.pt>
 * @author Sebastien Leroux <sleroux@keep.pt>
 *
 * @param <T>
 *          the storage service implementation
 */
public abstract class AbstractStorageServiceTest<T extends StorageService> {

  /**
   * Get current instance of storage.
   * 
   * @return
   */
  protected abstract T getStorage();

  /**
   * Clean up storage, making it empty.
   */
  @After
  public abstract void cleanUp();

  @Test
  public abstract void testClassInstantiation() throws RODAException;

  @Test
  public void testListContainer() throws RODAException {

    // 1) empty list of containers
    Iterator<Container> iterator = getStorage().listContainers().iterator();
    assertFalse(iterator.hasNext());

    // 2) container list with one element (which implies creating one
    // container)
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    iterator = getStorage().listContainers().iterator();
    assertTrue(iterator.hasNext());
    Container next = iterator.next();
    assertNotNull(next);
    assertEquals(containerStoragePath, next.getStoragePath());
    assertFalse(iterator.hasNext());

    // 3) list after cleanup
    getStorage().deleteContainer(containerStoragePath);
    iterator = getStorage().listContainers().iterator();
    assertFalse(iterator.hasNext());
  }

  @Test
  public void testCreateGetDeleteContainer() throws RODAException {
    // 1) create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // 2) get created container
    Container container = getStorage().getContainer(containerStoragePath);
    assertNotNull(container);
    assertEquals(containerStoragePath, container.getStoragePath());

    // 3) create container that already exists
    try {
      getStorage().createContainer(containerStoragePath);
      fail("An exception should have been thrown while creating a container that already exists but it didn't happen!");
    } catch (AlreadyExistsException e) {
      // do nothing
    }

    // 4) delete container
    getStorage().deleteContainer(containerStoragePath);

    // 5) test delete container
    try {
      getStorage().getContainer(containerStoragePath);
      fail("An exception should have been thrown while getting a container that was deleted but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }
  }

  @Test
  public void testGetContainerThatDoesntExist() throws RODAException {
    // 1) get container that doesn't exist
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    try {
      getStorage().getContainer(containerStoragePath);
      fail("An exception should have been thrown while getting a container that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }
  }

  @Test
  public void testGetContainerThatIsActuallyADirectory() throws RODAException {
    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    final StoragePath directoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(directoryStoragePath);

    // get directory as it was a binary
    try {
      getStorage().getContainer(directoryStoragePath);
      fail(
        "An exception should have been thrown while getting a container which actually isn't a container but instead a directory but it didn't happened!");
    } catch (RequestNotValidException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testGetContainerThatIsActuallyABinary() throws RODAException {
    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    final ContentPayload payload = new RandomMockContentPayload();
    getStorage().createBinary(binaryStoragePath, payload, false);

    // get binary as it was a directory
    try {
      getStorage().getContainer(binaryStoragePath);
      fail(
        "An exception should have been thrown while getting a container which actually isn't a container but instead a binary but it didn't happened!");
    } catch (RequestNotValidException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testDeleteContainerThatDoesntExist() throws RODAException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // 1) delete container that exists
    getStorage().deleteContainer(containerStoragePath);

    // 2) delete container that no longer exists
    try {
      getStorage().deleteContainer(containerStoragePath);
      fail(
        "An exception should have been thrown while deleting a container that no longer exists but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

  }

  @Test
  public void testDeleteNonEmptyContaienr() throws RODAException {
    // Set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);
    StoragePath directoryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(directoryStoragePath);

    // 1) delete container recursively
    getStorage().deleteContainer(containerStoragePath);

    // 2) test delete container
    try {
      getStorage().getContainer(containerStoragePath);
      fail("An exception should have been thrown while getting a container that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    try {
      getStorage().getDirectory(directoryStoragePath);
      fail("An exception should have been thrown while getting a container that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

  }

  @Test
  public void testListResourcesUnderContainer() throws RODAException {
    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // 1) list empty container
    Iterable<Resource> resources = getStorage().listResourcesUnderContainer(containerStoragePath, false);
    assertNotNull(resources);
    assertNotNull(resources.iterator());
    assertFalse(resources.iterator().hasNext());

    // 2) list container with 2 resources beneath (directories)
    StoragePath directoryStoragePath1 = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(directoryStoragePath1);

    StoragePath directoryStoragePath2 = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(directoryStoragePath2);

    resources = getStorage().listResourcesUnderContainer(containerStoragePath, false);
    assertNotNull(resources);
    // Resource r1 = new DefaultDirectory(directoryStoragePath1,
    // directoryMetadata1);
    // Resource r2 = new DefaultDirectory(directoryStoragePath2,
    // directoryMetadata2);
    // assertThat(resources, containsInAnyOrder(r1, r2));
    assertThat(resources, Matchers.<Resource> iterableWithSize(2));

    // TODO test recursive listing

    // cleanup
    getStorage().deleteResource(containerStoragePath);
  }

  @Test
  public void testCreateGetDeleteDirectory() throws RODAException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // 1) create directory
    StoragePath directoryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(directoryStoragePath);

    // 2) get created directory
    Directory directory = getStorage().getDirectory(directoryStoragePath);
    assertNotNull(directory);
    assertEquals(directoryStoragePath, directory.getStoragePath());
    assertTrue(directory.isDirectory());

    // 3) create directory that already exists
    try {
      getStorage().createDirectory(directoryStoragePath);
      fail(
        "An exception should have been thrown while creating a directory that already exists but it didn't happened!");
    } catch (AlreadyExistsException e) {
      // do nothing
    }

    // 4) delete directory
    getStorage().deleteResource(directoryStoragePath);

    // 5) test deleted directory
    try {
      getStorage().getDirectory(directoryStoragePath);
      fail("An exception should have been thrown while getting a directory that was deleted but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testGetDirectoryThatDoesntExist() throws RODAException {

    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);
    StoragePath directoryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    // get directory that doesn't exist
    try {
      getStorage().getDirectory(directoryStoragePath);
      fail("An exception should have been thrown while getting a directory that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testGetDirectoryThatIsActuallyABinary() throws RODAException {
    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    final ContentPayload payload = new RandomMockContentPayload();
    getStorage().createBinary(binaryStoragePath, payload, false);

    // get binary as it was a directory
    try {
      getStorage().getDirectory(binaryStoragePath);
      fail(
        "An exception should have been thrown while getting a directory which actually isn't a directory but instead a binary but it didn't happened!");
    } catch (RequestNotValidException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testGetDirectoryThatIsActuallyAContainer() throws RODAException {
    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // get container as it was a directory
    try {
      getStorage().getDirectory(containerStoragePath);
      fail(
        "An exception should have been thrown while getting a directory which actually isn't a directory but instead a container but it didn't happened!");
    } catch (RequestNotValidException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testListResourcesUnderDirectory() throws RODAException, IOException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // create directory
    StoragePath directoryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().createDirectory(directoryStoragePath);

    // 1) list empty directory
    Iterable<Resource> resources = getStorage().listResourcesUnderDirectory(directoryStoragePath, false);
    assertNotNull(resources);
    assertFalse(resources.iterator().hasNext());

    // 2) list directory with 2 resources beneath (directories)
    final StoragePath subDirectoryStoragePath1 = StorageTestUtils
      .generateRandomResourceStoragePathUnder(directoryStoragePath);
    getStorage().createDirectory(subDirectoryStoragePath1);

    final StoragePath subDirectoryStoragePath2 = StorageTestUtils
      .generateRandomResourceStoragePathUnder(directoryStoragePath);
    getStorage().createDirectory(subDirectoryStoragePath2);

    // add grand-child to ensure it is not listed
    final StoragePath subSubDirectoryStoragePath1 = StorageTestUtils
      .generateRandomResourceStoragePathUnder(subDirectoryStoragePath1);
    getStorage().createDirectory(subSubDirectoryStoragePath1);

    resources = getStorage().listResourcesUnderDirectory(directoryStoragePath, false);
    assertNotNull(resources);

    // Resource r1 = new DefaultDirectory(subDirectoryStoragePath1,
    // subDirectoryMetadata1);
    // Resource r2 = new DefaultDirectory(subDirectoryStoragePath2,
    // subDirectoryMetadata2);
    // assertThat(resources, containsInAnyOrder(r1, r2));
    assertThat(resources, Matchers.<Resource> iterableWithSize(2));

    // TODO test recursive listing

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  public void testBinaryContent(Binary binary, ContentPayload providedPayload) throws IOException, GenericException {
    // check if content is the same
    assertTrue(IOUtils.contentEquals(providedPayload.createInputStream(), binary.getContent().createInputStream()));

    Path tempFile = Files.createTempFile("test", ".tmp");
    Files.copy(binary.getContent().createInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

    // check size in bytes
    assertEquals(Long.valueOf(Files.size(tempFile)), binary.getSizeInBytes());

    if (binary.getContentDigest() != null) {
      for (Entry<String, String> entry : binary.getContentDigest().entrySet()) {
        String digest = FSUtils.computeContentDigest(tempFile, entry.getKey());
        assertEquals(digest, entry.getValue());
      }
    }

    // delete temp file
    Files.delete(tempFile);
  }

  @Test
  public void testCreateGetDeleteBinary() throws RODAException, IOException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // 1) create binary
    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    final ContentPayload payload = new RandomMockContentPayload();
    getStorage().createBinary(binaryStoragePath, payload, false);

    // 2) get binary
    Binary binary = getStorage().getBinary(binaryStoragePath);
    assertNotNull(binary);
    assertEquals(binaryStoragePath, binary.getStoragePath());
    assertFalse(binary.isDirectory());
    assertFalse(binary.isReference());
    testBinaryContent(binary, payload);

    // 3) create binary that already exists
    try {
      getStorage().createBinary(binaryStoragePath, payload, false);
      fail("An exception should have been thrown while creating a binary that already exists but it didn't happened!");
    } catch (AlreadyExistsException e) {
      // do nothing
    }

    // 4) delete binary
    getStorage().deleteResource(binaryStoragePath);

    // 5) test deleted binary
    try {
      getStorage().getBinary(binaryStoragePath);
      fail("An exception should have been thrown while getting a binary that was deleted but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testCreateGetDeleteBinaryAsReference() throws RODAException, IOException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // 1) create binary
    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    final ContentPayload payload = new RandomMockContentPayload();

    try {
      getStorage().createBinary(binaryStoragePath, payload, true);

      // 2) get binary
      Binary binary = getStorage().getBinary(binaryStoragePath);
      assertNotNull(binary);
      assertEquals(binaryStoragePath, binary.getStoragePath());
      assertFalse(binary.isDirectory());
      assertTrue(binary.isReference());
      testBinaryContent(binary, payload);

      // 3) create binary that already exists
      try {
        getStorage().createBinary(binaryStoragePath, payload, false);
        fail(
          "An exception should have been thrown while creating a binary that already exists but it didn't happened!");
      } catch (AlreadyExistsException e) {
        // do nothing
      }

      // 4) delete binary
      getStorage().deleteResource(binaryStoragePath);

      // 5) test deleted binary
      try {
        getStorage().getBinary(binaryStoragePath);
        fail("An exception should have been thrown while getting a binary that was deleted but it didn't happened!");
      } catch (NotFoundException e) {
        // do nothing
      }
    } catch (GenericException e) {
      // catching not implemented
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testUpdateBinaryContent() throws RODAException, IOException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // create binary
    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    final ContentPayload payload = new RandomMockContentPayload();
    getStorage().createBinary(binaryStoragePath, payload, false);

    // get created binary and save content to file
    final Binary binaryCreated = getStorage().getBinary(binaryStoragePath);
    assertNotNull(binaryCreated);

    final Path original = Files.createTempFile(binaryCreated.getStoragePath().getName(), ".tmp");
    binaryCreated.getContent().writeToPath(original);

    // 1) update binary content
    final ContentPayload newPayload = new RandomMockContentPayload();
    final Binary binaryUpdated = getStorage().updateBinaryContent(binaryStoragePath, newPayload, false, false);
    assertNotNull(binaryUpdated);
    assertFalse(IOUtils.contentEquals(Files.newInputStream(original), binaryUpdated.getContent().createInputStream()));

    testBinaryContent(binaryUpdated, newPayload);

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testUpdateBinaryThatDoesntExist() throws RODAException, IOException {
    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // update binary content from binary that doesn't exist
    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);
    final ContentPayload payload = new RandomMockContentPayload();
    final boolean asReference = false;
    try {
      getStorage().updateBinaryContent(binaryStoragePath, payload, asReference, false);
      fail("An exception should have been thrown while updating a binary that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // update binary content now with createIfNotExists=true
    Binary updatedBinaryContent = getStorage().updateBinaryContent(binaryStoragePath, payload, asReference, true);
    testBinaryContent(updatedBinaryContent, payload);

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  // TODO test update binary from reference to reference
  // TODO test update binary from non-reference to reference
  // TODO test update binary from reference to non-reference

  @Test
  public void testGetBinaryThatDoesntExist() throws RODAException {

    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);
    StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    // get binary that doesn't exist
    try {
      getStorage().getBinary(binaryStoragePath);
      fail("An exception should have been thrown while getting a binary that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testGetBinaryThatIsActuallyADirectory() throws RODAException {
    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    final StoragePath directoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().createDirectory(directoryStoragePath);

    // get directory as it was a binary
    try {
      getStorage().getBinary(directoryStoragePath);
      fail(
        "An exception should have been thrown while getting a binary which actually isn't a binary but instead a directory but it didn't happened!");
    } catch (RequestNotValidException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testGetBinaryThatIsActuallyAContainer() throws RODAException {
    // set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // get container as it was a binary
    try {
      getStorage().getBinary(containerStoragePath);
      fail(
        "An exception should have been thrown while getting a binary which actually isn't a binary but instead a container but it didn't happened!");
    } catch (RequestNotValidException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testDeleteNonEmptyDirectory() throws RODAException {

    // Set up
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);
    StoragePath directoryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().createDirectory(directoryStoragePath);
    StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(directoryStoragePath);
    final ContentPayload binaryPayload = new RandomMockContentPayload();
    getStorage().createBinary(binaryStoragePath, binaryPayload, false);

    // test recursively delete directory
    getStorage().deleteResource(directoryStoragePath);

    // test get sub-resource
    try {
      getStorage().getBinary(binaryStoragePath);
      fail("An exception should have been thrown while getting a binary that doesn't exist but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // test specific cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testCopyContainerToSameStorage() throws RODAException, IOException {
    // create and populate source container
    final StoragePath sourceContainerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(sourceContainerStoragePath);

    StorageTestUtils.populate(getStorage(), sourceContainerStoragePath);

    final StoragePath targetContainerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().copy(getStorage(), sourceContainerStoragePath, targetContainerStoragePath);
    StorageTestUtils.testEntityEqualRecursively(getStorage(), sourceContainerStoragePath, getStorage(),
      targetContainerStoragePath);

    // cleanup
    getStorage().deleteContainer(sourceContainerStoragePath);
    getStorage().deleteContainer(targetContainerStoragePath);
  }

  @Test
  public void testCopyDirectoryToSameStorage() throws RODAException, IOException {
    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // create and populate source directory
    final StoragePath sourceDirectoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(sourceDirectoryStoragePath);

    StorageTestUtils.populate(getStorage(), sourceDirectoryStoragePath);

    final StoragePath targetDirectoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().copy(getStorage(), sourceDirectoryStoragePath, targetDirectoryStoragePath);
    StorageTestUtils.testEntityEqualRecursively(getStorage(), sourceDirectoryStoragePath, getStorage(),
      targetDirectoryStoragePath);

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testCopyBinaryToSameStorage() throws RODAException, IOException {
    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // create binary
    final StoragePath sourceBinaryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    final ContentPayload sourcePayload = new RandomMockContentPayload();
    getStorage().createBinary(sourceBinaryStoragePath, sourcePayload, false);

    final StoragePath targetBinaryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().copy(getStorage(), sourceBinaryStoragePath, targetBinaryStoragePath);

    final Binary sourceBinary = getStorage().getBinary(sourceBinaryStoragePath);
    final Binary targetBinary = getStorage().getBinary(targetBinaryStoragePath);
    assertEquals(sourceBinary.isDirectory(), targetBinary.isDirectory());
    assertEquals(sourceBinary.getContentDigest(), targetBinary.getContentDigest());
    assertEquals(sourceBinary.getSizeInBytes(), targetBinary.getSizeInBytes());
    assertEquals(sourceBinary.isReference(), targetBinary.isReference());
    assertTrue(IOUtils.contentEquals(sourceBinary.getContent().createInputStream(),
      targetBinary.getContent().createInputStream()));

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  // TODO test copy from different storage

  @Test
  public void testMoveContainerToSameStorage() throws RODAException, IOException {
    // create and populate source container
    final StoragePath sourceContainerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(sourceContainerStoragePath);

    StorageTestUtils.populate(getStorage(), sourceContainerStoragePath);

    // copy for comparison test
    final StoragePath copyContainerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().copy(getStorage(), sourceContainerStoragePath, copyContainerStoragePath);

    // move
    final StoragePath targetContainerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().move(getStorage(), sourceContainerStoragePath, targetContainerStoragePath);

    // check with copy
    StorageTestUtils.testEntityEqualRecursively(getStorage(), copyContainerStoragePath, getStorage(),
      targetContainerStoragePath);

    // test source does not exist
    try {
      getStorage().getContainer(sourceContainerStoragePath);
      fail("An exception should have been thrown while getting a container that was moved but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(copyContainerStoragePath);
    getStorage().deleteContainer(targetContainerStoragePath);
  }

  @Test
  public void testMoveDirectoryToSameStorage() throws RODAException, IOException {
    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();

    getStorage().createContainer(containerStoragePath);

    // create and populate source directory
    final StoragePath sourceDirectoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().createDirectory(sourceDirectoryStoragePath);

    StorageTestUtils.populate(getStorage(), sourceDirectoryStoragePath);

    // copy for comparison test
    final StoragePath copyDirectoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);
    getStorage().copy(getStorage(), sourceDirectoryStoragePath, copyDirectoryStoragePath);

    // move
    final StoragePath targetDirectoryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().move(getStorage(), sourceDirectoryStoragePath, targetDirectoryStoragePath);

    // check with copy
    StorageTestUtils.testEntityEqualRecursively(getStorage(), copyDirectoryStoragePath, getStorage(),
      targetDirectoryStoragePath);

    // test source does not exist
    try {
      getStorage().getDirectory(sourceDirectoryStoragePath);
      fail("An exception should have been thrown while getting a directory that was moved but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  @Test
  public void testMoveBinaryToSameStorage() throws RODAException, IOException {
    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // create binary
    final StoragePath sourceBinaryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    final ContentPayload sourcePayload = new RandomMockContentPayload();
    getStorage().createBinary(sourceBinaryStoragePath, sourcePayload, false);

    // copy for comparison test
    final StoragePath copyBinaryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().copy(getStorage(), sourceBinaryStoragePath, copyBinaryStoragePath);

    // move
    final StoragePath targetBinaryStoragePath = StorageTestUtils
      .generateRandomResourceStoragePathUnder(containerStoragePath);

    getStorage().move(getStorage(), sourceBinaryStoragePath, targetBinaryStoragePath);

    // check with copy
    final Binary copyBinary = getStorage().getBinary(copyBinaryStoragePath);
    final Binary targetBinary = getStorage().getBinary(targetBinaryStoragePath);
    assertEquals(copyBinary.isDirectory(), targetBinary.isDirectory());
    assertEquals(copyBinary.getContentDigest(), targetBinary.getContentDigest());
    assertEquals(copyBinary.getSizeInBytes(), targetBinary.getSizeInBytes());
    assertEquals(copyBinary.isReference(), targetBinary.isReference());
    assertTrue(IOUtils.contentEquals(copyBinary.getContent().createInputStream(),
      targetBinary.getContent().createInputStream()));

    // test source does not exist
    try {
      getStorage().getBinary(sourceBinaryStoragePath);
      fail("An exception should have been thrown while getting a binary that was moved but it didn't happened!");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

  // TODO test move from different storage

  @Test
  public void testBinaryVersions() throws RODAException, IOException {

    // create container
    final StoragePath containerStoragePath = StorageTestUtils.generateRandomContainerStoragePath();
    getStorage().createContainer(containerStoragePath);

    // 1) create binary
    final StoragePath binaryStoragePath = StorageTestUtils.generateRandomResourceStoragePathUnder(containerStoragePath);

    final ContentPayload payload1 = new RandomMockContentPayload();
    getStorage().createBinary(binaryStoragePath, payload1, false);

    // 2) create binary version
    String message1 = "v1";
    BinaryVersion v1 = getStorage().createBinaryVersion(binaryStoragePath, message1);

    // 3) update binary
    final ContentPayload payload2 = new RandomMockContentPayload();
    getStorage().updateBinaryContent(binaryStoragePath, payload2, false, false);

    // 4) create binary version 2
    String message2 = "v2";
    BinaryVersion v2 = getStorage().createBinaryVersion(binaryStoragePath, message2);

    // 5) create a version with a message that already exists
    BinaryVersion v3 = getStorage().createBinaryVersion(binaryStoragePath, message1);

    // 6) list binary versions
    CloseableIterable<BinaryVersion> binaryVersions = getStorage().listBinaryVersions(binaryStoragePath);
    List<BinaryVersion> reusableBinaryVersions = new ArrayList<>();
    Iterables.addAll(reusableBinaryVersions, binaryVersions);

    assertEquals(3, reusableBinaryVersions.size());

    // 7) get binary version
    BinaryVersion binaryVersion1 = getStorage().getBinaryVersion(binaryStoragePath, v1.getId());
    assertEquals(message1, binaryVersion1.getMessage());
    assertNotNull(binaryVersion1.getCreatedDate());
    assertTrue(
      IOUtils.contentEquals(payload1.createInputStream(), binaryVersion1.getBinary().getContent().createInputStream()));

    // 8) revert to previous version
    getStorage().revertBinaryVersion(binaryStoragePath, v1.getId());

    Binary binary = getStorage().getBinary(binaryStoragePath);
    testBinaryContent(binary, payload1);

    // 9) delete binary version
    getStorage().deleteBinaryVersion(binaryStoragePath, v1.getId());

    try {
      getStorage().getBinaryVersion(binaryStoragePath, v1.getId());
      fail("Should have thrown NotFoundException");
    } catch (NotFoundException e) {
      // do nothing
    }

    // 10) delete binary and all its history
    getStorage().deleteResource(binaryStoragePath);

    try {
      getStorage().getBinaryVersion(binaryStoragePath, v1.getId());
      fail("Should have thrown NotFoundException");
    } catch (NotFoundException e) {
      // do nothing
    }

    // cleanup
    getStorage().deleteContainer(containerStoragePath);
  }

}
