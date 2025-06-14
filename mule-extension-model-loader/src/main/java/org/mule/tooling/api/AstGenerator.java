/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tooling.api;

import static org.mule.runtime.module.artifact.api.descriptor.ArtifactPluginDescriptor.MULE_PLUGIN_CLASSIFIER;

import org.mule.maven.client.api.MavenClient;
import org.mule.maven.pom.parser.api.model.BundleDescriptor;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.util.Pair;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ArtifactType;
import org.mule.runtime.ast.api.serialization.ArtifactAstSerializerProvider;
import org.mule.runtime.ast.api.util.MuleAstUtils;
import org.mule.runtime.ast.api.validation.Validation.Level;
import org.mule.runtime.ast.api.validation.ValidationResult;
import org.mule.runtime.ast.api.validation.ValidationResultItem;
import org.mule.runtime.ast.api.xml.AstXmlParser;
import org.mule.runtime.ast.internal.serialization.ArtifactAstSerializerFactory;
import org.mule.runtime.config.api.properties.ConfigurationPropertiesHierarchyBuilder;
import org.mule.runtime.config.api.properties.ConfigurationPropertiesResolver;
import org.mule.tooling.internal.PluginResources;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

public class AstGenerator {

  AstXmlParser xmlParser;

  public AstGenerator(MavenClient mavenClient, String runtimeVersion,
                      Set<Artifact> allDependencies, Path workingDir, ClassRealm classRealm,
                      List<Dependency> directDependencies) {
    this(mavenClient, runtimeVersion, allDependencies, workingDir, classRealm, directDependencies, true);
  }

  public AstGenerator(MavenClient mavenClient, String runtimeVersion,
                      Set<Artifact> allDependencies, Path workingDir, ClassRealm classRealm,
                      List<Dependency> directDependencies, Boolean asApplication) {
    ClassLoader classloader = AstGenerator.class.getClassLoader();
    ExtensionModelLoader loader = ExtensionModelLoaderFactory.createLoader(mavenClient, workingDir, classloader, runtimeVersion);
    Set<ExtensionModel> extensionModels = new HashSet<>();
    ArrayList<URL> dependenciesURL = new ArrayList<>();
    for (Dependency dependency : directDependencies) {
      removeExtModelIfExists(extensionModels, dependency);
      processDependency(dependency, classloader, mavenClient, runtimeVersion, workingDir, extensionModels, dependenciesURL,
                        loader);
    }
    allDependencies.stream().map(this::createDependency)
        .filter(dependency -> !directDependencies.contains(dependency))
        .forEach(dependency -> processDependency(dependency, classloader, mavenClient, runtimeVersion, workingDir,
                                                 extensionModels, dependenciesURL, loader));
    dependenciesURL.forEach(url -> {
      try {
        classRealm.addURL(url);
        // this seldom can throw ArtifactResolutionException and we should not stop the build for that
      } catch (Exception e1) {
        e1.printStackTrace();
      }
    });
    Set<ExtensionModel> runtimeExtensionModels = loader.getRuntimeExtensionModels();
    extensionModels.addAll(runtimeExtensionModels);
    AstXmlParser.Builder builder = new AstXmlParser.Builder();
    if (!asApplication) {
      builder.withArtifactType(ArtifactType.DOMAIN);
    }
    builder.withExtensionModels(extensionModels);

    // Get a more specific error, so we can discriminate unresolved imports because the importFile has a property of because the
    // file does not exist.
    ConfigurationPropertiesResolver emptyPropertyResolver = new ConfigurationPropertiesHierarchyBuilder().build();
    builder.withPropertyResolver(propertyKey -> (String) emptyPropertyResolver.resolveValue(propertyKey));

    xmlParser = builder.build();
  }


  private void removeExtModelIfExists(Set<ExtensionModel> extensionModels, Dependency dependency) {
    extensionModels.removeIf(extension -> extension.getArtifactCoordinates()
        .map(coordinates -> dependency.getArtifactId().equals(coordinates.getArtifactId())
            && dependency.getGroupId().equals(coordinates.getGroupId()))
        .orElse(false));
  }

