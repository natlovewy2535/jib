/*
 * Copyright 2019 Google LLC.
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

package com.google.cloud.tools.jib.plugins.api.maven;

import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.plugins.api.JibPluginExtensionException;
import java.util.function.Consumer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

public interface JibMavenPluginExtension {

  default JibContainerBuilder extendJibContainerBuilder(
      JibContainerBuilder containerBuilder,
      MavenProject project,
      MavenSession session,
      Consumer<LogEvent> logger)
      throws JibPluginExtensionException {
    return containerBuilder;
  }
}
