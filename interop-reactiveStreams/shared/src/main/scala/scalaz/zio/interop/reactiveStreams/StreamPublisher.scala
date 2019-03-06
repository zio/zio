package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import scalaz.zio.stream.Sink.Step
import scalaz.zio.stream.{ Sink, StreamR }
import scalaz.zio.{ Queue, Runtime, UIO, ZIO }

class StreamPublisher[R, E <: Throwable, A](
  stream: StreamR[R, E, A],
  runtime: Runtime[R]
) extends Publisher[A] {

  override def subscribe(subscriber: Subscriber[_ >: A]): Unit =
    if (subscriber == null) {
      throw new NullPointerException("Subscriber must not be null.")
    } else {
      runtime.unsafeRunAsync_(
        for {
          demand <- Queue.unbounded[Long]
          _      <- UIO(subscriber.onSubscribe(createSubscription(subscriber, demand)))
          fiber <- stream
                    .run(demandUnfoldSink(subscriber, demand))
                    .flatMap(_ => UIO(subscriber.onComplete()))
                    .catchAll(e => UIO(subscriber.onError(e)))
                    .flatMap(_ => demand.shutdown)
                    .fork
          // reactive streams rule 3.13
          _ <- (demand.awaitShutdown *> fiber.interrupt).fork
        } yield ()
      )
    }

  private def demandUnfoldSink(subscriber: Subscriber[_ >: A], demand: Queue[Long]): Sink[Nothing, A, A, Unit] =
    new Sink[Nothing, A, A, Unit] {
      override type State = Long

      override def initial: UIO[Step[Long, Nothing]] = UIO(Step.more(0L))

      override def step(state: Long, a: A): UIO[Step[Long, A]] =
        if (state > 0) {
          UIO(subscriber.onNext(a)).map(_ => Step.more(state - 1))
        } else {
          for {
            n <- demand.take
            _ <- UIO(subscriber.onNext(a))
          } yield Step.more(n - 1)
        }

      override def extract(state: Long): UIO[Unit] = UIO.unit

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
  def sinkToPublisher[R, E <: Throwable, A](stream: StreamR[R, E, A]): ZIO[R, Nothing, StreamPublisher[R, E, A]] =
    ZIO.runtime.map(new StreamPublisher(stream, _))
}
