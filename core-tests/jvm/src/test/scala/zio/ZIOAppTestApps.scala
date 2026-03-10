/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

/**
 * Standalone [[ZIOApp]] programs used as fixtures by [[ZIOAppIntegrationSpec]].
 *
 * Each object is compiled as part of the test sources and launched in a
 * separate JVM process via [[java.lang.ProcessBuilder]].  The parent test
 * communicates with the child through stdout markers.
 *
 * == Marker convention ==
 *  - `READY`        — child is waiting and can be signalled
 *  - `ACQUIRED`     — a resource has been acquired
 *  - `FINALIZED`    — a finalizer has completed
 *  - `FINALIZING`   — a (potentially slow) finalizer has started
 *  - `WORKING`      — the app is doing some work
 *  - `DONE`         — the app has completed its work
 *  - `SUCCESS`      — the app is about to exit successfully
 *  - `BG-FINALIZED` — a background fiber's finalizer has completed
 *  - `LAYER-*`      — ZLayer lifecycle markers
 *  - `BOOTSTRAP-*`  — bootstrap layer lifecycle markers
 *  - `FINAL-N`      — Nth finalizer in a LIFO ordering test
 *  - `ZERO-FINALIZED` — finalizer in a zero-timeout app (may or may not appear)
 */

// ---------------------------------------------------------------------------
// Normal-completion fixtures
// ---------------------------------------------------------------------------

/** Exits with code 0 after printing a success marker. */
object AppSuccess extends ZIOAppDefault {
  override def run =
    ZIO.succeed(println("SUCCESS"))
}

/** Exits with code 1 due to a typed failure. */
object AppFailure extends ZIOAppDefault {
  override def run =
    ZIO.fail("boom")
}

/** Exits with code 1 due to an untyped defect. */
object AppDie extends ZIOAppDefault {
  override def run =
    ZIO.die(new RuntimeException("catastrophic"))
}

/** Prints two markers and then exits with code 0. */
object AppExitAfterWork extends ZIOAppDefault {
  override def run =
    ZIO.succeed(println("WORKING")) *>
      ZIO.succeed(println("DONE"))
}

/** Acquires a resource in a `Scope`, then succeeds; verifies finalizer runs. */
object AppFinalizerOnSuccess extends ZIOAppDefault {
  override def run =
    ZIO.acquireRelease(
      ZIO.succeed(println("ACQUIRED"))
    )(_ => ZIO.succeed(println("FINALIZED"))) *>
      ZIO.unit
}

/** Acquires a resource, then fails; verifies finalizer still runs. */
object AppFinalizerOnFailure extends ZIOAppDefault {
  override def run =
    ZIO.acquireRelease(
      ZIO.succeed(println("ACQUIRED"))
    )(_ => ZIO.succeed(println("FINALIZED"))) *>
      ZIO.fail("planned failure")
}

// ---------------------------------------------------------------------------
// Signal-driven shutdown fixtures
// ---------------------------------------------------------------------------

/** Hangs forever on `ZIO.never`, printing `READY` first; has one finalizer. */
object AppHangsUntilInterrupted extends ZIOAppDefault {
  override def run =
    ZIO.addFinalizer(ZIO.succeed(println("FINALIZED"))) *>
      ZIO.succeed(println("READY")) *>
      ZIO.never
}

/**
 * Has a slow (3-second) finalizer; with the default [[Duration.Infinity]]
 * timeout the parent waits and sees "FINALIZED".
 * Regression test for issue #9901.
 */
object AppSlowFinalizer extends ZIOAppDefault {
  override def run =
    ZIO.addFinalizer(
      ZIO.succeed(println("FINALIZING")) *>
        ZIO.sleep(3.seconds) *>
        ZIO.succeed(println("FINALIZED"))
    ) *>
      ZIO.succeed(println("READY")) *>
      ZIO.never
}

/**
 * Registers three finalizers and emits LIFO-order markers.
 * Expected order: FINAL-3 → FINAL-2 → FINAL-1.
 */
