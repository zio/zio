package scalaz.zio.interop

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio.Exit.Cause.{ Die, Interrupt, Fail => TFail }
import scalaz.zio.Exit.Failure
import scalaz.zio._
import scalaz.zio.stream.{ Sink, Stream, Take }
import scalaz.zio.stream.Take.{ End, Fail, Value }

package object reactiveStreams {

  final implicit class streamToPublisher[E <: Throwable, A](val src: Stream[Any, E, A]) extends AnyVal {
    def toPublisher(qSize: Int = 10): UIO[Publisher[A]] =
      ZIO.runtime.map(runtime => new StreamPublisher(src, runtime, qSize))
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

private class StreamPublisher[E <: Throwable, A](
  src: Stream[Any, E, A],
  runtime: Runtime[_],
  qSize: Int
) extends Publisher[A] {
  override def subscribe(s: Subscriber[_ >: A]): Unit = {
    if (s == null) throw new NullPointerException("Subscriber must not be null.")
    runtime.unsafeRunSync(
      src.toQueue(qSize).use[Any, Throwable, Unit] { q =>
        val subscription = new StreamSubscription[E, A](s, q, runtime)
        Task(s.onSubscribe(subscription))
      }
    ) match {
      case Failure(Die(e))    => s.onError(e)
      case Failure(TFail(e))  => s.onError(e)
      case Failure(Interrupt) => s.onComplete()
      case _                  =>
    }
  }
}

private class StreamSubscription[E <: Throwable, A](s: Subscriber[_ >: A], q: Queue[Take[E, A]], runtime: Runtime[_])
    extends Subscription {
  var completed: Boolean = false
  override def request(n: Long): Unit = {
    if (n <= 0) s.onError(new IllegalArgumentException("n must be >= 0."))
    println(s"request $n")
    runtime.unsafeRunAsync(
      Stream
        .unfold(n)(n => if (n > 0) Some((n, n - 1)) else None)
        .mapM(_ => q.take)
        .foreach {
          case Value(t) =>
            Task {
              println("onNext")
              s.onNext(t)
            }
          case Fail(e) =>
            completed = true
            Task(s.onError(e))
          case End =>
            completed = true
            Task(s.onComplete())
        }
    ) {
      case Failure(Die(e)) if !completed    => s.onError(e)
      case Failure(Interrupt) if !completed => s.onComplete()
      case _                                =>
    }
  }
  override def cancel(): Unit = {
    completed = true
    runtime.unsafeRunAsync_(q.shutdown)
  }
}
