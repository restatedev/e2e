package dev.restate.e2e.functions.externalcall;

import dev.restate.e2e.functions.utils.ServiceRunner;
import java.io.IOException;

public class Main {

  public static void main(String[] args) throws IOException, InterruptedException {
    ServiceRunner.create(new RandomNumberListGeneratorService(), new ReplierService()).run();
  }
}
