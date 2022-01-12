package com.artspace.appuser.outgoing;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import java.util.HashMap;
import java.util.Map;

public class KafkaTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

  @Override
  public Map<String, String> start() {
    final var props = InMemoryConnector.switchOutgoingChannelsToInMemory("appusers-out");
    return new HashMap<>(props);
  }

  @Override
  public void stop() {
    InMemoryConnector.clear();
  }
}
