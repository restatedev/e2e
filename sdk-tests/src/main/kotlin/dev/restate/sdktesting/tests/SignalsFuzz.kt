// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate SDK Test suite tool,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-test-suite/blob/main/LICENSE
package dev.restate.sdktesting.tests

import dev.restate.client.Client
import dev.restate.client.kotlin.*
import dev.restate.sdktesting.contracts.TestUtilsService
import dev.restate.sdktesting.contracts.TestUtilsService.RejectSignalRequest
import dev.restate.sdktesting.contracts.TestUtilsService.ResolveSignalRequest
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAllCompleted
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitAllSucceededOrFirstFailed
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitFirstCompleted
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitOne
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.AwaitableCommand
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.Command
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.CreateAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.CreateSignal
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.InterpretRequest
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.RejectAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.ResolveAwakeable
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.RunReturns
import dev.restate.sdktesting.contracts.VirtualObjectCommandInterpreter.Sleep
import dev.restate.sdktesting.infra.*
import java.net.URI
import java.time.Duration
import java.util.UUID
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import org.apache.logging.log4j.LogManager
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.withAlias
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Randomly generated interpreter programs mixing awaits on named signals, awakeables, sleeps and
 * runs, together with a random delivery schedule for the signals and awakeables.
 *
 * Named signals can be delivered many times: the k-th await of a signal name must observe the k-th
 * delivery of that name, regardless of when it was delivered (before the handle is created, while
 * awaiting something else, ...). Programs are generated so that their outcome doesn't depend on
 * timing, and every delivery is waited to be in the journal before sending the next one, so the
 * journal order is the schedule order.
 *
 * Seeds are random on every run and printed in the test name. To reproduce a failure, set the
 * `SIGNALS_FUZZ_SEED` environment variable to the failing seed.
 */
class SignalsFuzz {
  companion object {
    @RegisterExtension
    val deployerExt: RestateDeployerExtension = RestateDeployerExtension {
      withServiceSpec(
          ServiceSpec.defaultBuilder()
              .withServices(VirtualObjectCommandInterpreter::class, TestUtilsService::class)
      )
    }

    private val LOG = LogManager.getLogger(SignalsFuzz::class.java)

    private const val NUMBER_OF_SEEDS = 10

    @JvmStatic
    fun seeds(): Stream<Arguments> {
      val seeds =
          System.getenv("SIGNALS_FUZZ_SEED")?.let { listOf(it.toLong()) }
              ?: List(NUMBER_OF_SEEDS) { Random.nextLong() }
      return seeds.stream().map { Arguments.of(it) }
    }
  }

