package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio.{ Queue, Runtime, Task, UIO, ZIO }
import scalaz.zio.stream.{ Sink, Stream, Take }

class SourcePublisher[E <: Throwable, A](src: Stream[Any, E, A], runtime: Runtime[Any]) extends Publisher[A] {
  override def subscribe(s: Subscriber[_ >: A]): Unit =
    if (s == null) {
      throw new NullPointerException("Subscriber must not be null.")
    } else {
      def takesToCallbacks(q: Queue[Take[E, A]], control: Stream[Any, Nothing, Unit]): Task[Unit] =
        Stream
          .fromQueue(q)
          .peel(Sink.readWhile[Take[E, A]](_.isInstanceOf[Take.Fail[E]]))
          .use {
            case (Take.Fail(e) :: _, _) => Task(s.onError(e))
            case (_, stream) =>
              stream
                .zip(control)
                .foreach {
                  case (Take.Value(a), _) => Task(s.onNext(a))
                  case (Take.Fail(e), _)  => Task(s.onError(e))
                  case (Take.End, _)      => Task(s.onComplete())
                }
          }
      val wiring =
        for {
          q       <- Queue.unbounded[Long]
          control = Stream.fromQueue(q).flatMap(n => Stream.unfold(n)(n => if (n > 0) Some(((), n - 1)) else None))
          _       <- src.toQueue().use(takesToCallbacks(_, control)).fork
        } yield {
          val subscription = new Subscription {
            override def request(n: Long): Unit = {
              if (n <= 0) s.onError(new IllegalArgumentException("n must be > 0"))
              runtime.unsafeRunAsync_(q.offer(n).void)
            }
            override def cancel(): Unit = runtime.unsafeRun(q.shutdown)
          }
          s.onSubscribe(subscription)
        }
      runtime.unsafeRunAsync_(wiring)
    }
  }

object SourcePublisher {
  def sinkToPublisher[E <: Throwable, A](src: Stream[Any, E, A]): UIO[SourcePublisher[E, A]] =
    ZIO.runtime.map(new SourcePublisher(src, _))
}
