package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio.Exit.Cause.{ Die, Interrupt, Fail => TFail }
import scalaz.zio.Exit.{ Failure, Success }
import scalaz.zio._
import scalaz.zio.stream.{ Sink, Stream, Take }

package object reactiveStreams {

  final implicit class streamToPublisher[E <: Throwable, A](val src: Stream[Any, E, A]) extends AnyVal {
    def toPublisher(): UIO[Publisher[A]] =
      ZIO.runtime.map(
        runtime =>
          (s: Subscriber[_ >: A]) => {
            if (s == null) throw new NullPointerException("Subscriber must not be null.")
            val wiring =
              for {
                q <- Queue.unbounded[Long]
                control = Stream
                  .fromQueue(q)
                  .flatMap(n => Stream.unfold(n)(n => if (n > 0) Some(((), n - 1)) else None))
                _ <- src
                      .toQueue(1)
                      .use {
                        q =>
                          Stream
                            .fromQueue(q)
                            .peel(Sink.readWhile[Take[E, A]](_.isInstanceOf[Take.Fail[E]]))
                            .use {
                              case (errors, stream) =>
                                errors.headOption match {
                                  case Some(Take.Fail(e)) => Task(s.onError(e))
                                  case _ =>
                                    stream
                                      .zipWith(control) {
                                        case (Some(Take.Value(a)), Some(_)) =>
                                          s.onNext(a)
                                          Some(())
                                        case (Some(Take.Fail(e)), _) =>
                                          s.onError(e)
                                          None
                                        case (Some(Take.End), _) =>
                                          s.onComplete()
                                          None
                                        case _ => None
                                      }
                                      .run(Sink.drain)
                                }
                            }
                      }
                      .fork
                subscription = new Subscription {
                  override def request(n: Long): Unit = {
                    if (n <= 0) s.onError(new IllegalArgumentException("n must be > 0"))
                    runtime.unsafeRunAsync_(q.offer(n).void)
                  }
                  override def cancel(): Unit = runtime.unsafeRun(q.shutdown)
                }
              } yield subscription
            runtime.unsafeRunAsync(wiring) {
              case Success(subscription) => s.onSubscribe(subscription)
              case Failure(Die(e))       => s.onError(e)
              case Failure(TFail(e))     => s.onError(e)
              case Failure(Interrupt)    => s.onComplete()
              case _                     =>
            }
        }
      )
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
            // todo: more intelligent requesting without blocking and infinite.
            override def onSubscribe(s: Subscription): Unit = {
              runtime.unsafeRunAsync(q.awaitShutdown *> Task(s.cancel()))(_ => ())
              s.request(Long.MaxValue)
            }
            override def onNext(t: T): Unit          = runtime.unsafeRun(q.offer(t).void)
            override def onError(t: Throwable): Unit = runtime.unsafeRun(p.fail(t) *> q.shutdown)
            override def onComplete(): Unit          = runtime.unsafeRun(q.shutdown)
          }
        (subscriber, p.await)
      }
  }
}
