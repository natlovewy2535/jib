package com.google.cloud.tools.jib.plugins.api;

public class JibPluginExtensionException extends Exception {

  public JibPluginExtensionException(String message) {
    super(message);
  }

  public JibPluginExtensionException(String message, Throwable cause) {
    super(message, cause);
  }

  public JibPluginExtensionException(Throwable cause) {
    super(cause);
  }
}
