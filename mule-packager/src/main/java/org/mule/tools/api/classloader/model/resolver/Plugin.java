/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.api.classloader.model.resolver;

import java.util.List;

import org.apache.maven.model.Dependency;

/**
 * Mojo to modelate a plugin that will declare additional dependencies.
 *
 * @since 3.2.0
 */
public class Plugin {

  private String groupId;

  private String artifactId;

  private List<Dependency> additionalDependencies;

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public List<Dependency> getAdditionalDependencies() {
    return additionalDependencies;
  }

  public void setAdditionalDependencies(List<Dependency> dependencies) {
    this.additionalDependencies = dependencies;
  }

  @Override
  public String toString() {
    return groupId + ":" + artifactId;
  }
}
