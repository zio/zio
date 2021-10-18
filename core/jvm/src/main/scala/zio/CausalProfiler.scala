package zio

import java.nio.file.{Files, Paths}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.util.{Random => ScRandom}

object CausalProfiler {

  final case class ThroughputData(name: String, delta: Int) {

    lazy val show: String =
      s"throughput-point\tname=$name\tdelta=$delta"

  }

  final case class ExperimentResult(
    selected: ZTraceElement,
    speedup: Float,
    duration: Long,
    selectedSamples: Long,
    throughputData: List[ThroughputData]
  ) {

    lazy val show: List[String] =
      s"experiment\tselected=$selected\tspeedup=$speedup\tduration=$duration\tselected-samples=$selectedSamples" :: throughputData
        .map(_.show)

  }

  final case class Samples(
    locations: Map[ZTraceElement, Long]
  ) {

    lazy val show: List[String] =
      locations.toList.map { case (k, v) =>
        s"samples\tlocation=$k\tcount=$v"
      }

  }

  final case class Result(
    experiments: List[ExperimentResult],
    samples: Samples
  ) {

    lazy val show: String =
      (experiments.flatMap(_.show) ++ samples.show).mkString("\n")

    def writeToFile(pathString: String): Task[Unit] =
      ZIO.effect {
        val path      = Paths.get(pathString)
        val parentDir = path.getParent()
        if (parentDir ne null) {
          Files.createDirectories(parentDir)
        }
        Files.write(path, show.getBytes())
        ()
      }

  }

  def progressPoint(name: String)(implicit trace: ZTraceElement): UIO[Unit] =
    internal.GlobalTrackerRef.get.flatMap { tracker =>
      ZIO.effectTotal {
        tracker.progressPoint(name)
      }
    }

  val DefaultScopeFilter: ZTraceElement => Boolean = (t => !(t.toString().startsWith("zio.internal")))

  def profile[R <: Has[Clock], E](
    iterations: Int = 80,
    scopeFilter: ZTraceElement => Boolean = (t => !(t.toString.startsWith("zio.internal"))),
    reportProgress: Boolean = true
  )(
    zio: ZIO[R, E, Any]
  )(implicit trace: ZTraceElement): ZIO[R, E, Result] =
    supervisor(iterations, scopeFilter, reportProgress).use { prof =>
      zio.supervised(prof).forkManaged.use_(prof.value)
    }

