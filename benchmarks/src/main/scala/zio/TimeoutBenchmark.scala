package zio

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Level,
  Mode,
  OutputTimeUnit,
  Param,
  Setup,
  State,
  Scope => JScope
}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TimeoutBenchmark {
  import BenchmarkUtil.unsafeRun

  @Param(Array("0", "100"))
  var n: Int = _

  var reps: Int = 1000

  var effect: UIO[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    effect = ZIO.foldLeft(0 until n)(0) { case (prev, x) =>
      ZIO.succeed(prev + x)
    }

  @Benchmark
  def zioBaseline = {
    val _ = unsafeRun {
      ZIO.foreachDiscard(1 to reps)(_ => effect)
    }
  }

  @Benchmark
  def zioTimeout = {
    val _ = unsafeRun {
      ZIO.foreachDiscard(1 to reps)(_ => effect.timeout(100.minutes))
    }
  }

  @Benchmark
  def zioTimeoutOrig = {
    val _ = unsafeRun {
      ZIO.foreachDiscard(1 to reps)(_ => TimeoutBenchmark.timeoutOrig(effect)(100.minutes))
    }
  }
}

object TimeoutBenchmark {

  /**
   * The previous implementation of `ZIO#timeout`, kept here (with the
   * `raceFibersWith` combinator it relied on) to allow benchmarking the new
   * implementation against the old one. It races the effect against a forked
   * `sleep` fiber.
   */
  def timeoutOrig[R, E, A](zio: ZIO[R, E, A])(duration: => Duration)(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZIO.fiberIdWith { parentFiberId =>
      raceFibersWith[R, E, A, Nothing, Unit, Option[A]](zio, ZIO.sleep(duration).interruptible)(
        (winner, loser) =>
          winner.await.flatMap { exit =>
            loser.interruptAs(parentFiberId) *> winner.inheritAll *> exit.mapExit(Some(_))
          },
        (winner, loser) =>
          winner.await.flatMap {
            case e: Exit.Failure[Nothing] =>
              loser.interruptAs(parentFiberId) *> loser.inheritAll *> e
            case _ =>
              loser.interruptAs(parentFiberId) *> loser.inheritAll.as(None)
          },
        null,
        internal.FiberScope.global
      )
    }

  /**
   * A copy of the private `ZIO#raceFibersWith` the previous `timeout`
   * implementation was built upon.
   */
  private def raceFibersWith[R, E, A, ER, B, C](
    left: ZIO[R, E, A],
    right: ZIO[R, ER, B]
  )(
    leftWins: (Fiber.Runtime[E, A], Fiber.Runtime[ER, B]) => ZIO[R, E, C],
    rightWins: (Fiber.Runtime[ER, B], Fiber.Runtime[E, A]) => ZIO[R, E, C],
    leftScope: internal.FiberScope,
    rightScope: internal.FiberScope
  )(implicit trace: Trace): ZIO[R, E, C] =
    ZIO.withFiberRuntime[R, E, C] { (parentFiber, parentStatus) =>
      import java.util.concurrent.atomic.AtomicBoolean

      @inline def complete[E0, E1, A0, B0](
        winner: Fiber.Runtime[E0, A0],
        loser: Fiber.Runtime[E1, B0],
        cont: (Fiber.Runtime[E0, A0], Fiber.Runtime[E1, B0]) => ZIO[R, E, C],
        ab: AtomicBoolean,
        cb: ZIO[R, E, C] => Any
      ): Unit =
        if (ab.compareAndSet(false, true)) {
          cb(cont(winner, loser))
        }

      val graft     = ZIO.Grafter(parentFiber)
      val leftEff   = graft.applyOnExit(left)
      val rightEff  = graft.applyOnExit(right)
      val flags     = parentStatus.runtimeFlags
      val leftFiber = ZIO.unsafe.makeChildFiber(trace, leftEff, parentFiber, flags, leftScope)(Unsafe)

      val rightFiber = ZIO.unsafe.makeChildFiber(trace, rightEff, parentFiber, flags, rightScope)(Unsafe)

      val startLeft  = leftFiber.startSuspended()(Unsafe)
      val startRight = rightFiber.startSuspended()(Unsafe)

      ZIO.async[R, E, C](
        { cb =>
          val raceIndicator = new AtomicBoolean()

          leftFiber.addObserver { _ =>
            complete(leftFiber, rightFiber, leftWins, raceIndicator, cb)
          }(Unsafe)

          rightFiber.addObserver { _ =>
            complete(rightFiber, leftFiber, rightWins, raceIndicator, cb)
          }(Unsafe)

          startLeft(leftEff)
          startRight(rightEff)
          ()
        },
        leftFiber.id <> rightFiber.id
      )
    }
}
