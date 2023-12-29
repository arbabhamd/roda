package org.roda.wui.client.common;

import com.google.gwt.user.client.ui.FlowPanel;

import java.util.HashMap;
import java.util.List;


/**
 * @author António Lindo <alindo@keep.pt>
 */

public class ConversionProfilePanel extends FlowPanel {

  private HashMap<String, List<FlowPanel>> profilePanels;

  public ConversionProfilePanel() {
    super();
    profilePanels = new HashMap<>();
  }

  public HashMap<String, List<FlowPanel>> getProfilePanel() {
    return profilePanels;
  }

  public void setProfilePanel(HashMap<String, List<FlowPanel>> profilePanels) {
    this.profilePanels = profilePanels;
  }

  public void addProfilePanel(String profileName, FlowPanel profilePanel) {
  //  this.profilePanels.put(profileName, profilePanel);
  }

}











