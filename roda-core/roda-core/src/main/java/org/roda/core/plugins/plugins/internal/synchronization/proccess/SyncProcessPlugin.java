package org.roda.core.plugins.plugins.internal.synchronization.proccess;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.roda.core.RodaCoreFactory;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.InvalidParameterException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.v2.LiteOptionalWithCause;
import org.roda.core.data.v2.Void;
import org.roda.core.data.v2.accessKey.AccessKey;
import org.roda.core.data.v2.accessToken.AccessToken;
import org.roda.core.data.v2.distributedInstance.LocalInstance;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.PluginParameter;
import org.roda.core.data.v2.jobs.PluginState;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.RODAProcessingLogic;
import org.roda.core.plugins.orchestrate.JobPluginInfo;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.plugins.plugins.internal.synchronization.bundle.BundleState;
import org.roda.core.plugins.plugins.internal.synchronization.bundle.SyncBundleHelper;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.fs.FSUtils;
import org.roda.core.util.RESTClientUtility;
import org.roda.core.util.ZipUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gabriel Barros <gbarros@keep.pt>
 */
public class SyncProcessPlugin extends AbstractPlugin<Void> {
  private static final Logger LOGGER = LoggerFactory.getLogger(SyncProcessPlugin.class);

  private String bundlePath = null;
  private String centralInstanceURL = null;

  private static Map<String, PluginParameter> pluginParameters = new HashMap<>();

  static {
    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_BUNDLE_PATH,
      new PluginParameter(RodaConstants.PLUGIN_PARAMS_BUNDLE_PATH, "Destination path",
        PluginParameter.PluginParameterType.STRING, "", true, false, "Destination path where bundles will be created"));

    pluginParameters.put(RodaConstants.PLUGIN_PARAMS_CENTRAL_INSTANCE_URL,
      new PluginParameter(RodaConstants.PLUGIN_PARAMS_CENTRAL_INSTANCE_URL, "Destination path",
        PluginParameter.PluginParameterType.STRING, "", true, false, "Destination path where bundles will be created"));
  }

  @Override
  public List<PluginParameter> getParameters() {
    ArrayList<PluginParameter> parameters = new ArrayList<>();
    parameters.add(pluginParameters.get(RodaConstants.PLUGIN_PARAMS_BUNDLE_PATH));
    parameters.add(pluginParameters.get(RodaConstants.PLUGIN_PARAMS_CENTRAL_INSTANCE_URL));
    return parameters;
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    super.setParameterValues(parameters);

    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_BUNDLE_PATH)) {
      bundlePath = parameters.get(RodaConstants.PLUGIN_PARAMS_BUNDLE_PATH);
    }

    if (parameters.containsKey(RodaConstants.PLUGIN_PARAMS_CENTRAL_INSTANCE_URL)) {
      centralInstanceURL = parameters.get(RodaConstants.PLUGIN_PARAMS_CENTRAL_INSTANCE_URL);
    }
  }

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "Synchronize instances";
  }

  @Override
  public String getDescription() {
    return "Send the sync bundle to the central instance";
  }

  @Override
  public RodaConstants.PreservationEventType getPreservationEventType() {
    return RodaConstants.PreservationEventType.NONE;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Send the sync bundle to the central instance";
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "Sync bundle sent successfully";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "Sync bundle not sent successfully";
  }

  @Override
  public PluginType getType() {
    return PluginType.INTERNAL;
  }

  @Override
  public List<String> getCategories() {
    return Arrays.asList(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE);
  }

  @Override
  public Plugin<Void> cloneMe() {
    return new SyncProcessPlugin();
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

  @Override
  public List<Class<Void>> getObjectClasses() {
    return Arrays.asList(Void.class);
  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    return new Report();
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage,
    List<LiteOptionalWithCause> list) throws PluginException {
    return PluginHelper.processVoids(this, new RODAProcessingLogic<Void>() {
      @Override
      public void process(IndexService index, ModelService model, StorageService storage, Report report, Job cachedJob,
        JobPluginInfo jobPluginInfo, Plugin<Void> plugin) throws PluginException {
        sendSyncBundle(index, model, report, jobPluginInfo, cachedJob);
      }
    }, index, model, storage);
  }

  private void sendSyncBundle(IndexService index, ModelService model, Report report, JobPluginInfo jobPluginInfo,
    Job job) throws PluginException {
    report.setPluginState(PluginState.SUCCESS);
    // Compress Bundle
    compressBundle(report, jobPluginInfo);

    // Send
    send(report, jobPluginInfo, model);
  }

  private void compressBundle(Report report, JobPluginInfo jobPluginInfo) throws PluginException {
    try {
      BundleState bundleStateFile = SyncBundleHelper.getBundleStateFile();
      String path = bundleStateFile.getDestinationPath();
      Path file = FSUtils.createFile(RodaCoreFactory.getSynchronizationDirectoryPath(), "artifact.zip");

      File zipFile = ZipUtility.createZIPFile(new File(file.toString()), new File(path));
      bundleStateFile.setZipFile(zipFile.getPath());
      bundleStateFile.setSyncState(BundleState.Status.PREPARED);
      SyncBundleHelper.updateBundleStateFile(bundleStateFile);

      jobPluginInfo.incrementObjectsProcessedWithSuccess();
      report.setPluginState(PluginState.SUCCESS);
    } catch (GenericException | IOException e) {
      LOGGER.error("Unable to read bundle state file", e);
      jobPluginInfo.incrementObjectsProcessedWithFailure();
      report.setPluginState(PluginState.FAILURE).setPluginDetails(e.getMessage());
      throw new PluginException("Unable to read bundle state file", e);
    }
  }

  private void send(Report report, JobPluginInfo jobPluginInfo, ModelService model) throws PluginException {
    try {
      BundleState bundleStateFile = SyncBundleHelper.getBundleStateFile();
      Path zipPath = Paths.get(bundleStateFile.getZipPath());

      LocalInstance localInstance = model.retrieveLocalInstanceConfiguration();
      AccessKey accessKey = new AccessKey(localInstance.getAccessKey());
      AccessToken accessToken = RESTClientUtility.sendPostRequest(accessKey, AccessToken.class,
        localInstance.getCentralInstanceURL(), "/api/v1/auth/token");

      int responseCode = RESTClientUtility.sendPostRequestWithCompressedFile(localInstance.getCentralInstanceURL(),
          "/api/v1/distributed_instances/sync", zipPath, accessToken);

      if(responseCode != 200) {
        jobPluginInfo.incrementObjectsProcessedWithFailure();
        report.setPluginState(PluginState.FAILURE).setPluginDetails("Server response is " + responseCode);
        bundleStateFile.setSyncState(BundleState.Status.FAILED);
      } else {
        jobPluginInfo.incrementObjectsProcessedWithSuccess();
        report.setPluginState(PluginState.SUCCESS);
        bundleStateFile.setSyncState(BundleState.Status.SENT);
      }
      SyncBundleHelper.updateBundleStateFile(bundleStateFile);
    } catch (RODAException | FileNotFoundException e) {
      LOGGER.error("Unable to send bundle to central instance", e);
      jobPluginInfo.incrementObjectsProcessedWithFailure();
      report.setPluginState(PluginState.FAILURE).setPluginDetails(e.getMessage());
      throw new PluginException("Unable to send bundle to central instance", e);
    }
  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    return new Report();
  }
}