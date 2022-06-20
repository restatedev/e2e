package dev.restate.e2e.utils

import io.grpc.Metadata
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils

object GrpcUtils {

  @JvmStatic
  fun <T : AbstractStub<T>> T.withRestateKey(key: String): T {
    val meta = Metadata()
    meta.put(Metadata.Key.of("x-restate-id", Metadata.ASCII_STRING_MARSHALLER), key)
    return this.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta))
  }
}
