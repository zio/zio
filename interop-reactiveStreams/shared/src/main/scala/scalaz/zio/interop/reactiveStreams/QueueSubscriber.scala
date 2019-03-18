package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.ZStream
import scalaz.zio.stream.ZStream.Fold
import scalaz.zio.{ IO, Queue, Runtime, Task, UIO, ZIO }

private[reactiveStreams] object QueueSubscriber {
  def make[A](capacity: Int): ZIO[Any, Nothing, (Subscriber[A], ZStream[Any, Throwable, A])] =
    for {
      runtime <- UIO.runtime[Any]
      q       <- Queue.bounded[A](capacity)
      qs      = new QueueSubscriber[A](runtime, q)
    } yield (qs, qs)
}

private class QueueSubscriber[A](runtime: Runtime[_], q: Queue[A])
    extends Subscriber[A]
    with ZStream[Any, Throwable, A] {

  private val capacity: Long = q.capacity.toLong

  private var completed: Boolean                 = false
  private var failed: Option[Throwable]          = None
  private var subscription: Option[Subscription] = None

  override def fold[R1 <: Any, E1 >: Throwable, A1 >: A, S]: Fold[R1, E1, A1, S] =
    IO.succeedLazy { (s, cont, f) =>
      def loop(s: S, demand: Long): ZIO[R1, E1, S] =
        if (!cont(s)) subscription.fold(UIO.unit)(s => UIO(s.cancel())) *> q.shutdown.const(s)
        else
          q.size.flatMap { n =>
            val empty = n <= 0
            if (empty && completed) q.shutdown.const(s)
            else if (empty && failed.isDefined) q.shutdown *> Task.fail(failed.get)
            else if (empty && (demand < q.capacity))
              subscription.fold(UIO.unit)(s => UIO(s.request(capacity - demand))) *> loop(s, capacity)
            else q.take.flatMap(f(s, _)).flatMap(loop(_, demand - 1))
          } <> failed.fold[Task[S]](UIO.succeed(s))(Task.fail)
      loop(s, capacity)
    }

  override def onSubscribe(s: Subscription): Unit = {
    if (s == null) throw new NullPointerException("s was null in onSubscribe")
    synchronized {
      subscription.fold {
        subscription = Some(s)
        s.request(capacity)
      }(_ => s.cancel())
    }
  }

  override def onNext(t: A): Unit = {
    if (t == null) throw new NullPointerException("t was null in onNext")
    runtime.unsafeRunSync(q.offer(t))
    ()
  }

  override def onError(e: Throwable): Unit = {
    if (e == null) throw new NullPointerException("t was null in onError")
    if (failed.isEmpty) {
      failed = Some(e)
      shutdownQueueIfEmpty()
    }
  }

  override def onComplete(): Unit =
    if (!completed) {
      completed = true
      shutdownQueueIfEmpty()
    }

  private def shutdownQueueIfEmpty(): Unit = {
    runtime.unsafeRunSync(q.size.flatMap[Any, Nothing, Unit](n => if (n <= 0) q.shutdown else UIO.unit))
    ()
  }
}
