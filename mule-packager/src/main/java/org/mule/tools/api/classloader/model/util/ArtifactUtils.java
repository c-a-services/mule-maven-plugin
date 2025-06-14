/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.api.classloader.model.util;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import static org.mule.maven.client.internal.MuleMavenClient.MULE_PLUGIN_CLASSIFIER;
import static org.mule.runtime.api.util.Preconditions.checkState;
import static org.mule.tools.api.classloader.Constants.ARTIFACT_ID;
import static org.mule.tools.api.classloader.Constants.GROUP_ID;
import static org.mule.tools.api.classloader.Constants.MULE_MAVEN_PLUGIN_ARTIFACT_ID;
import static org.mule.tools.api.classloader.Constants.MULE_MAVEN_PLUGIN_GROUP_ID;
import static org.mule.tools.api.classloader.Constants.SHARED_LIBRARIES_FIELD;
import static org.mule.tools.api.classloader.Constants.SHARED_LIBRARY_FIELD;
import static org.mule.tools.api.packager.packaging.Classifier.MULE_DOMAIN;
import static org.mule.tools.api.packager.packaging.Classifier.MULE_PLUGIN;

import org.mule.maven.pom.parser.api.model.BundleDependency;
import org.mule.maven.pom.parser.api.model.BundleDescriptor;
import org.mule.tools.api.classloader.model.ApplicationGAVModel;
import org.mule.tools.api.classloader.model.Artifact;
import org.mule.tools.api.classloader.model.ArtifactCoordinates;
import org.mule.tools.api.util.FileJarExplorer;
import org.mule.tools.api.util.JarInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * ArtifactUtils presents helper methods to convert artifact related classes and recognize mule plugin artifacts.
 */
public class ArtifactUtils {

  private static final String PACKAGE_TYPE = "jar";
  private static final String PROVIDED = "provided";
  private static final URI EMPTY_RESOURCE = URI.create("");
  private static final String POM_TYPE = "pom";

  /**
   * Convert a {@link BundleDescriptor} instance to {@link ArtifactCoordinates}.
   *
   * @param bundleDescriptor the bundle descriptor to be converted.
   * @return the corresponding artifact coordinates with normalized version.
   */
  public static ArtifactCoordinates toArtifactCoordinates(BundleDescriptor bundleDescriptor) {
    ArtifactCoordinates artifactCoordinates =
        new ArtifactCoordinates(bundleDescriptor.getGroupId(), bundleDescriptor.getArtifactId(),
                                bundleDescriptor.getBaseVersion(),
                                bundleDescriptor.getType(), bundleDescriptor.getClassifier().orElse(null));
    return artifactCoordinates;
  }

  /**
   * Convert a {@link BundleDependency} instance to {@link Artifact}.
   *
   * @param bundleDependency the bundle dependency to be converted.
   * @return the corresponding artifact with normalized version.
   */
  public static Artifact toArtifact(BundleDependency bundleDependency) {
    ArtifactCoordinates artifactCoordinates = toArtifactCoordinates(bundleDependency.getDescriptor());
    return new Artifact(artifactCoordinates, bundleDependency.getBundleUri());
  }

  /**
   * Converts a {@link List<BundleDependency>} to a {@link List<Artifact>}.
   *
   * @param dependencies the bundle dependency list to be converted.
   * @return the corresponding artifact list, each one with normalized version.
   */
  public static List<Artifact> toArtifacts(Collection<BundleDependency> dependencies) {
    return dependencies.stream().map(ArtifactUtils::toArtifact).collect(toList());
  }

  /**
   * Checks if a {@link Artifact} instance represents a mule-plugin.
   *
   * @param artifact the artifact to be checked.
   * @return true if the artifact is a mule-plugin, false otherwise.
   */
  public static boolean isValidMulePlugin(Artifact artifact) {
    ArtifactCoordinates pluginCoordinates = artifact.getArtifactCoordinates();
    Optional<String> pluginClassifier = Optional.ofNullable(pluginCoordinates.getClassifier());
    return pluginClassifier.isPresent() && MULE_PLUGIN_CLASSIFIER.equals(pluginClassifier.get());
  }

