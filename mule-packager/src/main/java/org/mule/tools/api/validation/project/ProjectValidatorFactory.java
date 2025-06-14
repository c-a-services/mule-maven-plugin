/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.api.validation.project;

import org.mule.maven.client.api.MavenClient;
import org.mule.tools.api.classloader.model.SharedLibraryDependency;
import org.mule.tools.api.packager.ProjectInformation;
import org.mule.tools.api.packager.packaging.PackagingType;

import java.util.List;

import static org.mule.tools.api.packager.packaging.PackagingType.MULE_DOMAIN_BUNDLE;

public class ProjectValidatorFactory {

  public static AbstractProjectValidator create(ProjectInformation defaultProjectInformation,
                                                MavenClient mavenClient,
                                                List<SharedLibraryDependency> sharedLibraries,
                                                boolean strictCheck) {
    ProjectRequirement requirement = new ProjectRequirement.ProjectRequirementBuilder().withStrictCheck(strictCheck).build();
    return create(defaultProjectInformation, mavenClient, sharedLibraries, requirement);
  }


  public static AbstractProjectValidator create(ProjectInformation defaultProjectInformation,
                                                MavenClient mavenClient,
                                                List<SharedLibraryDependency> sharedLibraries,
                                                ProjectRequirement requirement) {

    if (PackagingType.fromString(defaultProjectInformation.getPackaging()).equals(MULE_DOMAIN_BUNDLE)) {
      return new DomainBundleProjectValidator(defaultProjectInformation, mavenClient);
    }

    return new MuleProjectValidator(defaultProjectInformation, sharedLibraries, requirement);
  }
}
