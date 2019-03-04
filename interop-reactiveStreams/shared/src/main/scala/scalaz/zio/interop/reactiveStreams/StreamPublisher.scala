package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio.stream.{ Sink, Stream }
import scalaz.zio.{ Queue, Runtime, UIO, ZIO }

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
          _       <- UIO(subscriber.onSubscribe(createSubscription(subscriber, demand)))
          control = Stream.fromQueue(demand).flatMap(n => Stream.unfold(n)(n => if (n > 0) Some(((), n - 1)) else None))
          fiber <- stream
                    .zipWith(control) {
                      case (Some(a), Some(_)) =>
                        subscriber.onNext(a)
                        Some(())
                      case (None, Some(_)) =>
                        subscriber.onComplete()
                        None
                      case _ =>
                        None
                    }
                    .run(Sink.drain)
                    .catchAll(e => UIO(subscriber.onError(e)))
                    .flatMap(_ => demand.shutdown)
                    .fork
          // reactive streams rule 3.13
          _ <- (demand.awaitShutdown *> fiber.interrupt).fork
        } yield ()
      )
    }

  private def createSubscription(subscriber: Subscriber[_ >: A], demandQ: Queue[Long]): Subscription =
    new Subscription {
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