  /**
   * Converts a {@link ArtifactCoordinates} instance to a {@link BundleDescriptor} instance.
   *
   * @param artifactCoordinates the artifact coordinates to be converted.
   * @return the corresponding {@link BundleDescriptor} instance.
   */
  public static BundleDescriptor toBundleDescriptor(ArtifactCoordinates artifactCoordinates) {
    return new BundleDescriptor.Builder()
        .setGroupId(artifactCoordinates.getGroupId())
        .setArtifactId(artifactCoordinates.getArtifactId())
        .setVersion(artifactCoordinates.getVersion())
        .setBaseVersion(artifactCoordinates.getVersion())
        .setClassifier(artifactCoordinates.getClassifier())
        .setType(artifactCoordinates.getType()).build();
  }

  /**
   * Converts a {@link Dependency} instance to a {@link BundleDescriptor} instance.
   *
   * @return the corresponding {@link BundleDescriptor} instance.
   * @since 3.2.0
   */
  public static BundleDescriptor toBundleDescriptor(Dependency dependency) {
    return new BundleDescriptor.Builder()
        .setGroupId(dependency.getGroupId())
        .setArtifactId(dependency.getArtifactId())
        .setVersion(dependency.getVersion())
        .setBaseVersion(dependency.getVersion())
        .setClassifier(dependency.getClassifier())
        .setType(dependency.getType()).build();
  }

  public static List<Artifact> toApplicationModelArtifacts(List<BundleDependency> appDependencies) {
    List<Artifact> dependencies = toArtifacts(appDependencies);
    dependencies.forEach(ArtifactUtils::updateScopeIfDomain);
    return dependencies;
  }

  public static List<Artifact> updatePackagesResources(List<Artifact> artifacts) {
    return artifacts.stream().map(artifact -> updatePackagesResources(artifact)).collect(toList());
  }

  public static Artifact updatePackagesResources(Artifact artifact) {
    if (MULE_PLUGIN.equals(artifact.getArtifactCoordinates().getClassifier())
        || artifact.getUri() == null
        // mule-domain are set with a "" URI
        || isBlank(artifact.getUri().getPath())) {
      return artifact;
    }
    JarInfo jarInfo = new FileJarExplorer().explore(artifact.getUri());
    artifact.setPackages(jarInfo.getPackages().toArray(new String[jarInfo.getPackages().size()]));
    artifact.setResources(jarInfo.getResources().toArray(new String[jarInfo.getResources().size()]));
    return artifact;
  }

  public static List<Artifact> updateArtifactsSharedState(List<BundleDependency> appDependencies, List<Artifact> artifacts,
                                                          Model pomModel, List<String> activeProfiles) {
    List<BuildBase> builds = new ArrayList<BuildBase>();
    if (pomModel.getBuild() != null) {
      builds.add(pomModel.getBuild());
    }
    pomModel.getProfiles().forEach(p -> {
      if (activeProfiles.contains(p.getId()))
        builds.add(p.getBuild());
    });
    if (!builds.isEmpty()) {
      List<Plugin> plugins = new ArrayList<Plugin>();
      builds.forEach(b -> {
        if (b != null) {
          plugins.addAll(b.getPlugins());
        }
      });
      if (!plugins.isEmpty()) {
        Optional<Plugin> muleMavenPluginOptional = plugins.stream()
            .filter(plugin -> plugin.getGroupId().equalsIgnoreCase(MULE_MAVEN_PLUGIN_GROUP_ID) &&
                plugin.getArtifactId().equalsIgnoreCase(MULE_MAVEN_PLUGIN_ARTIFACT_ID))
            .findAny();
        muleMavenPluginOptional.ifPresent(plugin -> {
          Object configuration = plugin.getConfiguration();
          if (configuration != null) {
            Xpp3Dom sharedLibrariesDom = ((Xpp3Dom) configuration).getChild(SHARED_LIBRARIES_FIELD);
            if (sharedLibrariesDom != null) {
              Xpp3Dom[] sharedLibraries = sharedLibrariesDom.getChildren(SHARED_LIBRARY_FIELD);
              if (sharedLibraries != null) {
                for (Xpp3Dom sharedLibrary : sharedLibraries) {
                  String groupId = getAttribute(sharedLibrary, GROUP_ID);
                  String artifactId = getAttribute(sharedLibrary, ARTIFACT_ID);
                  findAndExportSharedLibrary(groupId, artifactId, artifacts, appDependencies);
                }
              }
            }
          }
        });
      }
    }
    return artifacts;
  }

