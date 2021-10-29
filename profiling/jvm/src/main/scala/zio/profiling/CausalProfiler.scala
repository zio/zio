/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

package zio.profiling

import com.github.ghik.silencer.silent
import zio._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConverters._

object CausalProfiler {

  def profile[R <: Has[Clock], E](config: ProfilerConfig)(
    zio: ZIO[R, E, Any]
  )(implicit trace: ZTraceElement): ZIO[R, E, Result] =
    supervisor(config.iterations, config.scopeFilter, config.reportProgress).use { prof =>
      ZIO.runtimeConfig
        .flatMap(runtimeConfig => zio.withRuntimeConfig(RuntimeConfigAspect.superviseOperations(runtimeConfig)))
        .supervised(prof)
        .forkManaged
        .useDiscard(prof.value)
    }

  def progressPoint(name: String)(implicit trace: ZTraceElement): UIO[Unit] =
    GlobalTrackerRef.get.flatMap { tracker =>
      ZIO.succeed {
        tracker.progressPoint(name)
      }
    }

  @silent("JavaConverters")
  private def supervisor(
    iterations: Int,
    scopeFilter: ScopeFilter,
    reportProgress: Boolean
  )(implicit trace: ZTraceElement): ZManaged[Has[Clock], Nothing, Supervisor[Result]] = {

    val fibers       = new ConcurrentHashMap[FiberId, FiberState]()
    val activeFibers = ConcurrentHashMap.newKeySet[FiberId]()
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

    val tracker = new Tracker {
      def progressPoint(name: String): Unit = {
        val exp = getCurrentExperiment()
        if (exp ne null) {
          exp.addProgressPointMeasurement(name)
        }
      }
    }

    GlobalTrackerRef.get.toManaged.flatMap { oldTracker =>
      ZManaged.acquireReleaseWith(GlobalTrackerRef.set(tracker))(_ => GlobalTrackerRef.set(oldTracker)).zipRight {
        Promise.make[Nothing, Result].toManaged.flatMap { valuePromise =>
          val samplingLoop = ZIO.succeed {
            if (inProgress()) {

              val now   = java.lang.System.nanoTime()
              var expId = currentExperimentId
              var exp   = experiments(expId)

              if ((exp ne null) && now > exp.endTime) {
                log(s"Completed experiment ${expId + 1}/$iterations")
                if (expId == (iterations - 1)) {
                  valuePromise.unsafeDone {
                    ZIO.succeed {
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

                    if ((location ne null) && scopeFilter.run(location)) {
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

              def initial(localDelay: Long) =
                new FiberState(
                  new AtomicLong(localDelay),
                  null.asInstanceOf[ZTraceElement],
                  false,
                  0
                )

              def value(implicit trace: ZTraceElement): UIO[Result] = valuePromise.await

              def unsafeOnStart[R, E, A](
                environment: R,
                effect: ZIO[R, E, A],
                parent: Option[Fiber.Runtime[Any, Any]],
                fiber: Fiber.Runtime[E, A]
              ): Unit = {
                val delay =
                  parent.flatMap(f => Option(fibers.get(f.id)).map(_.localDelay.get())).getOrElse(globalDelay.get())

                fibers.put(fiber.id, initial(delay))
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
                  fibers.put(fiber.id, initial(globalDelay.get()))
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
}