object AppMultipleFinalizers extends ZIOAppDefault {
  override def run =
    ZIO.addFinalizer(ZIO.succeed(println("FINAL-1"))) *>
      ZIO.addFinalizer(ZIO.succeed(println("FINAL-2"))) *>
      ZIO.addFinalizer(ZIO.succeed(println("FINAL-3"))) *>
      ZIO.succeed(println("READY")) *>
      ZIO.never
}

/**
 * Acquires a resource in the [[bootstrap]] layer, so the release function
 * is invoked by the bootstrap scope when the app shuts down.
 */
object AppBootstrapFinalizer extends ZIOAppDefault {
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Unit] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        ZIO.succeed(println("BOOTSTRAP-ACQUIRED"))
      )(_ => ZIO.succeed(println("BOOTSTRAP-FINALIZED")))
    )

  override def run =
    ZIO.succeed(println("READY")) *> ZIO.never
}

// ---------------------------------------------------------------------------
// gracefulShutdownTimeout fixtures
// ---------------------------------------------------------------------------

/**
 * Has a 10-second finalizer but sets [[gracefulShutdownTimeout]] to 1 second.
 * The parent verifies the process exits in < 8 s with "FINALIZING" in output
 * but *without* "FINALIZED".
 */
object AppShutdownTimeout extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = 1.second

  override def run =
    ZIO.addFinalizer(
      ZIO.succeed(println("FINALIZING")) *>
        ZIO.sleep(10.seconds) *>
        ZIO.succeed(println("FINALIZED"))
    ) *>
      ZIO.succeed(println("READY")) *>
      ZIO.never
}

/**
 * Sets [[gracefulShutdownTimeout]] to [[Duration.Zero]].
 * The shutdown hook interrupts the fiber but does *not* wait, so the process
 * should exit very quickly.  The `ZERO-FINALIZED` marker may or may not
 * appear — the test only asserts that the process exits fast.
 */
object AppZeroTimeout extends ZIOAppDefault {
  override def gracefulShutdownTimeout: Duration = Duration.Zero

  override def run =
    ZIO.addFinalizer(ZIO.succeed(println("ZERO-FINALIZED"))) *>
      ZIO.succeed(println("READY")) *>
      ZIO.never
}

// ---------------------------------------------------------------------------
// Background fiber cleanup
// ---------------------------------------------------------------------------

/**
 * Forks a daemon fiber with a finalizer; verifies the fiber is interrupted
 * and its finalizer runs when the app receives a signal.
 */
object AppBackgroundFiber extends ZIOAppDefault {
  override def run =
    ZIO.scoped {
      for {
        _ <- (ZIO.addFinalizer(ZIO.succeed(println("BG-FINALIZED"))) *> ZIO.never).forkDaemon
        _ <- ZIO.succeed(println("READY"))
        _ <- ZIO.never
      } yield ()
    }
}

// ---------------------------------------------------------------------------
// ZLayer resource management fixtures
// ---------------------------------------------------------------------------

/** Service type for ZLayer tests. */
trait MyService

/** Normal-exit: ZLayer resource is acquired and released on app exit. */
object AppZLayerNormalExit extends ZIOAppDefault {
  val myLayer: ZLayer[Any, Nothing, MyService] = ZLayer.scoped(
    ZIO.acquireRelease(
      ZIO.succeed(println("LAYER-ACQUIRED")).as(new MyService {})
    )(_ => ZIO.succeed(println("LAYER-RELEASED")))
  )

  override def run =
    ZIO.serviceWith[MyService](_ => ()).provide(myLayer)
}

/** Signal-exit: ZLayer resource released even when process receives SIGINT. */
object AppZLayerSIGINT extends ZIOAppDefault {
  val myLayer: ZLayer[Any, Nothing, MyService] = ZLayer.scoped(
    ZIO.acquireRelease(
      ZIO.succeed(println("LAYER-ACQUIRED")).as(new MyService {})
    )(_ => ZIO.succeed(println("LAYER-RELEASED")))
  )

  override def run =
    ZIO
      .serviceWith[MyService](_ => ())
      .zipRight(ZIO.succeed(println("READY")))
      .zipRight(ZIO.never)
      .provide(myLayer)
}
