package scalaz.zio.interop.reactiveStreams
import org.reactivestreams.{ Publisher, Subscriber }
import scalaz.zio._
import scalaz.zio.interop.reactiveStreams.SubscriberHelpers._
import scalaz.zio.stream.{ Sink, Stream }

object Adapters {

  /**
   * Create a [[Subscriber]] from a [[Sink]]. Returns a subscriber producing to the Sink and a [[Task]] of the value
   * produced by the Sink or any error either produced by the Sink or signaled to the subscriber.
   */
  def sinkToSubscriber[R, E <: Throwable, A1, A, B](
    sink: Sink[R, E, A1, A, B],
    bufferSize: Int = 10
  ): ZIO[R, Nothing, (Subscriber[A], Task[B])] =
    for {
      runtime    <- ZIO.runtime[Any]
      q          <- Queue.bounded[A](bufferSize)
      p          <- Promise.make[Throwable, B]
      subscriber = new QueueSubscriber[A, B](runtime, q, p)
      fiber <- Stream
                .fromQueue(q)
                .tap(_ => subscriber.signalDemand)
                .run(sink)
                .fork
      _ <- p.done(fiber.join).fork
    } yield (subscriber, p.await)

  /**
   * Create a [[Publisher]] from a [[Stream]].
   */
  def streamToPublisher[R, E <: Throwable, A](stream: Stream[R, E, A]): ZIO[R, Nothing, Publisher[A]] =
    ZIO.runtime.map { runtime => (subscriber: Subscriber[_ >: A]) =>
      if (subscriber == null) {
        throw new NullPointerException("Subscriber must not be null.")
      } else {
        runtime.unsafeRunAsync_(
          for {
            demand <- Queue.unbounded[Long]
            _      <- UIO(subscriber.onSubscribe(createSubscription(subscriber, demand, runtime)))
            fiber <- stream
                      .run(demandUnfoldSink(subscriber, demand))
                      .catchAll(e => UIO(subscriber.onError(e)))
                      .flatMap(_ => demand.shutdown)
                      .fork
            // reactive streams rule 3.13
            _ <- (demand.awaitShutdown *> fiber.interrupt).fork
          } yield ()
        )
      }
    }

  /**
   * Create a [[Sink]] from a [[Subscriber]]. The Sink will never fail.
   *
   * A [[Sink]] cannot signal [[Stream]] failure to the subscriber, as this requires a side channel from the [[Stream]].
   * For this reason, using this adapter with a [[Stream]] that may fail will most likely leak resources and lead to
   * unexpected behavior. If you need to cover such a case, convert the [[Stream]] to a [[Publisher]] instead and
   * connect the [[Subscriber]] to the resulting [[Publisher]].
   */
  def subscriberToSink[A](subscriber: Subscriber[A]): UIO[Sink[Any, Nothing, Unit, A, Unit]] =
    for {
      runtime      <- ZIO.runtime[Any]
      demandQ      <- Queue.unbounded[Long]
      subscription = createSubscription(subscriber, demandQ, runtime)
      _            <- UIO(subscriber.onSubscribe(subscription))
    } yield demandUnfoldSink(subscriber, demandQ)

  /**
   * Create a [[Stream]] from a [[Publisher]].
   */
  def publisherToStream[A](publisher: Publisher[A], bufferSize: Int): UIO[Stream[Any, Throwable, A]] =
    for {
      runtime    <- ZIO.runtime[Any]
      q          <- Queue.bounded[A](bufferSize)
      p          <- Promise.make[Throwable, Unit]
      subscriber = new QueueSubscriber[A, Unit](runtime, q, p)
      _          <- UIO(publisher.subscribe(subscriber))
    } yield
      Stream
        .fromQueue(q)
        .tap(_ => subscriber.signalDemand)
        .++(Stream.lift(p.succeed(())).drain)
        .merge(Stream.lift(p.await).drain)


}
