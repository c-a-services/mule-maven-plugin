/*
 * Copyright 2023 Salesforce, Inc. All rights reserved.
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.tools.api.classloader.model.util;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mule.tools.api.classloader.model.util.DefaultMavenRepositoryLayoutUtils.getFormattedFileName;
import static org.mule.tools.api.classloader.model.util.DefaultMavenRepositoryLayoutUtils.getFormattedOutputDirectory;
import static org.mule.tools.api.classloader.model.util.DefaultMavenRepositoryLayoutUtils.getNormalizedVersion;

class DefaultMavenRepositoryLayoutTest {

  private static final String ARTIFACT_ID = "artifact-id";
  private static final String VERSION = "1.0.0";
  private static final String SNAPSHOT_VERSION = "SNAPSHOT";
  private static final String SCOPE = "compile";
  private static final String TYPE = "zip";
  private static final String CLASSIFIER = "classifier";
  private static final String TIMESTAMP = "20100101.101010-1";
  private static final String PREFIX_GROUP_ID = "group";
  private static final String POSFIX_GROUP_ID = "id";
  private static final String GROUP_ID = PREFIX_GROUP_ID + "." + POSFIX_GROUP_ID;
  private static final String OUTPUT_DIRECTORY =
      PREFIX_GROUP_ID + File.separator + POSFIX_GROUP_ID + File.separator + ARTIFACT_ID + File.separator + VERSION;
  private ArtifactHandler handler;
  private Artifact artifact;

  @BeforeEach
  void before() {
    handler = new DefaultArtifactHandler(TYPE);
    artifact = new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VERSION, SCOPE, TYPE, CLASSIFIER, handler);
  }

  @Test
  void getNormalizedVersionFromBaseVersionTest() {
    artifact = new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VERSION + "-" + TIMESTAMP, SCOPE, TYPE, CLASSIFIER, handler);
    String actual = getNormalizedVersion(artifact);
    assertThat(actual).as("The normalized version is different from the expected").isEqualTo(VERSION + "-" + SNAPSHOT_VERSION);
  }


  @Test
  void getNormalizedVersionFromVersionTest() {
    String expected = getNormalizedVersion(artifact);
    assertThat(expected).as("The normalized version is different from the expected").isEqualTo(VERSION);
  }

  @Test
  void getFormattedOutputDirectoryTest(@TempDir File outputFolder) {
    File actual = getFormattedOutputDirectory(outputFolder, artifact);
    File expected = new File(outputFolder, OUTPUT_DIRECTORY);
    assertThat(actual.getAbsolutePath()).as("Actual formatted output directory is not the expected")
        .isEqualTo(expected.getAbsolutePath());
  }

  @Test
  void getFormattedFileNameTest() {
    String actual = getFormattedFileName(artifact);
    String expected = ARTIFACT_ID + "-" + VERSION + "-" + CLASSIFIER + "." + TYPE;
    assertThat(actual).as("Formatted file name is different from the expected").isEqualTo(expected);
  }

  @Test
  void getFormattedFileNameWithoutClassifierTest() {
    artifact = new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VERSION, SCOPE, TYPE, null, handler);
    String actual = getFormattedFileName(artifact);
    String expected = ARTIFACT_ID + "-" + VERSION + "." + TYPE;
    assertThat(actual).as("Formatted file name is different from the expected").isEqualTo(expected);
  }

  @Test
  void getFormattedFileNameWithoutHandlerTest() {
    artifact = new DefaultArtifact(GROUP_ID, ARTIFACT_ID, VERSION, SCOPE, TYPE, CLASSIFIER, null);
    String actual = getFormattedFileName(artifact);
    String expected = ARTIFACT_ID + "-" + VERSION + "-" + CLASSIFIER + "." + TYPE;
    assertThat(actual).as("Formatted file name is different from the expected").isEqualTo(expected);
  }

}
