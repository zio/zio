package zio.stream

import zio._
import zio.test.Assertion._
import zio.test._

object SubscriptionRefSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] =
    suite("SubscriptionRefSpec")(
      testM("multiple subscribers can receive changes") {
        for {
          subscriptionRef <- SubscriptionRef.make(0)
          promise1        <- Promise.make[Nothing, Unit]
          promise2        <- Promise.make[Nothing, Unit]
          subscriber1     <- subscriptionRef.changes.tap(_ => promise1.succeed(())).take(3).runCollect.fork
          _               <- promise1.await
          _               <- subscriptionRef.ref.update(n => ZIO.succeed(n + 1))
          subscriber2     <- subscriptionRef.changes.tap(_ => promise2.succeed(())).take(2).runCollect.fork
          _               <- promise2.await
          _               <- subscriptionRef.ref.update(n => ZIO.succeed(n + 1))
          values1         <- subscriber1.join
          values2         <- subscriber2.join
        } yield assert(values1)(equalTo(Chunk(0, 1, 2))) &&
          assert(values2)(equalTo(Chunk(1, 2)))
      },
      testM("subscriptions are interruptible") {
        for {
          subscriptionRef <- SubscriptionRef.make(0)
          promise1        <- Promise.make[Nothing, Unit]
          promise2        <- Promise.make[Nothing, Unit]
          subscriber1     <- subscriptionRef.changes.tap(_ => promise1.succeed(())).take(5).runCollect.fork
          _               <- promise1.await
          _               <- subscriptionRef.ref.update(n => ZIO.succeed(n + 1))
          subscriber2     <- subscriptionRef.changes.tap(_ => promise2.succeed(())).take(2).runCollect.fork
          _               <- promise2.await
          _               <- subscriptionRef.ref.update(n => ZIO.succeed(n + 1))
          values1         <- subscriber1.interrupt
          values2         <- subscriber2.join
        } yield assert(values1)(isInterrupted) &&
          assert(values2)(equalTo(Chunk(1, 2)))
      },
      testM("concurrent subscribes and unsubscribes are handled correctly") {
        def subscriber(subscriptionRef: SubscriptionRef[Long]) =
          for {
            n  <- random.nextLongBetween(1, 200)
            as <- subscriptionRef.changes.take(n).runCollect
          } yield as
        for {
          subscriptionRef <- SubscriptionRef.make(0L)
          fiber           <- subscriptionRef.ref.updateAndGet(n => UIO(n + 1)).forever.fork
          values          <- ZIO.collectAllPar(List.fill(5000)(subscriber(subscriptionRef)))
          _               <- fiber.interrupt
        } yield assert(values)(forall(isSorted))
      }
    )
}
