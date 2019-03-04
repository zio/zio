package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio.stream.{ Sink, Stream, Take }
import scalaz.zio.{ Queue, Runtime, Task, ZIO }

class StreamPublisher[R, E <: Throwable, A](
  stream: Stream[R, E, A],
  runtime: Runtime[R]
) extends Publisher[A] {

  override def subscribe(subscriber: Subscriber[_ >: A]): Unit =
    if (subscriber == null) {
      throw new NullPointerException("Subscriber must not be null.")
    } else {
      runtime.unsafeRunAsync_(
        for {
          demand  <- Queue.unbounded[Long]
          _       <- Task(subscriber.onSubscribe(new QSubscription(subscriber, demand)))
          control = Stream.fromQueue(demand).flatMap(n => Stream.unfold(n)(n => if (n > 0) Some(((), n - 1)) else None))
          fiber   <- stream.toQueue().use(takesToCallbacks(subscriber, _, control, demand)).fork
          // reactive streams rule 3.13
          _ <- (demand.awaitShutdown *> fiber.interrupt).fork
        } yield ()
      )
    }

  private def takesToCallbacks(
    subscriber: Subscriber[_ >: A],
    sourceQ: Queue[Take[E, A]],
    control: Stream[Any, Nothing, Unit],
    demandQ: Queue[Long]
  ): Task[Unit] =
    Stream
      .fromQueue(sourceQ)
      // handle initially failed source before receiving demand
      .peel(Sink.readWhile[Take[E, A]](_.isFailure))
      .use {
        case (Take.Fail(e) :: _, _) => Task(subscriber.onError(e))
        case (_, takes) =>
          takes
            .zip(control)
            .foreach {
              case (Take.Value(a), _) => Task(subscriber.onNext(a))
              case (Take.Fail(e), _)  => Task(subscriber.onError(e)) *> demandQ.shutdown // rule 106
              case (Take.End, _)      => Task(subscriber.onComplete()) *> demandQ.shutdown // rule 106
            }
      }

  private class QSubscription(subscriber: Subscriber[_ >: A], demandQ: Queue[Long]) extends Subscription {
    override def request(n: Long): Unit = {
      if (n <= 0) subscriber.onError(new IllegalArgumentException("n must be > 0"))
      runtime.unsafeRunAsync_(demandQ.offer(n).void)
    }
    override def cancel(): Unit = runtime.unsafeRun(demandQ.shutdown)
  }
}

object StreamPublisher {
  def sinkToPublisher[R, E <: Throwable, A](stream: Stream[R, E, A]): ZIO[R, Nothing, StreamPublisher[R, E, A]] =
    ZIO.runtime.map(new StreamPublisher(stream, _))
}
