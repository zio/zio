package scalaz.zio.interop.reactiveStreams

import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio._
import scalaz.zio.interop.reactiveStreams.SubscriberHelpers._
import scalaz.zio.stream.{ Stream, Take, ZSink, ZStream }

//in scala 2.11 the proof for Any in not found by the compiler
import Stream.ConformsAnyProof

object Adapters {

  def sinkToSubscriber[R, E <: Throwable, A1, A, B](
    sink: ZSink[R, E, A1, A, B],
    bufferSize: Int = 10
  ): ZIO[R, Nothing, (Subscriber[A], Task[B])] =
    for {
      runtime    <- ZIO.runtime[Any]
      q          <- Queue.bounded[Take[Throwable, A]](bufferSize + 1)
      subscriber = new QueueSubscriber[A](runtime, q)
      fiber <- untakeQ(q)
                .tap(_ => subscriber.signalDemand)
                .run(sink)
                .fork
    } yield (subscriber, fiber.join)

  def streamToPublisher[R, E <: Throwable, A](stream: ZStream[R, E, A]): ZIO[R, Nothing, Publisher[A]] =
    ZIO.runtime.map { runtime => (subscriber: Subscriber[_ >: A]) =>
      if (subscriber == null) {
        throw new NullPointerException("Subscriber must not be null.")
      } else {
        runtime.unsafeRunAsync_(
          for {
            demand <- Queue.unbounded[Long]
            _      <- UIO(subscriber.onSubscribe(createSubscription(subscriber, demand, runtime)))
            _ <- stream
                  .run(demandUnfoldSink(subscriber, demand))
                  .catchAll(e => UIO(subscriber.onError(e)))
                  .fork
          } yield ()
        )
      }
    }

  def subscriberToSink[E <: Throwable, A](
    subscriber: Subscriber[A]
  ): UIO[(Promise[E, Nothing], ZSink[Any, Nothing, Unit, A, Unit])] =
    for {
      runtime      <- ZIO.runtime[Any]
      demand       <- Queue.unbounded[Long]
      error        <- Promise.make[E, Nothing]
      subscription = createSubscription(subscriber, demand, runtime)
      _            <- UIO(subscriber.onSubscribe(subscription))
      _            <- error.await.catchAll(t => UIO(subscriber.onError(t)) *> demand.shutdown).fork
    } yield (error, demandUnfoldSink(subscriber, demand))

  def publisherToStream[A](publisher: Publisher[A], bufferSize: Int): ZStream[Any, Throwable, A] =
    Stream.unwrap(for {
      runtime    <- ZIO.runtime[Any]
      q          <- Queue.bounded[Take[Throwable, A]](bufferSize + 1)
      subscriber = new QueueSubscriber[A](runtime, q)
      _          <- UIO(publisher.subscribe(subscriber))
    } yield untakeQ(q).tap(_ => subscriber.signalDemand))

  private def untakeQ[R, E, A](q: Queue[Take[E, A]]): ZStream[R, E, A] =
    Stream.fromQueue(q).unTake ++ Stream.fromEffect(q.shutdown).drain

}
