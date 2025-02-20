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

package com.google.cloud.tools.jib.gradle;

import com.google.cloud.tools.jib.ProjectInfo;
import com.google.cloud.tools.jib.gradle.skaffold.CheckJibVersionTask;
import com.google.cloud.tools.jib.gradle.skaffold.FilesTaskV2;
import com.google.cloud.tools.jib.gradle.skaffold.InitTask;
import com.google.cloud.tools.jib.gradle.skaffold.SyncMapTask;
import com.google.cloud.tools.jib.plugins.common.VersionChecker;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.util.GradleVersion;

public class JibPlugin implements Plugin<Project> {

  @VisibleForTesting static final GradleVersion GRADLE_MIN_VERSION = GradleVersion.version("4.9");

  public static final String JIB_EXTENSION_NAME = "jib";
  public static final String BUILD_IMAGE_TASK_NAME = "jib";
  public static final String BUILD_TAR_TASK_NAME = "jibBuildTar";
  public static final String BUILD_DOCKER_TASK_NAME = "jibDockerBuild";
  public static final String SKAFFOLD_FILES_TASK_V2_NAME = "_jibSkaffoldFilesV2";
  public static final String SKAFFOLD_INIT_TASK_NAME = "_jibSkaffoldInit";
  public static final String SKAFFOLD_SYNC_MAP_TASK_NAME = "_jibSkaffoldSyncMap";
  public static final String SKAFFOLD_CHECK_REQUIRED_VERSION_TASK_NAME =
      "_skaffoldFailIfJibOutOfDate";

  public static final String REQUIRED_VERSION_PROPERTY_NAME = "jib.requiredVersion";

  /**
   * Collects all project dependencies of the style "compile project(':mylib')" for any kind of
   * configuration [compile, runtime, etc]. It potentially will collect common test libraries in
   * configs like [test, integrationTest, etc], but it's either that or filter based on a
   * configuration containing the word "test" which feels dangerous.
   *
   * @param project this project we are containerizing
   * @return a list of projects that this project depends on.
   */
  @VisibleForTesting
  static List<Project> getProjectDependencies(Project project) {
    return project
        .getConfigurations()
        .stream()
        .map(Configuration::getDependencies)
        .flatMap(DependencySet::stream)
        .filter(ProjectDependency.class::isInstance)
        .map(ProjectDependency.class::cast)
        .map(ProjectDependency::getDependencyProject)
        .collect(Collectors.toList());
  }

  private static void checkGradleVersion() {
    if (GRADLE_MIN_VERSION.compareTo(GradleVersion.current()) > 0) {
      throw new GradleException(
          "Detected "
              + GradleVersion.current()
              + ", but jib requires "
              + GRADLE_MIN_VERSION
              + " or higher. You can upgrade by running 'gradle wrapper --gradle-version="
              + GRADLE_MIN_VERSION.getVersion()
              + "'.");
    }
  }

  /** Check the Jib version matches the required version (if specified). */
  private static void checkJibVersion() {
    // todo: should retrieve from project properties?
    String requiredVersion = System.getProperty(REQUIRED_VERSION_PROPERTY_NAME);
    if (requiredVersion == null) {
      return;
    }
    String actualVersion = ProjectInfo.VERSION;
    if (actualVersion == null) {
      throw new GradleException("Could not determine Jib plugin version");
    }
    VersionChecker<GradleVersion> checker = new VersionChecker<>(GradleVersion::version);
    if (!checker.compatibleVersion(requiredVersion, actualVersion)) {
      String failure =
          String.format(
              "Jib plugin version is %s but is required to be %s", actualVersion, requiredVersion);
      throw new GradleException(failure);
    }
  }

