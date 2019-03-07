package scalaz.zio.interop.reactiveStreams
import org.reactivestreams.{ Subscriber, Subscription }
import scalaz.zio.stream.Sink
import scalaz.zio.stream.Sink.Step
import scalaz.zio.{ Chunk, Queue, Runtime, UIO }

private[reactiveStreams] object SubscriberHelpers {

  def demandUnfoldSink[A](subscriber: Subscriber[_ >: A], demand: Queue[Long]): Sink[Any, Nothing, Unit, A, Unit] =
    new Sink[Any, Nothing, Unit, A, Unit] {
      override type State = Long

      override def initial: UIO[Step[Long, Nothing]] = UIO(Step.more(0L))

      override def step(state: Long, a: A): UIO[Step[Long, Nothing]] = {
        val done = demand.awaitShutdown.map(_ => Step.done(state, Chunk.empty))
        val more =
          if (state > 0) {
            UIO(subscriber.onNext(a)).map(_ => Step.more(state - 1))
          } else {
            for {
              n <- demand.take
              _ <- UIO(subscriber.onNext(a))
            } yield Step.more(n - 1)
          }
        done.race(more)
      }

      override def extract(state: Long): UIO[Unit] =
        for {
          f <- demand.awaitShutdown.fork
          o <- f.poll
          _ <- o.fold(UIO(subscriber.onComplete()))(_ => UIO.unit)
          _ <- demand.shutdown
        } yield ()

    }

  def createSubscription[A](subscriber: Subscriber[_ >: A], demandQ: Queue[Long], runtime: Runtime[_]): Subscription =
    new Subscription {
      override def request(n: Long): Unit = {
        if (n <= 0) subscriber.onError(new IllegalArgumentException("n must be > 0"))
        runtime.unsafeRunAsync_(demandQ.offer(n).void)
      }
      override def cancel(): Unit = runtime.unsafeRun(demandQ.shutdown)
    }
}
