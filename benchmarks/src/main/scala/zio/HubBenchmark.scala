package zio

import cats.effect.std.{Queue => CatsQueue}
import cats.effect.unsafe.implicits.global
import cats.effect.{Fiber => CFiber, IO => CIO, Resource}
import cats.syntax.all._
import fs2.concurrent.Topic
import io.github.timwspence.cats.stm.{STM => CSTM}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio.stm._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class HubBenchmarks {

  val hubSize               = 2
  val totalSize             = 1024
  val publisherParallelism  = 1
  val subscriberParallelism = 1024

  val cstm: CSTM[CIO] = CSTM.runtime[CIO].unsafeRunSync()
  import cstm.{TQueue => CatsTQueue, TVar}

  @Benchmark
  def catsQueueBoundedBackPressure(): Int =
    catsParallel(CatsHubLike.catsQueueBounded(hubSize))

  @Benchmark
  def catsQueueBoundedParallel(): Int =
    catsParallel(CatsHubLike.catsQueueBounded(totalSize))

  @Benchmark
  def catsQueueBoundedSequential(): Int =
    catsSequential(CatsHubLike.catsQueueBounded(totalSize))

  @Benchmark
  def catsQueueUnboundedParallel(): Int =
    catsParallel(CatsHubLike.catsQueueUnbounded)

  @Benchmark
  def catsQueueUnboundedSequential(): Int =
    catsSequential(CatsHubLike.catsQueueUnbounded)

  @Benchmark
  def catsTQueueParallel(): Int =
    catsParallel(CatsHubLike.catsTQueueUnbounded)

  @Benchmark
  def catsTQueueSequential(): Int =
    catsSequential(CatsHubLike.catsTQueueUnbounded)

  @Benchmark
  def fs2TopicBackPressure(): Int =
    catsParallel(CatsHubLike.fs2TopicBounded(hubSize))

  @Benchmark
  def fs2TopicParallel(): Int =
    catsParallel(CatsHubLike.fs2TopicBounded(totalSize))

  @Benchmark
  def fs2TopicSequential(): Int =
    catsSequential(CatsHubLike.fs2TopicBounded(totalSize))

  @Benchmark
  def zioHubBoundedBackPressure(): Int =
    zioParallel(ZIOHubLike.zioHubBounded(hubSize))

  @Benchmark
  def zioHubBoundedParallel(): Int =
    zioParallel(ZIOHubLike.zioHubBounded(totalSize))

  @Benchmark
  def zioHubBoundedSequential(): Int =
    zioSequential(ZIOHubLike.zioHubBounded(totalSize))

  @Benchmark
  def zioHubUnboundedParallel(): Int =
    zioParallel(ZIOHubLike.zioHubUnbounded)

  @Benchmark
  def zioHubUnboundedSequential(): Int =
    zioSequential(ZIOHubLike.zioHubUnbounded)

  @Benchmark
  def zioQueueBoundedBackPressure(): Int =
    zioParallel(ZIOHubLike.zioQueueBounded(hubSize))

  @Benchmark
  def zioQueueBoundedParallel(): Int =
    zioParallel(ZIOHubLike.zioQueueBounded(totalSize))

  @Benchmark
  def zioQueueBoundedSequential(): Int =
    zioSequential(ZIOHubLike.zioQueueBounded(totalSize))

  @Benchmark
  def zioQueueUnboundedParallel(): Int =
    zioParallel(ZIOHubLike.zioQueueUnbounded)

  @Benchmark
  def zioQueueUnboundedSequential(): Int =
    zioSequential(ZIOHubLike.zioQueueUnbounded)

  @Benchmark
  def zioTHubBoundedBackPressure(): Int =
    zioParallel(ZIOHubLike.zioTHubBounded(hubSize))

  @Benchmark
  def zioTHubBoundedParallel(): Int =
    zioParallel(ZIOHubLike.zioTHubBounded(totalSize))

  @Benchmark
  def zioTHubBoundedSequential(): Int =
    zioSequential(ZIOHubLike.zioTHubBounded(totalSize))

  @Benchmark
  def zioTHubUnboundedParallel(): Int =
    zioParallel(ZIOHubLike.zioTHubUnbounded)

  @Benchmark
  def zioTHubUnboundedSequential(): Int =
    zioSequential(ZIOHubLike.zioTHubUnbounded)

  @Benchmark
  def zioTQueueBoundedBackPressure(): Int =
    zioParallel(ZIOHubLike.zioTQueueBounded(hubSize))

  @Benchmark
  def zioTQueueBoundedParallel(): Int =
    zioParallel(ZIOHubLike.zioTQueueBounded(totalSize))

  @Benchmark
  def zioTQueueBoundedSequential(): Int =
    zioSequential(ZIOHubLike.zioTQueueBounded(totalSize))

  @Benchmark
  def zioTQueueUnboundedParallel(): Int =
    zioParallel(ZIOHubLike.zioTQueueUnbounded)

  @Benchmark
  def zioTQueueUnboundedSequential(): Int =
    zioSequential(ZIOHubLike.zioTQueueUnbounded)

  trait ZIOHubLike[A] {
    def publish(a: A): UIO[Any]
    def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]]
  }

  object ZIOHubLike {

    def zioHubBounded[A](capacity: Int): UIO[ZIOHubLike[A]] =
      Hub.bounded[A](capacity).map { hub =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            hub.publish(a)
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            hub.subscribe.map(dequeue => n => zioRepeat(n)(dequeue.take))
        }
      }

    def zioHubUnbounded[A]: UIO[ZIOHubLike[A]] =
      Hub.unbounded[A].map { hub =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            hub.publish(a)
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            hub.subscribe.map(dequeue => n => zioRepeat(n)(dequeue.take))
        }
      }

    def zioQueueBounded[A](capacity: Int): UIO[ZIOHubLike[A]] =
      Ref.make(0L).zipWith(Ref.make[Map[Long, Queue[A]]](Map.empty)) { (key, ref) =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            ref.get.flatMap(map => ZIO.foreach(map.values)(_.offer(a)))
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            for {
              key   <- key.getAndUpdate(_ + 1)
              queue <- Queue.bounded[A](capacity)
              _     <- ZIO.acquireRelease(ref.update(_ + (key -> queue)))(_ => ref.update(_ - key))
            } yield n => zioRepeat(n)(queue.take)
        }
      }

    def zioQueueUnbounded[A]: UIO[ZIOHubLike[A]] =
      Ref.make(0L).zipWith(Ref.make[Map[Long, Queue[A]]](Map.empty)) { (key, ref) =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            ref.get.flatMap(map => ZIO.foreach(map.values)(_.offer(a)))
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            for {
              key   <- key.getAndUpdate(_ + 1)
              queue <- Queue.unbounded[A]
              _     <- ZIO.acquireRelease(ref.update(_ + (key -> queue)))(_ => ref.update(_ - key))
            } yield n => zioRepeat(n)(queue.take)
        }
      }

    def zioTHubBounded[A](capacity: Int): UIO[ZIOHubLike[A]] =
      THub.bounded[A](capacity).commit.map { hub =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            hub.publish(a).commit
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            hub.subscribeScoped.map(dequeue => n => zioRepeat(n)(dequeue.take.commit))
        }
      }

    def zioTHubUnbounded[A]: UIO[ZIOHubLike[A]] =
      THub.unbounded[A].commit.map { hub =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            hub.publish(a).commit
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            hub.subscribeScoped.map(dequeue => n => zioRepeat(n)(dequeue.take.commit))
        }
      }

    def zioTQueueBounded[A](capacity: Int): UIO[ZIOHubLike[A]] =
      TRef.make(0L).commit.zipWith(TRef.make[Map[Long, TQueue[A]]](Map.empty).commit) { (key, ref) =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            ref.get.flatMap(map => ZSTM.foreach(map.values)(_.offer(a))).commit
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            for {
              key   <- key.getAndUpdate(_ + 1).commit
              queue <- TQueue.bounded[A](capacity).commit
              _     <- ZIO.acquireRelease(ref.update(_ + (key -> queue)).commit)(_ => ref.update(_ - key).commit)
            } yield n => zioRepeat(n)(queue.take.commit)
        }
      }

    def zioTQueueUnbounded[A]: UIO[ZIOHubLike[A]] =
      TRef.make(0L).commit.zipWith(TRef.make[Map[Long, TQueue[A]]](Map.empty).commit) { (key, ref) =>
        new ZIOHubLike[A] {
          def publish(a: A): UIO[Any] =
            ref.get.flatMap(map => ZSTM.foreach(map.values)(_.offer(a))).commit
          def subscribe: ZIO[Scope, Nothing, Int => UIO[Any]] =
            for {
              key   <- key.getAndUpdate(_ + 1).commit
              queue <- TQueue.unbounded[A].commit
              _     <- ZIO.acquireRelease(ref.update(_ + (key -> queue)).commit)(_ => ref.update(_ - key).commit)
            } yield n => zioRepeat(n)(queue.take.commit)
        }
      }
  }

  trait CatsHubLike[A] {
    def publish(a: A): CIO[Any]
    def subscribe: Resource[CIO, Int => CIO[Any]]
  }

  object CatsHubLike {

    def catsQueueBounded[A](capacity: Int): CIO[CatsHubLike[A]] =
      CIO.ref(0L).map2(CIO.ref[Map[Long, CatsQueue[CIO, A]]](Map.empty)) { (key, ref) =>
        new CatsHubLike[A] {
          def publish(a: A): CIO[Unit] =
            ref.get.flatMap(_.values.toList.traverse_(_.offer(a)))
          def subscribe: Resource[CIO, Int => CIO[Any]] =
            for {
              key   <- Resource.eval(key.modify(n => (n + 1, n)))
              queue <- Resource.eval(CatsQueue.bounded[CIO, A](capacity))
              _     <- Resource.make(ref.update(_ + (key -> queue)))(_ => ref.update(_ - key))
            } yield n => catsRepeat(n)(queue.take)
        }
      }

    def catsQueueUnbounded[A]: CIO[CatsHubLike[A]] =
      CIO.ref(0L).map2(CIO.ref[Map[Long, CatsQueue[CIO, A]]](Map.empty)) { (key, ref) =>
        new CatsHubLike[A] {
          def publish(a: A): CIO[Unit] =
            ref.get.flatMap(_.values.toList.traverse_(_.offer(a)))
          def subscribe: Resource[CIO, Int => CIO[Any]] =
            for {
              key   <- Resource.eval(key.modify(n => (n + 1, n)))
              queue <- Resource.eval(CatsQueue.unbounded[CIO, A])
              _     <- Resource.make(ref.update(_ + (key -> queue)))(_ => ref.update(_ - key))
            } yield n => catsRepeat(n)(queue.take)
        }
      }

    def catsTQueueUnbounded[A]: CIO[CatsHubLike[A]] =
      cstm.commit {
        TVar.of(0L).map2(TVar.of[Map[Long, CatsTQueue[A]]](Map.empty)) { (key, ref) =>
          new CatsHubLike[A] {
            def publish(a: A): CIO[Unit] =
              cstm.commit(ref.get.flatMap(_.values.toList.traverse_(_.put(a))))
            def subscribe: Resource[CIO, Int => CIO[Any]] =
              for {
                key   <- Resource.eval(cstm.commit(key.get.flatMap(n => key.modify(_ + 1).as(n))))
                queue <- Resource.eval(cstm.commit(CatsTQueue.empty[A]))
                _     <- Resource.make(cstm.commit(ref.modify(_ + (key -> queue))))(_ => cstm.commit(ref.modify(_ - key)))
              } yield n => catsRepeat(n)(cstm.commit(queue.read))
          }
        }
      }

    def fs2TopicBounded[A](capacity: Int): CIO[CatsHubLike[A]] =
      Topic[CIO, A].map { topic =>
        new CatsHubLike[A] {
          def publish(a: A): CIO[Unit] =
            topic.publish1(a).void
          def subscribe: Resource[CIO, Int => CIO[Unit]] =
            topic.subscribeAwait(capacity).map(subscription => n => subscription.take(n.toLong).compile.drain)
        }
      }
  }

  def catsParallel(makeHub: CIO[CatsHubLike[Int]]): Int = {

    val io = for {
      ref      <- CIO.ref(subscriberParallelism)
      deferred <- CIO.deferred[Unit]
      hub      <- makeHub
      subscribers <- catsForkAll(List.fill(subscriberParallelism)(hub.subscribe.use { take =>
                       ref.modify { n =>
                         if (n == 1) (n - 1, deferred.complete(()))
                         else (n - 1, CIO.unit)
                       }.flatten *>
                         take(totalSize)
                     }))
      _ <- deferred.get
      _ <- catsForkAll(List.fill(publisherParallelism)(catsRepeat(totalSize / publisherParallelism)(hub.publish(0))))
      _ <- catsJoinAll(subscribers)
    } yield 0

    io.unsafeRunSync()
  }

  def catsSequential(makeHub: CIO[CatsHubLike[Int]]): Int = {
    import cats.effect._

    val io = for {
      ref       <- IO.ref(subscriberParallelism)
      deferred1 <- Deferred[IO, Unit]
      deferred2 <- Deferred[IO, Unit]
      hub       <- makeHub
      subscribers <- catsForkAll(List.fill(subscriberParallelism)(hub.subscribe.use { take =>
                       ref.modify { n =>
                         if (n == 1) (n - 1, deferred1.complete(()))
                         else (n - 1, IO.unit)
                       }.flatten *>
                         deferred2.get *>
                         take(totalSize)
                     }))
      _ <- deferred1.get
      _ <- catsRepeat(totalSize)(hub.publish(0))
      _ <- deferred2.complete(())
      _ <- catsJoinAll(subscribers)
    } yield 0

    io.unsafeRunSync()
  }

  def zioParallel(makeHub: UIO[ZIOHubLike[Int]]): Int = {

    val io = for {
      ref     <- Ref.make(subscriberParallelism)
      promise <- Promise.make[Nothing, Unit]
      hub     <- makeHub
      subscribers <- zioForkAll(List.fill(subscriberParallelism)(ZIO.scoped(hub.subscribe.flatMap { take =>
                       promise.succeed(()).whenZIO(ref.updateAndGet(_ - 1).map(_ == 0)) *>
                         take(totalSize)
                     })))
      _ <- promise.await
      _ <- zioForkAll(List.fill(publisherParallelism)(zioRepeat(totalSize / publisherParallelism)(hub.publish(0))))
      _ <- zioJoinAll(subscribers)
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io)
    }
  }

  def zioSequential(makeHub: UIO[ZIOHubLike[Int]]): Int = {

    val io = for {
      ref      <- Ref.make(subscriberParallelism)
      promise1 <- Promise.make[Nothing, Unit]
      promise2 <- Promise.make[Nothing, Unit]
      hub      <- makeHub
      subscribers <- zioForkAll(List.fill(subscriberParallelism)(ZIO.scoped(hub.subscribe.flatMap { take =>
                       promise1.succeed(()).whenZIO(ref.updateAndGet(_ - 1).map(_ == 0)) *>
                         promise2.await *>
                         take(totalSize)
                     })))
      _ <- promise1.await
      _ <- zioRepeat(totalSize)(hub.publish(0))
      _ <- promise2.succeed(())
      _ <- zioJoinAll(subscribers)
    } yield 0

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(io)
    }
  }

  def zioForkAll[R, E, A](as: List[ZIO[R, E, A]]): ZIO[R, Nothing, List[Fiber[E, A]]] =
    ZIO.foreach(as)(_.fork)

  def zioJoinAll[E, A](as: List[Fiber[E, A]]): ZIO[Any, E, List[A]] =
    ZIO.foreach(as)(_.join)

  def zioRepeat[R, E, A](n: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) zio
    else zio *> zioRepeat(n - 1)(zio)

  def catsForkAll[A](as: List[CIO[A]]): CIO[List[CFiber[CIO, Throwable, A]]] =
    as.traverse(_.start)

  def catsJoinAll[A](as: List[CFiber[CIO, Throwable, A]]): CIO[List[A]] =
    as.traverse(_.joinWithNever)

  def catsRepeat[A](n: Int)(io: CIO[A]): CIO[A] =
    if (n <= 1) io
    else io.flatMap(_ => catsRepeat(n - 1)(io))
}
