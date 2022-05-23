package dev.restate.e2e.functions.counter;

import dev.restate.sdk.RestateGrpcInterceptors;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        final int port = Optional.ofNullable(System.getenv("PORT"))
                .map(Integer::parseInt).orElse(8080);

        logger.info("Start counter on port {}", port);
        final var server =
                ServerBuilder.forPort(port)
                        .addService(RestateGrpcInterceptors.from(new CounterService()))
                        .addService(RestateGrpcInterceptors.from(new NoopService()))
                        .build();

        server.start().awaitTermination();
    }
}