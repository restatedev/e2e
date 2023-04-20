package dev.restate.e2e

import io.grpc.ManagedChannel
import java.util.concurrent.TimeUnit

object GrpcUtils {

  fun <R> ManagedChannel.useAndTerminate(useChannel: (ManagedChannel) -> R): R {
    val returnValue = useChannel(this)

    this.shutdownNow()
    this.awaitTermination(10, TimeUnit.SECONDS)

    return returnValue
  }

  fun <S, R> ManagedChannel.blockingUseAndTerminate(
      stubCreator: (ManagedChannel) -> S,
      useStubFunction: (S) -> R
  ): R {
    val stub = stubCreator(this)
    val returnValue = useStubFunction(stub)

    this.shutdownNow()
    this.awaitTermination(10, TimeUnit.SECONDS)

    return returnValue
  }
}
