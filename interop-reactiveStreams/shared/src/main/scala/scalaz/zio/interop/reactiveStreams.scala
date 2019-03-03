package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio._
import scalaz.zio.stream.{ Sink, Stream, Take }

package object reactiveStreams {

  final implicit class streamToPublisher[E <: Throwable, A](val src: Stream[Any, E, A]) extends AnyVal {
    def toPublisher(): UIO[Publisher[A]] =
      ZIO.runtime.map { runtime => (s: Subscriber[_ >: A]) =>
        {
          if (s == null) throw new NullPointerException("Subscriber must not be null.")
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
              _       <- src.toQueue(1).use(takesToCallbacks(_, control)).fork
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
  }

  final implicit class sinkToSubscriber[T, A](val sink: Sink[Any, _ <: Throwable, Unit, T, A]) extends AnyVal {
    def toSubscriber(qSize: Int = 10): UIO[(Subscriber[T], Task[A])] =
      for {
        runtime <- ZIO.runtime[Any]
        q       <- Queue.bounded[T](qSize)
        p       <- Promise.make[Throwable, A]
        _       <- p.done(Stream.fromQueue(q).run(sink)).fork
      } yield {
        val subscriber =
          new Subscriber[T] {
            var subscribed = false
            // todo: more intelligent requesting without blocking and infinite.
            override def onSubscribe(s: Subscription): Unit =
              if (subscribed) {
                s.cancel()
              } else {
                subscribed = true
                runtime.unsafeRunAsync(q.awaitShutdown *> Task(s.cancel()))(_ => ())
                s.request(Long.MaxValue) // FIXME
              }
            override def onNext(t: T): Unit = {
              if (t == null) throw new NullPointerException("t was null in onNext")
              runtime.unsafeRun(q.offer(t).void)
            }
            override def onError(t: Throwable): Unit = {
              if (t == null) throw new NullPointerException("t was null in onError")
              runtime.unsafeRun(p.fail(t) *> q.shutdown)
            }
            override def onComplete(): Unit =
              runtime.unsafeRun(q.shutdown)
          }
        (subscriber, p.await)
      }
  }
}