  @Override
  public void apply(Project project) {
    checkGradleVersion();
    checkJibVersion();

    JibExtension jibExtension =
        project.getExtensions().create(JIB_EXTENSION_NAME, JibExtension.class, project);

    TaskContainer tasks = project.getTasks();
    TaskProvider<BuildImageTask> buildImageTask =
        tasks.register(
            BUILD_IMAGE_TASK_NAME,
            BuildImageTask.class,
            task -> {
              task.setGroup("Jib");
              task.setDescription("Builds a container image to a registry.");
              task.setJibExtension(jibExtension);
            });

    TaskProvider<BuildDockerTask> buildDockerTask =
        tasks.register(
            BUILD_DOCKER_TASK_NAME,
            BuildDockerTask.class,
            task -> {
              task.setGroup("Jib");
              task.setDescription("Builds a container image to a Docker daemon.");
              task.setJibExtension(jibExtension);
            });

    TaskProvider<BuildTarTask> buildTarTask =
        tasks.register(
            BUILD_TAR_TASK_NAME,
            BuildTarTask.class,
            task -> {
              task.setGroup("Jib");
              task.setDescription("Builds a container image to a tarball.");
              task.setJibExtension(jibExtension);
            });

    tasks
        .register(SKAFFOLD_FILES_TASK_V2_NAME, FilesTaskV2.class)
        .configure(task -> task.setJibExtension(jibExtension));
    tasks
        .register(SKAFFOLD_INIT_TASK_NAME, InitTask.class)
        .configure(task -> task.setJibExtension(jibExtension));
    TaskProvider<SyncMapTask> syncMapTask =
        tasks.register(
            SKAFFOLD_SYNC_MAP_TASK_NAME,
            SyncMapTask.class,
            task -> task.setJibExtension(jibExtension));

    // A check to catch older versions of Jib.  This can be removed once we are certain people
    // are using Jib 1.3.1 or later.
    tasks.register(SKAFFOLD_CHECK_REQUIRED_VERSION_TASK_NAME, CheckJibVersionTask.class);

    project.afterEvaluate(
        projectAfterEvaluation -> {
          try {
            TaskProvider<Task> warTask = TaskCommon.getWarTaskProvider(projectAfterEvaluation);
            TaskProvider<Task> bootWarTask =
                TaskCommon.getBootWarTaskProvider(projectAfterEvaluation);
            List<TaskProvider<?>> dependsOnTask = new ArrayList<>();
            if (warTask != null || bootWarTask != null) {
              // Have all tasks depend on the 'war' and/or 'bootWar' task.
              if (warTask != null) {
                dependsOnTask.add(warTask);
              }
              if (bootWarTask != null) {
                dependsOnTask.add(bootWarTask);
              }
            } else if ("packaged".equals(jibExtension.getContainerizingMode())) {
              // Have all tasks depend on the 'jar' task.
              TaskProvider<Task> jarTask = projectAfterEvaluation.getTasks().named("jar");
              dependsOnTask.add(jarTask);

              if (projectAfterEvaluation.getPlugins().hasPlugin("org.springframework.boot")) {
                Jar jar = (Jar) jarTask.get();
                jar.setEnabled(true);
                jar.setClassifier("original");
              }
            } else {
              // Have all tasks depend on the 'classes' task.
              dependsOnTask.add(projectAfterEvaluation.getTasks().named("classes"));
            }
            buildImageTask.configure(task -> task.dependsOn(dependsOnTask));
            buildDockerTask.configure(task -> task.dependsOn(dependsOnTask));
            buildTarTask.configure(task -> task.dependsOn(dependsOnTask));
            syncMapTask.configure(task -> task.dependsOn(dependsOnTask));

            // Find project dependencies and add a dependency to their assemble task. We make sure
            // to only add the dependency after BasePlugin is evaluated as otherwise the assemble
            // task may not be available yet.
            List<Project> computedDependencies = getProjectDependencies(projectAfterEvaluation);
            for (Project dependencyProject : computedDependencies) {
              dependencyProject
                  .getPlugins()
                  .withType(
                      BasePlugin.class,
                      unused -> {
                        TaskProvider<Task> assembleTask =
                            dependencyProject.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME);
                        buildImageTask.configure(task -> task.dependsOn(assembleTask));
                        buildDockerTask.configure(task -> task.dependsOn(assembleTask));
                        buildTarTask.configure(task -> task.dependsOn(assembleTask));
                      });
            }
          } catch (UnknownTaskException ex) {
            throw new GradleException(
                "Could not find task 'classes' on project "
                    + projectAfterEvaluation.getDisplayName()
                    + " - perhaps you did not apply the 'java' plugin?",
                ex);
          }
        });
  }
}
