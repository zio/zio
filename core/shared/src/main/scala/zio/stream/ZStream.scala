package zio

import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.{util => jutil}
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

trait ZStream[+R, +E, +A] { self =>

  /**
   * Maps each element of this stream to a `ZIO`, executes the `ZIO`s
   * in parallel up to the specified maximum number of concurrent
   * fibers, and emits the results in the same order as the source
   * stream.
   *
   * The `bufferSize` parameter controls the maximum number of
   * elements from the stream that can be "in flight" (i.e., being
   * processed by a `ZIO` function) at any given time. This includes
   * both elements that are actively being processed and those that
   * are queued for processing.
   *
   * By default, the buffer size is set to the maximum of
   * `parallelism` and 16 to ensure that the desired level of
   * parallelism can be achieved. However, users can explicitly
   * specify a smaller buffer size if needed, understanding that this
   * may limit the actual parallelism.
   *
   * @param parallelism
   *   the maximum number of `ZIO` actions that can be executed
   *   concurrently
   * @param bufferSize
   *   the maximum number of elements that can be in flight
   *   simultaneously (defaults to `max(parallelism, 16)`)
   * @param f
   *   function to apply to each element
   * @tparam R1
   *   environment type of the resulting `ZIO` actions
   * @tparam E1
   *   error type of the resulting `ZIO` actions
   * @tparam B
   *   result type of the resulting `ZIO` actions
   * @return
   *   a new stream with the results of applying `f` to each element
   */
  def mapZIOPar[R1 <: R, E1 >: E, B](
    parallelism: Int
  )(f: A => ZIO[R1, E1, B]): ZStream[R1, E1, B] =
    mapZIOParWithBufferStrategy(parallelism, ZStream.DefaultStrategy, ZStream.defaultBufferSize(parallelism))(f)

  /**
   * Maps each element of this stream to a `ZIO`, executes the `ZIO`s
   * in parallel up to the specified maximum number of concurrent
   * fibers, and emits the results in the same order as the source
   * stream.
   *
   * This method allows specifying a custom buffer strategy and
   * buffer size.
   *
   * @param parallelism
   *   the maximum number of `ZIO` actions that can be executed
   *   concurrently
   * @param bufferStrategy
   *   strategy to use when the buffer is full
   * @param bufferSize
   *   the maximum number of elements that can be in flight
   *   simultaneously
   * @param f
   *   function to apply to each element
   * @tparam R1
   *   environment type of the resulting `ZIO` actions
   * @tparam E1
   *   error type of the resulting `ZIO` actions
   * @tparam B
   *   result type of the resulting `ZIO` actions
   * @return
   *   a new stream with the results of applying `f` to each element
   */
  def mapZIOParWithBufferStrategy[R1 <: R, E1 >: E, B](
    parallelism: Int,
    bufferStrategy: ZSink.PullStrategy,
    bufferSize: Int
  )(f: A => ZIO[R1, E1, B]): ZStream[R1, E1, B] = {
    require(parallelism > 0, s"Parallelism must be positive, but got $parallelism")
    require(bufferSize > 0, s"Buffer size must be positive, but got $bufferSize")

    ZStream.scoped {
      for {
        output     <- Queue.bounded[Exit[E1, B]](bufferSize)
        permit     <- Semaphore.make(parallelism.toLong)
        shutdown   <- Promise.make[E1, Unit]
        running    <- Ref.make(0)
        offerError = (e: E1) => output.offer(Exit.fail(e)).unit
        awaitDone = {
          val awaitAllDone = running.get.flatMap { runningCount =>
            if (runningCount == 0) ZIO.unit
            else {
              // We can't await on `running`, because it may never reach 0 if the stream is infinite.
              // Instead, we rely on the fact that `output` will be shutdown when the stream ends.
              ZIO.never
            }
          }
          shutdown.await *> awaitAllDone
        }
        processElement = { (a: A) =>
          running.modify { count =>
            if (count == 0 && shutdown.isDone) (true, count)
            else {
              val nextCount = count + 1
              (false, nextCount)
            }
          }.flatMap {
            case true  => ZIO.unit
            case false => permit.withPermit(f(a).exit).flatMap(output.offer(_).unit).ensuring(running.update(_ - 1))
          }
        }
        _ <- (self foreach processElement)
               .ensuring(output.shutdown)
               .catchAll { e =>
                 offerError(e) *> shutdown.fail(e)
               }
               .forkDaemon
      } yield output.take.await.repeatWhile(_.isFailure).ensuring(awaitDone)
    }
  }
}

object ZStream {
  private[zio] def defaultBufferSize(parallelism: Int): Int =
    math.max(parallelism, 16)
}