package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio.stream.{ ZSink, ZStream }
import scalaz.zio.{ Promise, Task, UIO, ZIO }

package object reactiveStreams {

  /**
   * Create a `Publisher` from a `Stream`.
   */
  final implicit class streamToPublisher[R, E <: Throwable, A](val stream: ZStream[R, E, A]) extends AnyVal {
    def toPublisher: ZIO[R, Nothing, Publisher[A]] =
      Adapters.streamToPublisher(stream)
  }

  /**
   * Create a `Subscriber` from a `Sink`. Returns a subscriber producing to the Sink and a `Task` of the value
   * produced by the Sink or any error either produced by the Sink or signaled to the subscriber.
   */
  final implicit class sinkToSubscriber[R, E <: Throwable, A0, A, B](val sink: ZSink[R, E, A0, A, B]) extends AnyVal {
    def toSubscriber(qSize: Int = 16): ZIO[R, Nothing, (Subscriber[A], Task[B])] =
      Adapters.sinkToSubscriber(sink, qSize)
  }

  /**
   * Create a `Stream` from a `Publisher`.
   */
  final implicit class publisherToStream[A](val publisher: Publisher[A]) extends AnyVal {
    def toStream(qSize: Int = 16): ZStream[Any, Throwable, A] =
      Adapters.publisherToStream(publisher, qSize)
  }

  /**
   * Create a `Sink` from a `Subscriber`. Errors need to be transported via the returned Promise:
   *
   * ```
   * val subscriber: Subscriber[Int] = ???
   * val stream: Stream[Any, Throwable, Int] = ???
   * for {
   *   sinkError <- subscriberToSink(subscriber)
   *   (error, sink) = sinkError
   *   _ <- stream.run(sink).catchAll(e => error.fail(e)).fork
   * } yield ()
   * ```
   */
  final implicit class subscriberToSink[A](val subscriber: Subscriber[A]) extends AnyVal {
    def toSink[E <: Throwable]: UIO[(Promise[E, Nothing], ZSink[Any, E, Unit, A, Unit])] =
      Adapters.subscriberToSink(subscriber)
  }

}
