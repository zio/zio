package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import java.nio.channels.{ServerSocketChannel, SocketChannel, Selector}
import java.net.InetSocketAddress

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 10, timeUnit = TimeUnit.SECONDS, time = 2)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 2)
@Fork(value = 3)
class NioSchedulerBenchmark {

  val defaultScheduler: Executor = zio.Executor.makeDefault()
  val nioScheduler: Executor = new NioScheduler(autoBlocking = true)

  @Benchmark
  def defaultSpawnManyLocal: Int =
    zioSpawnManyLocal(defaultScheduler, 10000)

  @Benchmark
  def nioSpawnManyLocal: Int =
    zioSpawnManyLocal(nioScheduler, 10000)

  @Benchmark
  def defaultSpawnManyRemote: Int =
    zioSpawnManyRemote(defaultScheduler, 10000)

  @Benchmark
  def nioSpawnManyRemote: Int =
    zioSpawnManyRemote(nioScheduler, 10000)

  @Benchmark
  def defaultPingPong: Int =
    zioPingPong(defaultScheduler, 1000)

  @Benchmark
  def nioPingPong: Int =
    zioPingPong(nioScheduler, 1000)

  @Benchmark
  def defaultYieldMany: Int =
    zioYieldMany(defaultScheduler, 10000)

  @Benchmark
  def nioYieldMany: Int =
    zioYieldMany(nioScheduler, 10000)

  @Benchmark
  def nioChannelRegistration: Int = {
    var count = 0
    val scheduler = new NioScheduler(autoBlocking = true)
    val channel = ServerSocketChannel.open()
    channel.configureBlocking(false)
    channel.socket().bind(new InetSocketAddress(0))

    (1 to 100).foreach { _ =>
      val fiber = new IOFiber { _ => }
      scheduler.registerChannel(channel, java.nio.channels.SelectionKey.OP_ACCEPT, fiber)
      count += 1
    }

    count
  }

  // === Helper Methods ===

  private def zioSpawnManyLocal(executor: Executor, count: Int): Int = {
    val runtime = zio.Runtime.default
    var completed = 0

    val effect = ZIO
      .foreachDiscard(1 to count)(_ => ZIO.succeed(()).onExecutor(executor))
      .tap(_ => ZIO.succeed(completed += 1))

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(effect).getOrThrow()
    }

    completed
  }

  private def zioSpawnManyRemote(executor: Executor, count: Int): Int = {
    val runtime = zio.Runtime.default
    var completed = 0

    val effect = ZIO
      .foreachDiscard(1 to count) { i =>
        ZIO.succeed(()).onExecutor(executor) *>
          ZIO.succeed(completed += 1)
      }

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(effect).getOrThrow()
    }

    completed
  }

  private def zioPingPong(executor: Executor, count: Int): Int = {
    val runtime = zio.Runtime.default
    var completed = 0

    val effect = ZIO
      .succeed(0)
      .flatMap { _ =>
        (1 to count).foldLeft(ZIO.succeed(())) { (acc, _) =>
          acc *> ZIO.succeed(()).onExecutor(executor)
        }
      }
      .tap(_ => ZIO.succeed(completed += 1))

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(effect).getOrThrow()
    }

    completed
  }

  private def zioYieldMany(executor: Executor, count: Int): Int = {
    val runtime = zio.Runtime.default
    var completed = 0

    val effect = ZIO
      .foreachDiscard(1 to count)(_ => ZIO.yieldNow.onExecutor(executor))
      .tap(_ => ZIO.succeed(completed += 1))

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(effect).getOrThrow()
    }

    completed
  }
}