  def supervisor(
    iterations: Int = 80,
    scopeFilter: ZTraceElement => Boolean = (t => !(t.toString.startsWith("zio.internal"))),
    reportProgress: Boolean = true
  )(implicit trace: ZTraceElement): ZManaged[Has[Clock], Nothing, Supervisor[Result]] = {
    import internal._

    val fibers       = new ConcurrentHashMap[Fiber.Id, FiberState]()
    val activeFibers = ConcurrentHashMap.newKeySet[Fiber.Id]()
    val globalDelay  = new AtomicLong(0)

    @volatile
    var currentExperimentId = 0

    val experiments = new VolatileArray[Experiment](iterations)

    val lifeTimeSamples = new ConcurrentHashMap[ZTraceElement, Long]()

    def inProgress() =
      currentExperimentId < iterations

    def log(msg: => String) =
      if (reportProgress) {
        println(s"CausalProfiler: $msg")
      }

    def calculateResult(): Result =
      Result(experiments.toList.map(_.toResult()), Samples(lifeTimeSamples.asScala.toMap))

    def delayFiber(state: FiberState) = {
      val delay = globalDelay.get() - state.localDelay.get()
      if (delay > 0) {
        val before = java.lang.System.nanoTime()
        Thread.sleep(delay / 1000000, (delay % 1000000).toInt)
        val after = java.lang.System.nanoTime()
        state.localDelay.addAndGet(after - before)
      }
      ()
    }

    def getCurrentExperiment() = {
      val expId = currentExperimentId
      if (expId < iterations) {
        experiments(currentExperimentId)
      } else null
    }

    val tracker = new internal.Tracker {
      def progressPoint(name: String): Unit = {
        val exp = getCurrentExperiment()
        if (exp ne null) {
          exp.addProgressPointMeasurement(name)
        }
      }
    }

    GlobalTrackerRef.get.toManaged_.flatMap { oldTracker =>
      ZManaged.make(GlobalTrackerRef.set(tracker))(_ => GlobalTrackerRef.set(oldTracker)).zipRight {
        Promise.make[Nothing, Result].toManaged_.flatMap { valuePromise =>
          val samplingLoop = ZIO.effectTotal {
            if (inProgress()) {

              val now   = java.lang.System.nanoTime()
              var expId = currentExperimentId
              var exp   = experiments(expId)

              if ((exp ne null) && now > exp.endTime) {
                log(s"Completed experiment ${expId + 1}/$iterations")
                if (expId == (iterations - 1)) {
                  valuePromise.unsafeDone {
                    ZIO.effectTotal {
                      calculateResult()
                    }
                  }
                }
                expId += 1
                currentExperimentId = expId
              }

              if (inProgress()) {
                for (id <- activeFibers.asScala) {
                  val state = fibers.get(id)
                  if ((state ne null) && !state.suspended) {
                    val location = state.location

                    if ((location ne null) && scopeFilter(location)) {
                      lifeTimeSamples.compute(location, (_, v) => v + 1)
                      if (exp == null) {
                        exp = if (expId > 0) {
                          val previous = experiments(expId - 1)
                          Experiment.fromPrevious(previous, now, location)
                        } else {
                          Experiment.start(now, ExperimentMinTime, location)
                        }
                        experiments.update(expId, exp)
                      }
                    }

                    if ((exp ne null) && exp.inProgressAt(now) && state.location == exp.candidate) {
                      val delayAmount = (exp.speedUp * SamplingNanos).toLong
                      state.localDelay.addAndGet(delayAmount)
                      globalDelay.addAndGet(delayAmount)
                      exp.trackDelay(delayAmount)
                      ()
                    }
                  }
                }
              }
            }
          }

          samplingLoop.repeat(Schedule.spaced(SamplingNanos.nanos)).forkManaged.as {
            new Supervisor[Result] {

              def value(implicit trace: ZTraceElement): UIO[Result] = valuePromise.await

              def unsafeOnStart[R, E, A](
                environment: R,
                effect: ZIO[R, E, A],
                parent: Option[Fiber.Runtime[Any, Any]],
                fiber: Fiber.Runtime[E, A]
              ): Unit = {
                val delay =
                  parent.flatMap(f => Option(fibers.get(f.id)).map(_.localDelay.get())).getOrElse(globalDelay.get())

                fibers.put(fiber.id, FiberState.initial(delay))
                ()
              }

              def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
                fibers.remove(fiber.id)
                ()
              }

              override def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = {
                val state = fibers.get(fiber.id)
                if (state ne null) {
                  state.location = effect.trace
                  if (DelayImmediatelyOn.contains(effect.tag)) {
                    delayFiber(state)
                  }
                }
              }

              override def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
                val state = fibers.get(fiber.id)
                if (state ne null) {
                  delayFiber(state)
                  state.location = null.asInstanceOf[ZTraceElement]
                  state.suspended = true
                  state.preSuspendGlobalDelay = globalDelay.get()
                }
                activeFibers.remove(fiber.id)
                ()
              }

              override def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit = {
                val state = fibers.get(fiber.id)
                if (state ne null) {
                  if (state.suspended) {
                    state.suspended = false
                    state.localDelay.addAndGet(globalDelay.get() - state.preSuspendGlobalDelay)
                    state.preSuspendGlobalDelay = 0
                  }
                } else {
                  fibers.put(fiber.id, FiberState.initial(globalDelay.get()))
                  ()
                }
                activeFibers.add(fiber.id)
                ()
              }
            }
          }
        }
      }
    }
  }

  private[CausalProfiler] object internal {
    final val SamplingNanos          = 1000000
    final val ExperimentMinTime      = 500000000L
    final val ExperimentTargetDelta  = 15
    final val ExperimentCoolOff: Int = SamplingNanos * 2
    final val ZeroSpeedupWeight      = 10
    final val MaxConsideredSpeedUp   = 100

    // tags that we need to execute delays before
    // anything that can wake a another fiber should be here
    val DelayImmediatelyOn: Set[Int] = {
      import ZIO.Tags._
      Set(
        FlatMap,
        Fold,
        Ensuring,
        Succeed,
        SucceedWith,
        Suspend,
        SuspendWith,
        Async,
        RaceWith
      )
    }

    val GlobalTrackerRef: FiberRef[Tracker] =
      Runtime.default.unsafeRun {
        FiberRef.make[internal.Tracker](Tracker.NoOp)
      }

    trait Tracker {
      def progressPoint(name: String): Unit
    }

    object Tracker {
      object NoOp extends Tracker {
        def progressPoint(name: String): Unit = ()
      }
    }

    final class VolatileArray[A: ClassTag](val length: Int) {

      private[this] val array = new Array[A](length)

      @volatile
      private[this] var marker = 0;

      def apply(i: Int): A           = { marker; array(i) }
      def update(i: Int, x: A): Unit = { array(i) = x; marker = 0 }
      def toList: List[A]            = { marker; array.toList }
    }

    final class FiberState(
      val localDelay: AtomicLong,
      @volatile var location: ZTraceElement,
      @volatile var suspended: Boolean,
      @volatile var preSuspendGlobalDelay: Long
    )

    object FiberState {
      def initial(localDelay: Long) =
        new FiberState(
          new AtomicLong(localDelay),
          null.asInstanceOf[ZTraceElement],
          false,
          0
        )
    }

    final class Experiment(
      val candidate: ZTraceElement,
      val startTime: Long,
      val duration: Long,
      val speedUp: Float,
      private val progressPoints: ConcurrentHashMap[String, Int],
      @volatile private[this] var delays: Long,
      @volatile private[this] var effectiveDuration: Long
    ) {

      val endTime: Long =
        startTime + duration

      def trackDelay(amount: Long): Unit = {
        delays += 1
        effectiveDuration -= amount
        ()
      }

      def inProgressAt(time: Long): Boolean =
        (time >= startTime && time < endTime)

      def addProgressPointMeasurement(name: String): Unit = {
        val now = java.lang.System.nanoTime()
        if (now >= startTime && now < endTime) {
          progressPoints.compute(name, (_, v) => v + 1)
          ()
        }
      }

      def toResult(): ExperimentResult =
        ExperimentResult(
          candidate,
          speedUp,
          effectiveDuration,
          delays,
          progressPoints.asScala.map { case (name, delta) =>
            ThroughputData(
              name,
              delta
            )
          }.toList
        )
    }

    object Experiment {

      def start(startTime: Long, duration: Long, candidate: ZTraceElement): Experiment = {
        val speedUp = selectSpeedUp()
        new Experiment(
          candidate,
          startTime,
          duration,
          speedUp,
          new ConcurrentHashMap(),
          0,
          duration
        )
      }

      def fromPrevious(previous: Experiment, startTime: Long, candidate: ZTraceElement): Experiment = {
        val previousDeltas = previous.progressPoints.values().asScala

        val minDelta = if (previousDeltas.nonEmpty) previousDeltas.min else 0

        val nextDuration =
          if (minDelta < ExperimentTargetDelta) {
            previous.duration * 2
          } else if (minDelta >= ExperimentTargetDelta * 2 && previous.duration >= ExperimentMinTime * 2) {
            previous.duration / 2
          } else {
            previous.duration
          }

        Experiment.start(startTime + ExperimentCoolOff, nextDuration, candidate)
      }

    }

    def selectSpeedUp(): Float =
      if (ScRandom.nextInt(ZeroSpeedupWeight) == 0) 0L
      else (ScRandom.nextInt(MaxConsideredSpeedUp) + 1).toFloat / 100

  }
}