  @ParameterizedTest(name = "seed {0}")
  @MethodSource("seeds")
  @Execution(ExecutionMode.CONCURRENT)
  fun randomAwaitsAndSignals(
      seed: Long,
      @InjectClient ingressClient: Client,
      @InjectAdminURI adminURI: URI,
  ) =
      runTest(timeout = 3.minutes) {
        val program = Program.generate(Random(seed))
        val schedule = program.generateSchedule(Random(seed))
        val description = describe(seed, program, schedule)
        LOG.info("Running signals fuzz program\n$description")

        val interpreterClient =
            ingressClient.toVirtualObject<VirtualObjectCommandInterpreter>(
                UUID.randomUUID().toString()
            )
        val utilsClient = ingressClient.toService<TestUtilsService>()

        try {
          val sendResponse =
              interpreterClient
                  .request {
                    interpretCommands(InterpretRequest(program.steps.map { it.toCommand() }))
                  }
                  .options(idempotentCallOptions)
                  .send()
          val invocationId = sendResponse.invocationId()

          for ((index, event) in schedule.withIndex()) {
            when (event) {
              is Event.DeliverSignal ->
                  if (event.outcome.failed) {
                    utilsClient
                        .request {
                          rejectSignal(
                              RejectSignalRequest(invocationId, event.name, event.outcome.value)
                          )
                        }
                        .options(idempotentCallOptions)
                        .call()
                  } else {
                    utilsClient
                        .request {
                          resolveSignal(
                              ResolveSignalRequest(invocationId, event.name, event.outcome.value)
                          )
                        }
                        .options(idempotentCallOptions)
                        .call()
                  }
              is Event.CompleteAwakeable -> {
                await withAlias
                    "awakeable ${event.key} is registered" untilAsserted
                    {
                      assertThat(
                              interpreterClient.request { hasAwakeable(event.key) }.call().response
                          )
                          .isTrue()
                    }
                if (event.outcome.failed) {
                  interpreterClient
                      .request { rejectAwakeable(RejectAwakeable(event.key, event.outcome.value)) }
                      .options(idempotentCallOptions)
                      .call()
                } else {
                  interpreterClient
                      .request {
                        resolveAwakeable(ResolveAwakeable(event.key, event.outcome.value))
                      }
                      .options(idempotentCallOptions)
                      .call()
                }
              }
            }

            // Signals and awakeable completions are both signal notifications in the journal.
            // After the last event the invocation can complete, and its journal might be gone.
            if (index == schedule.lastIndex) {
              continue
            }
            (await withAlias "event $index ($event) is in the journal").pollInterval(
                Duration.ofMillis(20)
            ) untilAsserted
                {
                  assertThat(
                          getJournal(adminURI, invocationId).rows.count {
                            it.entryType == "Notification: Signal"
                          }
                      )
                      .isEqualTo(index + 1)
                }
          }

          // Poll the results rather than attaching, so that a lost delivery fails with the observed
          // values instead of hanging.
          await withAlias
              "all steps completed with the expected results" untilAsserted
              {
                assertThat(interpreterClient.request { getResults() }.call().response)
                    .containsExactlyElementsOf(program.expectedResults)
              }

          val terminalFailure = program.expectedTerminalFailure
          if (terminalFailure != null) {
            assertThat(runCatching { sendResponse.attachSuspend().response }.exceptionOrNull())
                .message()
                .contains(terminalFailure)
          } else {
            assertThat(sendResponse.attachSuspend().response)
                .isEqualTo(program.expectedResults.lastOrNull() ?: "")
          }
        } catch (e: Throwable) {
          val observed = runCatching { interpreterClient.request { getResults() }.call().response }
          throw AssertionError(
              "Signals fuzz failed\n$description\nObserved results: ${observed.getOrElse { "<unavailable: ${it.message}>" }}",
              e,
          )
        }
      }

  private fun describe(seed: Long, program: Program, schedule: List<Event>) =
      """
      |Seed: $seed (set SIGNALS_FUZZ_SEED=$seed to reproduce)
      |Program (${program.steps.size} steps):
      |${program.steps.withIndex().joinToString("\n") { (i, s) -> "  $i: $s" }}
      |Schedule (${schedule.size} events):
      |${schedule.withIndex().joinToString("\n") { (i, e) -> "  $i: $e" }}
      |Expected results: ${program.expectedResults}
      """
          .trimMargin()

  // --- Model

  /** The value a signal/awakeable is completed with, or the failure reason when [failed]. */
  private data class Outcome(val value: String, val failed: Boolean) {
    override fun toString() = if (failed) "reject($value)" else "resolve($value)"
  }

  private sealed interface Leaf {
    fun toCommand(): AwaitableCommand

    /** Result of awaiting this leaf, when it succeeds. */
    val value: String

    val failed: Boolean
      get() = false

    data class Signal(val name: String, val outcome: Outcome) : Leaf {
      override fun toCommand() = CreateSignal(name)

      override val value = outcome.value
      override val failed = outcome.failed
    }

    data class Awakeable(val key: String, val outcome: Outcome) : Leaf {
      override fun toCommand() = CreateAwakeable(key)

      override val value = outcome.value
      override val failed = outcome.failed
    }

    /** An awakeable that is never completed, used as the losing branch of a race. */
    data class PendingAwakeable(val key: String) : Leaf {
      override fun toCommand() = CreateAwakeable(key)

