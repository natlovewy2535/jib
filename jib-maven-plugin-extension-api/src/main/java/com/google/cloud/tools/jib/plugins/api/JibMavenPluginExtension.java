package com.google.cloud.tools.jib.plugins.api;

import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
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
