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
    log.info("     ___      .______   .______    __    __       _______. _______ .______          _______.        ___      .______    __  ");
    log.info("    /   \\     |   _  \\  |   _  \\  |  |  |  |     /       ||   ____||   _  \\        /       |       /   \\     |   _  \\  |  | ");
    log.info("   /  ^  \\    |  |_)  | |  |_)  | |  |  |  |    |   (----`|  |__   |  |_)  |      |   (----`      /  ^  \\    |  |_)  | |  | ");
    log.info("  /  /_\\  \\   |   ___/  |   ___/  |  |  |  |     \\   \\    |   __|  |      /        \\   \\         /  /_\\  \\   |   ___/  |  | ");
    log.info(" /  _____  \\  |  |      |  |      |  `--'  | .----)   |   |  |____ |  |\\  \\----.----)   |       /  _____  \\  |  |      |  | ");
    log.info("/__/     \\__\\ | _|      | _|       \\______/  |_______/    |_______|| _| `._____|_______/       /__/     \\__\\ | _|      |__|");

    log.info("The application APPUSER is starting with profile " + ProfileManager.getActiveProfile());
  }

  void onStop(@Observes ShutdownEvent ev) {
    log.info("The application APPUSER is stopping...");
  }
}
