package org.roda.core.plugins.plugins.internal.disposal;

import java.util.Collections;
import java.util.List;

import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.LiteOptionalWithCause;
import org.roda.core.data.v2.ip.disposal.DisposalConfirmationMetadata;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.RODAObjectProcessingLogic;
import org.roda.core.plugins.orchestrate.JobPluginInfo;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Miguel Guimarães <mguimaraes@keep.pt>
 */
public class DestroyRecordsInDisposalConfirmationPlugin extends AbstractPlugin<DisposalConfirmationMetadata> {
  private static final Logger LOGGER = LoggerFactory.getLogger(DestroyRecordsInDisposalConfirmationPlugin.class);

  @Override
  public String getVersionImpl() {
    return "1.0";
  }

  public static String getStaticName() {
    return "Destroy records under disposal confirmation report";
  }

  @Override
  public String getName() {
    return getStaticName();
  }

  public static String getStaticDescription() {
    return "";
  }

  @Override
  public String getDescription() {
    return getStaticDescription();
  }

  @Override
  public RodaConstants.PreservationEventType getPreservationEventType() {
    return RodaConstants.PreservationEventType.DELETION;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Create disposal confirmation report";
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "";
  }

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage,
    List<LiteOptionalWithCause> liteList) throws PluginException {
    return PluginHelper.processObjects(this, new RODAObjectProcessingLogic<DisposalConfirmationMetadata>() {
      @Override
      public void process(IndexService index, ModelService model, StorageService storage, Report report, Job cachedJob,
        JobPluginInfo jobPluginInfo, Plugin<DisposalConfirmationMetadata> plugin, DisposalConfirmationMetadata object) {
          processDisposalConfirmation();
      }
    }, index, model, storage, liteList);
  }

  private void processDisposalConfirmation() {
    // Get disposal confirmation AIPs

    // Iterate over the AIP
    // - Make a copy to disposal bin
    // - Apply a stylesheet over the aip metadata
    // - remove the AIP metadata according to configurations
    // - Mark the AIP as residual
  }

  private void processAIP() {

  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public List<Class<DisposalConfirmationMetadata>> getObjectClasses() {
    return Collections.singletonList(DisposalConfirmationMetadata.class);
  }

  @Override
  public List<String> getCategories() {
    return Collections.singletonList(RodaConstants.PLUGIN_CATEGORY_NOT_LISTABLE);
  }

  @Override
  public Plugin<DisposalConfirmationMetadata> cloneMe() {
    return new DestroyRecordsInDisposalConfirmationPlugin();
  }

  @Override
  public PluginType getType() {
    return PluginType.INTERNAL;
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }
}