  private static void findAndExportSharedLibrary(String sharedLibraryGroupId, String sharedLibraryArtifactId,
                                                 List<Artifact> artifacts, List<BundleDependency> appDependencies) {
    appDependencies.stream()
        .forEach(bundleDependency -> {
          if (bundleDependency.getDescriptor().getGroupId().equals(sharedLibraryGroupId) &&
              bundleDependency.getDescriptor().getArtifactId().equals(sharedLibraryArtifactId)) {
            setArtifactTransitiveDependenciesAsShared(artifacts, bundleDependency);
          }
        });

  }

  private static void setArtifactTransitiveDependenciesAsShared(List<Artifact> artifacts, BundleDependency bundleDependency) {
    setArtifactAsShared(bundleDependency.getDescriptor().getGroupId(), bundleDependency.getDescriptor().getArtifactId(),
                        artifacts);
    bundleDependency.getTransitiveDependencies()
        .stream()
        .forEach(transitiveDependency -> setArtifactTransitiveDependenciesAsShared(artifacts, transitiveDependency));
  }

  private static void setArtifactAsShared(String sharedLibraryGroupId, String sharedLibraryArtifactId, List<Artifact> artifacts) {
    artifacts.stream()
        .forEach(artifact -> {
          if (artifact.getArtifactCoordinates().getGroupId().equals(sharedLibraryGroupId) &&
              artifact.getArtifactCoordinates().getArtifactId().equals(sharedLibraryArtifactId)) {
            artifact.setShared(true);

          }
        });
  }


  protected static String getAttribute(org.codehaus.plexus.util.xml.Xpp3Dom tag, String attributeName) {
    org.codehaus.plexus.util.xml.Xpp3Dom attributeDom = tag.getChild(attributeName);
    checkState(attributeDom != null, format("'%s' element not declared at '%s' in the pom file",
                                            attributeName, tag.toString()));
    String attributeValue = attributeDom.getValue().trim();
    checkState(!isEmpty(attributeValue),
               format("'%s' was defined but has an empty value at '%s' declared in the pom file",
                      attributeName, tag.toString()));
    return attributeValue;

  }

  public static void updateScopeIfDomain(Artifact artifact) {
    String classifier = artifact.getArtifactCoordinates().getClassifier();
    if (StringUtils.equals(classifier, MULE_DOMAIN.toString())) {
      artifact.getArtifactCoordinates().setScope(PROVIDED);
      artifact.setUri(EMPTY_RESOURCE);
    }
  }

  public static ArtifactCoordinates getApplicationArtifactCoordinates(Model pomModel, ApplicationGAVModel appGAVModel) {
    ArtifactCoordinates appCoordinates = toArtifactCoordinates(getPomProjectBundleDescriptor(appGAVModel));
    appCoordinates.setType(PACKAGE_TYPE);
    appCoordinates.setClassifier(pomModel.getPackaging());
    return appCoordinates;
  }

  public static BundleDescriptor getPomProjectBundleDescriptor(ApplicationGAVModel appGAVModel) {
    return getBundleDescriptor(appGAVModel);
  }


  public static BundleDescriptor getBundleDescriptor(ApplicationGAVModel appGAVModel) {
    return new BundleDescriptor.Builder()
        .setGroupId(appGAVModel.getGroupId())
        .setArtifactId(appGAVModel.getArtifactId())
        .setVersion(appGAVModel.getVersion())
        .setBaseVersion(appGAVModel.getVersion())
        .setType(POM_TYPE)
        .build();
  }
}
