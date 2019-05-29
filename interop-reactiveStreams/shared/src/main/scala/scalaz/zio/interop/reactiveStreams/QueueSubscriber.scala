package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.ZStream
import scalaz.zio.stream.ZStream.Fold
import scalaz.zio.{ Promise, Queue, Runtime, UIO, ZIO }

private[reactiveStreams] object QueueSubscriber {

  def make[A](capacity: Int): ZIO[Any, Nothing, (Subscriber[A], ZStream[Any, Throwable, A])] =
    for {
      runtime      <- UIO.runtime[Any]
      q            <- Queue.bounded[A](capacity)
      subscription <- Promise.make[Nothing, Subscription]
      completion   <- Promise.make[Throwable, Unit]
    } yield (subscriber(runtime, q, subscription, completion), stream(q, subscription, completion))

  private def stream[A](
    q: Queue[A],
    subscription: Promise[Nothing, Subscription],
    completion: Promise[Throwable, Unit]
  ): ZStream[Any, Throwable, A] =
    /*
     * Unfold q. When `onComplete` or `onError` is signalled, take the remaining values from `q`, then shut down.
     * `onComplete` or `onError` always are the last signal if they occur. We optimistically take from `q` and rely on
     * interruption in case that they are signalled while we wait. `forkQShutdownHook` ensures that `take` is
     * interrupted in case `q` is empty while `onComplete` or `onError` is signalled.
     * When we see `completion.done` after `loop`, the `Publisher` has signalled `onComplete` or `onError` and we are
     * done. Otherwise the stream has completed before and we need to cancel the subscription.
     */
    new ZStream[Any, Throwable, A] {
      private val capacity: Long = q.capacity.toLong
      private def forkQShutdownHook =
        completion.await.ensuring(q.size.flatMap(n => if (n <= 0) q.shutdown else UIO.unit)).fork

      override def fold[R1 <: Any, E1 >: Throwable, A1 >: A, S]: Fold[R1, E1, A1, S] =
        forkQShutdownHook *> subscription.await.map { sub => (s: S, cont: S => Boolean, f: (S, A1) => ZIO[R1, E1, S]) =>
          def loop(s: S, demand: Long): ZIO[R1, E1, S] =
            if (!cont(s)) UIO.succeed(s)
            else {
              def requestAndLoop = UIO(sub.request(capacity - demand)) *> loop(s, capacity)
              def takeAndLoop    = q.take.flatMap(f(s, _)).flatMap(loop(_, demand - 1))
              def completeWithS  = completion.await.const(s)
              q.size.flatMap { n =>
                if (n <= 0) completion.isDone.flatMap {
                  case true                       => completeWithS
                  case false if demand < capacity => requestAndLoop
                  case false                      => takeAndLoop
                } else takeAndLoop
              } <> completeWithS
            }
          loop(s, 0).ensuring(UIO(sub.cancel()).whenM(completion.isDone.map(!_)) *> q.shutdown)
        }.onInterrupt(q.shutdown)
    }

  private def subscriber[A](
    runtime: Runtime[_],
    q: Queue[A],
    subscription: Promise[Nothing, Subscription],
    completion: Promise[Throwable, Unit]
  ): Subscriber[A] = new Subscriber[A] {

    override def onSubscribe(s: Subscription): Unit = {
      if (s == null) throw new NullPointerException("s was null in onSubscribe")
      runtime.unsafeRun(subscription.succeed(s).flatMap {
        // `whenM(q.isShutdown)`, the Stream has been interrupted or completed before we received `onSubscribe`
        case true  => UIO(s.cancel()).whenM(q.isShutdown)
        case false => UIO(s.cancel())
      })
    }

    override def onNext(t: A): Unit = {
      if (t == null) throw new NullPointerException("t was null in onNext")
      runtime.unsafeRunSync(q.offer(t))
      ()
    }

    override def onError(e: Throwable): Unit = {
      if (e == null) throw new NullPointerException("t was null in onError")
      runtime.unsafeRun(completion.fail(e).unit)
    }

    override def onComplete(): Unit = runtime.unsafeRun(completion.succeed(()).unit)
  }
}
