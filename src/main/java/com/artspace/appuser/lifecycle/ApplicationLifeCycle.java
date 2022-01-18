package com.artspace.appuser.lifecycle;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
class ApplicationLifeCycle {

  void onStart(@Observes StartupEvent ev) {
    final StringBuilder appName = new StringBuilder("\n");
    appName
        .append("     ___      .______   .______    __    __       _______. _______ .______          _______.        ___      .______    __  ").append("\n")
        .append("    /   \\     |   _  \\  |   _  \\  |  |  |  |     /       ||   ____||   _  \\        /       |       /   \\     |   _  \\  |  | ").append("\n")
        .append("   /  ^  \\    |  |_)  | |  |_)  | |  |  |  |    |   (----`|  |__   |  |_)  |      |   (----`      /  ^  \\    |  |_)  | |  | ").append("\n")
        .append("  /  /_\\  \\   |   ___/  |   ___/  |  |  |  |     \\   \\    |   __|  |      /        \\   \\         /  /_\\  \\   |   ___/  |  | ").append("\n")
        .append(" /  _____  \\  |  |      |  |      |  `--'  | .----)   |   |  |____ |  |\\  \\----.----)   |       /  _____  \\  |  |      |  | ").append("\n")
        .append("/__/     \\__\\ | _|      | _|       \\______/  |_______/    |_______|| _| `._____|_______/       /__/     \\__\\ | _|      |__|").append("\n");

    log.info(appName.toString());
    log.info("The application APPUSER is starting with profile " + ProfileManager.getActiveProfile());
  }

  void onStop(@Observes ShutdownEvent ev) {
    log.info("The application APPUSER is stopping...");
  }
}
