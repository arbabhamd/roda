/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.data.v2.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author António Lindo <alindo@keep.pt>
 */
public class UserProfile implements Serializable {
  private static final long serialVersionUID = -117396300862413045L;
  private String i18nProperty;
  private String profile;
  private String description;
  private Map<String, String> options;
  private Map<String, List<String>> controlledVocabulary;

  public UserProfile() {

    options = new HashMap<>();
    controlledVocabulary = new HashMap<>();

  }

  public void setI18nProperty(String i18nProperty) {
    this.i18nProperty = i18nProperty;
  }

  public void setProfile(String profile) {
    this.profile = profile;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setOptions(Map<String, String> options) {
    this.options = options;
  }

  public String getI18nProperty() {
    return i18nProperty;
  }

  public String getProfile() {
    return profile;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, String> getOptions() {
    return options;
  }

  public Map<String, List<String>> getControlledVocabulary() {
    return controlledVocabulary;
  }

  public void setControlledVocabulary(Map<String, List<String>> controlledVocabulary) {
    this.controlledVocabulary = controlledVocabulary;
  }
}
