/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

import zio.duration.Duration
import zio.internal.{NamedThreadFactory, OneShot, Scheduler}

import java.time.{DateTimeException, Instant, LocalDateTime, OffsetDateTime}
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicReference
import java.util.{Collections, List}
import scala.annotation.tailrec

package object clock {
  import Scheduler.CancelToken

  type Clock = Has[Clock.Service]

  object Clock extends PlatformSpecific with Serializable {

    trait Service extends Serializable {

      def asScheduledExecutorService: UIO[ScheduledExecutorService] =
        ZIO.runtime[Any].map { runtime =>
          val executor = runtime.platform.executor

          val scheduler = new zio.internal.Scheduler {
            def schedule(runnable: Runnable, duration: Duration): Scheduler.CancelToken = {
              val canceler =
                runtime.unsafeRunAsyncCancelable(sleep(duration) *> ZIO.effectTotal(runnable.run()))(_ => ())
              () => canceler(Fiber.Id.None).interrupted
            }
          }

          def compute[A](a: => A): Either[Throwable, A] =
            try {
              Right(a)
            } catch {
              case t: CancellationException => Left(t)
              case t: InterruptedException  => Left(t)
              case t: OutOfMemoryError      => throw t
              case t: Throwable             => Left(new ExecutionException(t))
            }

          def now(): Instant =
            runtime.unsafeRun(instant)

          def scheduleFuture[S, A](initial: Instant, s: S)(
            f: (Instant, S) => Option[(Instant, S)]
          )(a: => A): ScheduledFuture[A] = {

            sealed trait State

            final case class Done(result: Either[Throwable, A])                        extends State
            final case class Running(cancelToken: Scheduler.CancelToken, end: Instant) extends State
            final case class Scheduling(end: Instant)                                  extends State

            val state = new AtomicReference[State](Scheduling(initial))
            val latch = new CountDownLatch(1)

            def loop(end: Instant, s: S): Unit = {
              val updatedState = state.updateAndGet {
                case Done(result) => Done(result)
                case _            => Scheduling(end)
              }
              if (updatedState == Scheduling(end)) {
                val start    = now()
                val interval = Duration.fromInterval(start, end)
                val cancelToken = scheduler.schedule(
                  () =>
                    executor.submitOrThrow { () =>
                      compute(a) match {
                        case Left(t) =>
                          state.set(Done(Left(t)))
                          latch.countDown()
                        case Right(a) =>
                          val end = now()
                          f(end, s) match {
                            case Some((interval, s)) =>
                              loop(interval, s)
                            case None =>
                              state.set(Done(Right(a)))
                              latch.countDown()
                          }
                      }
                    },
                  interval
                )
                val currentState = state.getAndUpdate {
                  case Scheduling(end) => Running(cancelToken, end)
                  case state           => state
                }
                if (currentState != Scheduling(end)) {
                  cancelToken()
                  ()
                }
              }
            }

            loop(initial, s)

            new ScheduledFuture[A] { self =>
              def compareTo(that: Delayed): Int =
                if (self eq that) 0
                else self.getDelay(TimeUnit.NANOSECONDS).compareTo(that.getDelay(TimeUnit.NANOSECONDS))
              def getDelay(unit: TimeUnit): Long =
                state.get match {
                  case Done(_) => 0L
                  case Running(_, end) =>
                    unit.convert(Duration.fromInterval(now(), end).toMillis, TimeUnit.MILLISECONDS)
                  case Scheduling(end) =>
                    unit.convert(Duration.fromInterval(now(), end).toMillis, TimeUnit.MILLISECONDS)
                }
              def cancel(mayInterruptIfRunning: Boolean): Boolean = {
                val currentState = state.getAndUpdate {
                  case Done(result) => Done(result)
                  case _            => Done(Left(new InterruptedException))
                }
                currentState match {
                  case Done(_)                 => false
                  case Running(cancelToken, _) => cancelToken()
                  case Scheduling(_)           => true
                }
              }
              def isCancelled(): Boolean =
                state.get match {
                  case Done(Left(_: InterruptedException)) => true
                  case _                                   => false
                }
              def isDone(): Boolean =
                latch.getCount() == 0L
              def get(): A = {
                latch.await()
                state.get match {
                  case Done(result) => result.fold(t => throw t, identity)
                  case _            => throw new Error(s"Defect in zio.internal.Timer#asScheduledExecutorService")
                }
              }
              def get(timeout: Long, unit: TimeUnit): A = {
                latch.await(timeout, unit)
                state.get match {
                  case Done(result) => result.fold(t => throw t, identity)
                  case _            => throw new Error(s"Defect in zio.internal.Timer#asScheduledExecutorService")
                }
              }
            }
          }

          new AbstractExecutorService with ScheduledExecutorService {
            def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
              false
            def execute(command: Runnable): Unit = {
              executor.submit(command)
              ()
            }
            def isShutdown(): Boolean =
              false
            def isTerminated(): Boolean =
              false
            def schedule(runnable: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[Any] = {
              val start = now()
              val end   = start.plus(Duration(delay, unit))
              scheduleFuture(end, ())((_, _) => None)(runnable.run())
            }
            def schedule[A](callable: Callable[A], delay: Long, unit: TimeUnit): ScheduledFuture[A] = {
              val end = now().plus(Duration(delay, unit))
              scheduleFuture(end, ())((_, _) => None)(callable.call())
            }
            def scheduleAtFixedRate(
              runnable: Runnable,
              initialDelay: Long,
              period: Long,
              unit: TimeUnit
            ): ScheduledFuture[Any] = {
              val start = now()
              val end   = start.plus(Duration(initialDelay, unit))

              @tailrec
              def loop(now: Instant, start: Instant): Option[(Instant, Instant)] = {
                val end = start.plus(Duration(period, unit))
                if (now.compareTo(end) < 0) Some((start, end))
                else loop(now, end)
              }

              scheduleFuture(end, end.plus(Duration(period, unit)))(loop)(runnable.run())
            }
            def scheduleWithFixedDelay(
              runnable: Runnable,
              initialDelay: Long,
              delay: Long,
              unit: TimeUnit
            ): ScheduledFuture[Any] = {
              val start = now()
              val end   = start.plus(Duration(initialDelay, unit))
              scheduleFuture(end, ())((now, _) => Some((now.plus(Duration(delay, unit)), ())))(runnable.run())
            }
            def shutdown(): Unit =
              ()
            def shutdownNow(): List[Runnable] =
              Collections.emptyList[Runnable]
          }
        }

      def currentTime(unit: TimeUnit): UIO[Long]

      // Could be UIO. We keep IO to preserve binary compatibility.
      def currentDateTime: IO[DateTimeException, OffsetDateTime]

      // The implementation is only here to preserve binary compatibility.
      def instant: UIO[java.time.Instant] = currentTime(TimeUnit.MILLISECONDS).map(java.time.Instant.ofEpochMilli(_))

      // This could be a UIO. We keep IO to preserve binary compatibility.
      // The implementation is only here to preserve binary compatibility.
      def localDateTime: IO[DateTimeException, java.time.LocalDateTime] = currentDateTime.map(_.toLocalDateTime())

      def nanoTime: UIO[Long]

      def sleep(duration: Duration): UIO[Unit]
    }

    object Service {
      val live: Service = new Service {
        override val asScheduledExecutorService: UIO[ScheduledExecutorService] =
          ZIO.executor.map { executor =>
            new AbstractExecutorService with ScheduledExecutorService {
              def awaitTermination(timeout: Long, unit: TimeUnit): Boolean =
                service.awaitTermination(timeout, unit)
              def execute(command: Runnable): Unit =
                service.execute(() => executor.submitOrThrow(command))
              def isShutdown(): Boolean =
                service.isShutdown()
              def isTerminated(): Boolean =
                service.isTerminated()
              def schedule(runnable: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture[_] =
                service.schedule(
                  new Runnable {
                    override def run: Unit =
                      executor.submitOrThrow(runnable)
                  },
                  delay,
                  unit
                )
              def schedule[A](callable: Callable[A], delay: Long, unit: TimeUnit): ScheduledFuture[A] =
                service.schedule(
                  new Callable[A] {
                    val oneShot = OneShot.make[A]
                    executor.submitOrThrow(() => oneShot.set(callable.call()))
                    def call(): A = oneShot.get()
                  },
                  delay,
                  unit
                )
              def scheduleAtFixedRate(
                runnable: Runnable,
                initialDelay: Long,
                period: Long,
                unit: TimeUnit
              ): ScheduledFuture[_] =
                service.scheduleAtFixedRate(() => executor.submitOrThrow(runnable), initialDelay, period, unit)
              def scheduleWithFixedDelay(
                runnable: Runnable,
                initialDelay: Long,
                delay: Long,
                unit: TimeUnit
              ): ScheduledFuture[_] =
                service.scheduleWithFixedDelay(() => executor.submitOrThrow(runnable), initialDelay, delay, unit)
              def shutdown(): Unit =
                service.shutdown()
              def shutdownNow(): java.util.List[Runnable] =
                service.shutdownNow()
            }
          }

        def currentTime(unit: TimeUnit): UIO[Long] =
          instant.map { inst =>
            // A nicer solution without loss of precision or range would be
            // unit.toChronoUnit.between(Instant.EPOCH, inst)
            // However, ChronoUnit is not available on all platforms
            unit match {
              case TimeUnit.NANOSECONDS =>
                inst.getEpochSecond() * 1000000000 + inst.getNano()
              case TimeUnit.MICROSECONDS =>
                inst.getEpochSecond() * 1000000 + inst.getNano() / 1000
              case _ => unit.convert(inst.toEpochMilli(), TimeUnit.MILLISECONDS)
            }
          }

        val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime)

        def sleep(duration: Duration): UIO[Unit] =
          UIO.effectAsyncInterrupt { cb =>
            val canceler = globalScheduler.schedule(() => cb(UIO.unit), duration)
            Left(UIO.effectTotal(canceler()))
          }

        def currentDateTime: IO[DateTimeException, OffsetDateTime] =
          ZIO.effectTotal(OffsetDateTime.now())

        override def instant: zio.UIO[Instant] =
          ZIO.effectTotal(Instant.now())

        override def localDateTime: zio.IO[DateTimeException, LocalDateTime] =
          ZIO.effectTotal(LocalDateTime.now())

      }
    }

    val any: ZLayer[Clock, Nothing, Clock] =
      ZLayer.requires[Clock]

    val live: Layer[Nothing, Clock] =
      ZLayer.succeed(Service.live)

    private[this] val service = makeService()

    private[clock] val globalScheduler = new Scheduler {

      private[this] val ConstFalse = () => false

      override def schedule(task: Runnable, duration: Duration): CancelToken = (duration: @unchecked) match {
        case Duration.Infinity => ConstFalse
        case Duration.Finite(_) =>
          val future = service.schedule(
            new Runnable {
              def run: Unit =
                task.run()
            },
            duration.toNanos,
            TimeUnit.NANOSECONDS
          )

          () => {
            val canceled = future.cancel(true)

            canceled
          }
      }
    }

    private[this] def makeService(): ScheduledExecutorService = {
      val service = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("zio-timer", true))
      service.setRemoveOnCancelPolicy(true)
      service
    }
  }

  /**
   * Views this `Clock` as a Java `ScheduledExecutorService`.
   */
  val asScheduledExecutorService: URIO[Clock, ScheduledExecutorService] =
    ZIO.accessM(_.get.asScheduledExecutorService)

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  def currentTime(unit: => TimeUnit): URIO[Clock, Long] =
    ZIO.accessM(_.get.currentTime(unit))

  /**
   * Get the current time, represented in the current timezone.
   */
  val currentDateTime: ZIO[Clock, DateTimeException, OffsetDateTime] =
    ZIO.accessM(_.get.currentDateTime)

  val instant: ZIO[Clock, Nothing, java.time.Instant] =
    ZIO.accessM(_.get.instant)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  val nanoTime: URIO[Clock, Long] =
    ZIO.accessM(_.get.nanoTime)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  def sleep(duration: => Duration): URIO[Clock, Unit] =
    ZIO.accessM(_.get.sleep(duration))

}
