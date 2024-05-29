// Copyright (c) 2024 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate e2e tests,
// which are released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/e2e/blob/main/LICENSE

@file:OptIn(ExperimentalSerializationApi::class)

package my.restate.e2e.services.kotlin

import dev.restate.sdk.annotation.Handler
import dev.restate.sdk.annotation.Service
import dev.restate.sdk.annotation.Shared
import dev.restate.sdk.annotation.VirtualObject
import dev.restate.sdk.kotlin.Context
import dev.restate.sdk.kotlin.ObjectContext
import dev.restate.sdk.kotlin.SharedObjectContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable(with = CommandSerializer::class)
sealed class Command {
  abstract val kind: Int
}

@Serializable
class IncrementStateCounter : Command() {
  companion object {
    const val KIND = 4
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class RecoverTerminalCall : Command() {
  companion object {
    const val KIND = 13
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class RecoverTerminalCallMaybeUnAwaited : Command() {
  companion object {
    const val KIND = 14
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class ThrowingSideEffect : Command() {
  companion object {
    const val KIND = 11
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class SlowSideEffect : Command() {
  companion object {
    const val KIND = 12
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class IncrementStateCounterIndirectly : Command() {
  companion object {
    const val KIND = 5
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class ResolveAwakeable : Command() {
  companion object {
    const val KIND = 16
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class RejectAwakeable : Command() {
  companion object {
    const val KIND = 17
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class IncrementStateCounterViaAwakeable : Command() {
  companion object {
    const val KIND = 18
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class CallService : Command() {
  companion object {
    const val KIND = 7
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
class SideEffect : Command() {
  companion object {
    const val KIND = 10
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class GetState(val key: Int) : Command() {
  companion object {
    const val KIND = 2
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class ClearState(val key: Int) : Command() {
  companion object {
    const val KIND = 3
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class SetState(val key: Int) : Command() {
  companion object {
    const val KIND = 1
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class Sleep(val duration: Int) : Command() {
  companion object {
    const val KIND = 6
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class IncrementViaDelayedCall(val duration: Int) : Command() {
  companion object {
    const val KIND = 9
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class AwaitPromise(val index: Int) : Command() {
  companion object {
    const val KIND = 15
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class CallSlowService(val sleep: Int) : Command() {
  companion object {
    const val KIND = 8
  }

  @EncodeDefault override val kind: Int = KIND
}

@Serializable
data class CallObject(val key: Int, val program: Program) : Command() {
  companion object {
    const val KIND = 19
  }

  @EncodeDefault override val kind: Int = KIND
}

object CommandSerializer : JsonContentPolymorphicSerializer<Command>(Command::class) {
  override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Command> {

    return when (val type = element.jsonObject["kind"]?.jsonPrimitive?.intOrNull) {
      IncrementStateCounter.KIND -> IncrementStateCounter.serializer()
      RecoverTerminalCall.KIND -> RecoverTerminalCall.serializer()
      RecoverTerminalCallMaybeUnAwaited.KIND -> RecoverTerminalCallMaybeUnAwaited.serializer()
      ThrowingSideEffect.KIND -> ThrowingSideEffect.serializer()
      SlowSideEffect.KIND -> SlowSideEffect.serializer()
      IncrementStateCounterIndirectly.KIND -> IncrementStateCounterIndirectly.serializer()
      ResolveAwakeable.KIND -> ResolveAwakeable.serializer()
      RejectAwakeable.KIND -> RejectAwakeable.serializer()
      IncrementStateCounterViaAwakeable.KIND -> IncrementStateCounterViaAwakeable.serializer()
      CallService.KIND -> CallService.serializer()
      SideEffect.KIND -> SideEffect.serializer()
      GetState.KIND -> GetState.serializer()
      ClearState.KIND -> ClearState.serializer()
      SetState.KIND -> SetState.serializer()
      Sleep.KIND -> Sleep.serializer()
      IncrementViaDelayedCall.KIND -> IncrementViaDelayedCall.serializer()
      AwaitPromise.KIND -> AwaitPromise.serializer()
      CallSlowService.KIND -> CallSlowService.serializer()
      CallObject.KIND -> CallObject.serializer()
      else -> error("unknown command kind $type")
    }
  }
}

@Serializable data class Program(val commands: List<Command>)

@VirtualObject(name = "ObjectInterpreter")
interface ObjectInterpreter {

  @Shared suspend fun counter(ctx: SharedObjectContext): Int

  @Handler suspend fun interpret(ctx: ObjectContext, program: Program)
}

@Serializable data class EchoLaterRequest(val sleep: Int, val parameter: String)

@Serializable data class InterpreterId(val layer: Int, val key: String)

@Serializable
data class IncrementViaAwakeableDanceRequest(
    val interpreter: InterpreterId,
    val txPromiseId: String
)

@Service(name = "ServiceInterpreterHelper")
interface ServiceInterpreterHelper {
  @Handler suspend fun ping(ctx: Context)

  @Handler suspend fun echo(ctx: Context, param: String): String

  @Handler suspend fun echoLater(ctx: Context, req: EchoLaterRequest): String

  @Handler suspend fun terminalFailure(ctx: Context)

  @Handler suspend fun incrementIndirectly(ctx: Context, id: InterpreterId)

  @Handler suspend fun resolveAwakeable(ctx: Context, id: String)

  @Handler suspend fun rejectAwakeable(ctx: Context, id: String)

  @Handler
  suspend fun incrementViaAwakeableDance(ctx: Context, req: IncrementViaAwakeableDanceRequest)
}