  private Dependency createDependency(Artifact artifact) {
    Dependency dependency = new Dependency();
    dependency.setArtifactId(artifact.getArtifactId());
    dependency.setGroupId(artifact.getGroupId());
    dependency.setVersion(artifact.getVersion());
    if (artifact.getClassifier() != null) {
      dependency.setClassifier(artifact.getClassifier());
    }
    if (artifact.getType() != null) {
      dependency.setType(artifact.getType());
    }
    return dependency;
  }


  private boolean resourceInJar(URL resource) {
    return (resource.toExternalForm().startsWith("jar:") && resource.toExternalForm().contains("!/"));
  }


  public static BundleDescriptor toBundleDescriptor(Dependency dependency) {
    return new BundleDescriptor.Builder()
        .setGroupId(dependency.getGroupId())
        .setArtifactId(dependency.getArtifactId())
        .setVersion(dependency.getVersion())
        .setBaseVersion(dependency.getVersion())
        .setClassifier(dependency.getClassifier())
        .setType(dependency.getType()).build();
  }

  public ArtifactAst generateAST(List<String> configs, Path configsPath) throws FileNotFoundException {
    List<Pair<String, InputStream>> appXmlConfigInputStreams = new ArrayList<>();
    for (String config : configs) {
      appXmlConfigInputStreams.add(new Pair(config, new FileInputStream(configsPath.resolve(config).toFile())));
    }
    return appXmlConfigInputStreams.isEmpty() ? null : xmlParser.parse(appXmlConfigInputStreams);
  }

  public void processDependency(Dependency dependency, ClassLoader classloader, MavenClient mavenClient, String runtimeVersion,
                                Path workingDir, Set<ExtensionModel> extensionModels, ArrayList<URL> dependenciesURL,
                                ExtensionModelLoader loader) {
    if (dependency.getClassifier() != null && dependency.getClassifier().equals(MULE_PLUGIN_CLASSIFIER)) {
      PluginResources extensionInformation = loader.load(toBundleDescriptor(dependency));
      extensionModels.addAll(extensionInformation.getExtensionModels());
      extensionInformation.getExportedResources().forEach(resource -> {
        try {
          if (resourceInJar(resource)) {
            dependenciesURL.add(new URL(resource.toExternalForm().split("!/")[0] + "!/"));
          }
        } catch (MalformedURLException e) {
          e.printStackTrace();
        }
      });
    } else {
      if (("jar").equals(dependency.getType())) {
        try {
          dependenciesURL.add(mavenClient.resolveBundleDescriptor(toBundleDescriptor(dependency)).getBundleUri().toURL());
          // this seldom can throw ArtifactResolutionException and we should not stop the build for that
        } catch (Exception e1) {
          e1.printStackTrace();
        }
      }
    }
  }

  public AstValidatonResult validateAST(ArtifactAst artifactAst) throws ConfigurationException {
    // Do not fail for unresolvable properties, since those are expected at deployment time, not packaging time.
    artifactAst.updatePropertiesResolver(propertyKey -> propertyKey);

    ValidationResult result = MuleAstUtils.validatorBuilder()
        .ignoreParamsWithProperties(true)
        .build().validate(artifactAst);
    List<ValidationResultItem> dynamicStructureErrors = new ArrayList<>();
    List<ValidationResultItem> errors = new ArrayList<>();
    List<ValidationResultItem> warnings = new ArrayList<>();
    result.getItems().forEach(v -> {
      if (v.getValidation().getLevel().equals(Level.ERROR)) {
        if (v.causedByDynamicArtifact()) {
          dynamicStructureErrors.add(v);
        } else {
          errors.add(v);
        }
      } else {
        warnings.add(v);
      }
    });
    if (errors.size() > 0) {
      throw new ConfigurationException(errors.get(0).getMessage());
    }
    return new AstValidatonResult(errors, warnings, dynamicStructureErrors);
  }

  public static InputStream serialize(ArtifactAst artifactAst) {
    return new ArtifactAstSerializerProvider().getSerializer(ArtifactAstSerializerFactory.JSON, "1.0").serialize(artifactAst);
  }
}
