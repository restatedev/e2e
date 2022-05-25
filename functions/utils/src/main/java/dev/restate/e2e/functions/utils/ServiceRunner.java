package dev.restate.e2e.functions.utils;

import dev.restate.sdk.RestateGrpcInterceptors;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServiceRunner {
  private static final Logger LOG = LogManager.getLogger(ServiceRunner.class);

  private final List<ServerServiceDefinition> serverServiceDefinitions;
  private final int port;

  private ServiceRunner(List<? extends BindableService> bindableServices, int port) {
    this.serverServiceDefinitions =
        bindableServices.stream().map(RestateGrpcInterceptors::from).collect(Collectors.toList());
    this.port = port;
  }

  public void run() throws IOException, InterruptedException {
    LOG.info("Start grpc services on port {}", port);

    var builder = ServerBuilder.forPort(port);

    for (ServerServiceDefinition serverServiceDefinition : this.serverServiceDefinitions) {
      builder.addService(serverServiceDefinition);
    }

    final var server = builder.build();

    server.start().awaitTermination();
  }

  public static ServiceRunner create(BindableService... bindableServices) {
    return createWithPort(
        Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(8080),
        bindableServices);
  }

  public static ServiceRunner createWithPort(int port, BindableService... bindableServices) {
    return new ServiceRunner(List.of(bindableServices), port);
  }
}
