package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio._
import scalaz.zio.stream.{ Sink, Stream }

package object reactiveStreams {

  final implicit class streamToPublisher[E <: Throwable, A](val src: Stream[Any, E, A]) extends AnyVal {
    def toPublisher: UIO[Publisher[A]] =
      SourcePublisher.sinkToPublisher(src)
  }

  final implicit class sinkToSubscriber[T, A](val sink: Sink[Any, _ <: Throwable, Unit, T, A]) extends AnyVal {
    def toSubscriber(qSize: Int = 10): UIO[(Subscriber[T], Task[A])] =
      SinkSubscriber.sinkToSubscriber(sink, qSize, new SinkSubscriber[T, A](_, _, _, _))
  }
}
