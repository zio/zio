package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio.stream.{ ZSink, ZStream }
import scalaz.zio.{ Promise, Task, UIO, ZIO }

package object reactiveStreams {

  final implicit class streamToPublisher[R, E <: Throwable, A](val stream: ZStream[R, E, A]) extends AnyVal {

    /**
     * Create a `Publisher` from a `Stream`. Every time the `Publisher` is subscribed to, a new instance of the `Stream`
     * is run.
     */
    def toPublisher: ZIO[R, Nothing, Publisher[A]] =
      Adapters.streamToPublisher(stream)
  }

  final implicit class sinkToSubscriber[R, E <: Throwable, A0, A, B](val sink: ZSink[R, E, A0, A, B]) extends AnyVal {

    /**
     * Create a `Subscriber` from a `Sink`. Returns a subscriber producing to the Sink and a `Task` of the value
     * produced by the Sink or any error either produced by the Sink or signaled to the subscriber.
     * @param qSize The size used as internal buffer. If possible, set to a power of 2 value for best performance.
     */
    def toSubscriber(qSize: Int = 16): ZIO[R, Nothing, (Subscriber[A], Task[B])] =
      Adapters.sinkToSubscriber(sink, qSize)
  }

  final implicit class publisherToStream[A](val publisher: Publisher[A]) extends AnyVal {

    /**
     * Create a `Stream` from a `Publisher`.
     * @param qSize The size used as internal buffer. If possible, set to a power of 2 value for best performance.
     */
    def toStream(qSize: Int = 16): ZStream[Any, Throwable, A] =
      Adapters.publisherToStream(publisher, qSize)
  }

  final implicit class subscriberToSink[A](val subscriber: Subscriber[A]) extends AnyVal {

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
    def toSink[E <: Throwable]: UIO[(Promise[E, Nothing], ZSink[Any, E, Unit, A, Unit])] =
      Adapters.subscriberToSink(subscriber)
  }

}
