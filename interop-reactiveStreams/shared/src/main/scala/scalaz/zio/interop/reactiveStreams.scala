package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio._
import scalaz.zio.stream.{ ZSink, ZStream }

package object reactiveStreams {

  final implicit class streamToPublisher[R, E <: Throwable, A](val stream: ZStream[R, E, A]) extends AnyVal {
    def toPublisher: ZIO[R, Nothing, Publisher[A]] =
      Adapters.streamToPublisher(stream)
  }

  final implicit class sinkToSubscriber[R, E <: Throwable, A0, A, B](val sink: ZSink[R, E, A0, A, B]) extends AnyVal {
    def toSubscriber(qSize: Int = 10): ZIO[R, Nothing, (Subscriber[A], Task[B])] =
      Adapters.sinkToSubscriber(sink, qSize)
  }

  final implicit class publisherToStream[A](val publisher: Publisher[A]) extends AnyVal {
    def toStream(qSize: Int = 10): UIO[ZStream[Any, Throwable, A]] =
      Adapters.publisherToStream(publisher, qSize)
  }

  final implicit class subscriberToSink[A](val subscriber: Subscriber[A]) extends AnyVal {
    def toSink[E <: Throwable]: UIO[(Promise[E, Unit], ZSink[Any, E, Unit, A, Unit])] =
      Adapters.subscriberToSink(subscriber)
  }

}