      override val value
        get() = error("never completes")
    }

    data class Run(override val value: String) : Leaf {
      override fun toCommand() = RunReturns(value)
    }

    data class SleepFor(val millis: Long) : Leaf {
      override fun toCommand() = Sleep(millis)

      override val value = "sleep"
    }
  }

  private sealed interface Step {
    val leaves: List<Leaf>

    fun toCommand(): Command

    /** Result recorded in the interpreter results, null if the step fails the invocation. */
    val expectedResult: String?

    data class One(val leaf: Leaf) : Step {
      override val leaves = listOf(leaf)

      override fun toCommand() = AwaitOne(leaf.toCommand())

      override val expectedResult = if (leaf.failed) null else leaf.value
    }

    data class AllSucceeded(override val leaves: List<Leaf>) : Step {
      override fun toCommand() = AwaitAllSucceededOrFirstFailed(leaves.map { it.toCommand() })

      override val expectedResult = leaves.joinToString("|") { it.value }
    }

    data class AllCompleted(override val leaves: List<Leaf>) : Step {
      override fun toCommand() = AwaitAllCompleted(leaves.map { it.toCommand() })

      override val expectedResult =
          leaves.joinToString("|") { if (it.failed) "err:${it.value}" else "ok:${it.value}" }
    }

    /** Race of a signal against an awakeable that never completes: the signal always wins. */
    data class RaceWithPending(val signal: Leaf.Signal, val pending: Leaf.PendingAwakeable) : Step {
      override val leaves = listOf(signal, pending)

      override fun toCommand() =
          AwaitFirstCompleted(listOf(signal.toCommand(), pending.toCommand()))

      override val expectedResult = signal.value
    }
  }

  private sealed interface Event {
    data class DeliverSignal(val name: String, val outcome: Outcome) : Event {
      override fun toString() = "signal $name $outcome"
    }

    data class CompleteAwakeable(val key: String, val outcome: Outcome) : Event {
      override fun toString() = "awakeable $key $outcome"
    }
  }

  private class Program(val steps: List<Step>) {
    val expectedResults: List<String> = steps.mapNotNull { it.expectedResult }

    val expectedTerminalFailure: String? =
        (steps.lastOrNull() as? Step.One)?.leaf?.takeIf { it.failed }?.value

    /**
     * Interleaves the signal deliveries and awakeable completions. Deliveries of the same name stay
     * in program order, and might be sent early, even many steps before the program awaits them.
     * Awakeables are completed only once all the previous steps can complete, otherwise the program
     * would never reach them.
     */
    fun generateSchedule(random: Random): List<Event> {
      val pendingDeliveries: Map<String, ArrayDeque<Outcome>> =
          steps
              .flatMap { it.leaves }
              .filterIsInstance<Leaf.Signal>()
              .groupBy({ it.name }, { it.outcome })
              .mapValues { ArrayDeque(it.value) }

      val totalDeliveries = pendingDeliveries.mapValues { it.value.size }
      fun delivered(name: String) = totalDeliveries[name]!! - pendingDeliveries[name]!!.size

      val schedule = mutableListOf<Event>()
      fun deliverNext(name: String) {
        schedule.add(Event.DeliverSignal(name, pendingDeliveries[name]!!.removeFirst()))
      }

      // How many deliveries of each name the steps so far consume
      val consumed = mutableMapOf<String, Int>()

      for (step in steps) {
        // Send some deliveries early, before the program awaits them
        repeat(random.nextInt(0, 5)) {
          val names = pendingDeliveries.filterValues { it.isNotEmpty() }.keys.toList()
          if (names.isNotEmpty()) {
            deliverNext(names.random(random))
          }
        }
        // Sometimes flush all the remaining deliveries of a name, so they all queue up
        if (random.nextInt(5) == 0) {
          pendingDeliveries
              .filterValues { it.isNotEmpty() }
              .keys
              .randomOrNull(random)
              ?.let { name -> repeat(pendingDeliveries[name]!!.size) { deliverNext(name) } }
        }

        val stepEvents = mutableListOf<() -> Unit>()
        for (leaf in step.leaves) {
          when (leaf) {
            is Leaf.Signal -> {
              val needed = consumed.merge(leaf.name, 1, Int::plus)!!
              stepEvents.add {
                if (delivered(leaf.name) < needed) {
                  deliverNext(leaf.name)
                }
              }
            }
            is Leaf.Awakeable ->
                stepEvents.add { schedule.add(Event.CompleteAwakeable(leaf.key, leaf.outcome)) }
            else -> {}
          }
        }
        stepEvents.shuffled(random).forEach { it() }
      }

      check(pendingDeliveries.values.all { it.isEmpty() })
      return schedule
    }

