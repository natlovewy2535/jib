/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.maven;

import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.docker.DockerClient;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.plugins.api.JibPluginExtensionException;
import com.google.cloud.tools.jib.plugins.common.BuildStepsExecutionException;
import com.google.cloud.tools.jib.plugins.common.HelpfulSuggestions;
import com.google.cloud.tools.jib.plugins.common.IncompatibleBaseImageJavaVersionException;
import com.google.cloud.tools.jib.plugins.common.InvalidAppRootException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerVolumeException;
import com.google.cloud.tools.jib.plugins.common.InvalidContainerizingModeException;
import com.google.cloud.tools.jib.plugins.common.InvalidCreationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidFilesModificationTimeException;
import com.google.cloud.tools.jib.plugins.common.InvalidWorkingDirectoryException;
import com.google.cloud.tools.jib.plugins.common.MainClassInferenceException;
import com.google.cloud.tools.jib.plugins.common.PluginConfigurationProcessor;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Builds a container image and exports to the default Docker daemon. */
@Mojo(
    name = BuildDockerMojo.GOAL_NAME,
    requiresDependencyResolution = ResolutionScope.RUNTIME_PLUS_SYSTEM,
    threadSafe = true)
public class BuildDockerMojo extends JibPluginConfiguration {

  @VisibleForTesting static final String GOAL_NAME = "dockerBuild";
  private static final String HELPFUL_SUGGESTIONS_PREFIX = "Build to Docker daemon failed";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    checkJibVersion();
    if (MojoCommon.shouldSkipJibExecution(this)) {
      return;
    }

    Path dockerExecutable = getDockerClientExecutable();
    boolean isDockerInstalled =
        dockerExecutable == null
            ? DockerClient.isDefaultDockerInstalled()
            : DockerClient.isDockerInstalled(dockerExecutable);
    if (!isDockerInstalled) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forDockerNotInstalled(HELPFUL_SUGGESTIONS_PREFIX));
    }

    MavenSettingsProxyProvider.activateHttpAndHttpsProxies(
        getSession().getSettings(), getSettingsDecrypter());

    TempDirectoryProvider tempDirectoryProvider = new TempDirectoryProvider();
    MavenProjectProperties projectProperties =
        MavenProjectProperties.getForProject(
            getProject(), getSession(), getLog(), tempDirectoryProvider);
    try {
      PluginConfigurationProcessor.createJibBuildRunnerForDockerDaemonImage(
              new MavenRawConfiguration(this),
              new MavenSettingsServerCredentials(
                  getSession().getSettings(), getSettingsDecrypter()),
              projectProperties,
              new MavenHelpfulSuggestions(HELPFUL_SUGGESTIONS_PREFIX))
          .runBuild();

    } catch (InvalidAppRootException ex) {
      throw new MojoExecutionException(
          "<container><appRoot> is not an absolute Unix-style path: " + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidContainerizingModeException ex) {
      throw new MojoExecutionException(
          "invalid value for <containerizingMode>: " + ex.getInvalidContainerizingMode(), ex);

    } catch (InvalidWorkingDirectoryException ex) {
      throw new MojoExecutionException(
          "<container><workingDirectory> is not an absolute Unix-style path: "
              + ex.getInvalidPathValue(),
          ex);

    } catch (InvalidContainerVolumeException ex) {
      throw new MojoExecutionException(
          "<container><volumes> is not an absolute Unix-style path: " + ex.getInvalidVolume(), ex);

    } catch (InvalidFilesModificationTimeException ex) {
      throw new MojoExecutionException(
          "<container><filesModificationTime> should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or special keyword \"EPOCH_PLUS_SECOND\": "
              + ex.getInvalidFilesModificationTime(),
          ex);

    } catch (InvalidCreationTimeException ex) {
      throw new MojoExecutionException(
          "<container><creationTime> should be an ISO 8601 date-time (see "
              + "DateTimeFormatter.ISO_DATE_TIME) or a special keyword (\"EPOCH\", "
              + "\"USE_CURRENT_TIMESTAMP\"): "
              + ex.getInvalidCreationTime(),
          ex);

    } catch (JibPluginExtensionException ex) {
      throw new MojoExecutionException(
          "executing a third-part Jib Plugin extension failed: " + ex.getMessage(), ex.getCause());

    } catch (IncompatibleBaseImageJavaVersionException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forIncompatibleBaseImageJavaVersionForMaven(
              ex.getBaseImageMajorJavaVersion(), ex.getProjectMajorJavaVersion()),
          ex);

    } catch (InvalidImageReferenceException ex) {
      throw new MojoExecutionException(
          HelpfulSuggestions.forInvalidImageReference(ex.getInvalidReference()), ex);

    } catch (IOException | CacheDirectoryCreationException | MainClassInferenceException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);

    } catch (BuildStepsExecutionException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex.getCause());

    } finally {
      tempDirectoryProvider.close();
      projectProperties.waitForLoggingThread();
      getLog().info("");
    }
  }
}