    companion object {
      private val SIGNAL_NAMES = listOf("a", "b", "c", "d", "e")

      fun generate(random: Random): Program {
        // Few names, so that the same name is delivered many times
        val names = SIGNAL_NAMES.shuffled(random).take(random.nextInt(1, 4))
        val deliveryCounters = mutableMapOf<String, Int>()
        var awakeableCounter = 0
        var runCounter = 0

        fun signal(name: String, mayFail: Boolean): Leaf.Signal {
          val k = deliveryCounters.merge(name, 1, Int::plus)
          val failed = mayFail && random.nextInt(3) == 0
          return Leaf.Signal(name, Outcome(if (failed) "$name#$k-rejected" else "$name#$k", failed))
        }

        fun awakeable(mayFail: Boolean): Leaf.Awakeable {
          val key = "awk${awakeableCounter++}"
          val failed = mayFail && random.nextInt(3) == 0
          return Leaf.Awakeable(key, Outcome(if (failed) "$key-rejected" else key, failed))
        }

        fun nonSignalLeaf(mayFail: Boolean): Leaf =
            when (random.nextInt(3)) {
              0 -> awakeable(mayFail)
              1 -> Leaf.Run("run${runCounter++}")
              else -> Leaf.SleepFor(random.nextLong(1, 200))
            }

        // Combinator members: distinct signal names, so that two handles never compete for the
        // same delivery
        fun combinatorLeaves(mayFail: Boolean): List<Leaf> {
          val signals =
              names.shuffled(random).take(random.nextInt(0, names.size + 1)).map {
                signal(it, mayFail)
              }
          val others =
              List(random.nextInt(if (signals.isEmpty()) 2 else 0, 3)) {
                nonSignalLeaf(mayFail)
              }
          return (signals + others).shuffled(random)
        }

        val steps = buildList {
          repeat(random.nextInt(5, 21)) {
            when (random.nextInt(12)) {
              in 0..4 -> add(Step.One(signal(names.random(random), mayFail = false)))
              5 -> {
                // Await the same signal many times in a row
                val name = names.random(random)
                repeat(random.nextInt(2, 6)) { add(Step.One(signal(name, mayFail = false))) }
              }
              6 -> add(Step.One(awakeable(mayFail = false)))
              7 -> add(Step.One(nonSignalLeaf(mayFail = false)))
              8 -> add(Step.AllSucceeded(combinatorLeaves(mayFail = false)))
              9,
              10 -> add(Step.AllCompleted(combinatorLeaves(mayFail = true)))
              else ->
                  add(
                      Step.RaceWithPending(
                          signal(names.random(random), mayFail = false),
                          Leaf.PendingAwakeable("awk${awakeableCounter++}"),
                      )
                  )
            }
          }
        }

        // Sometimes end with a rejected signal, failing the invocation
        val failingStep =
            if (random.nextInt(5) == 0) {
              val name = names.random(random)
              val k = deliveryCounters.merge(name, 1, Int::plus)
              listOf(Step.One(Leaf.Signal(name, Outcome("$name#$k-rejected", true))))
            } else emptyList()

        return Program(steps + failingStep)
      }
    }
  }
}
