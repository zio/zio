package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole
import zio.BenchmarkedEnvironment.Foo009

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
class ZEnvironmentBenchmark {
  import BenchmarkedEnvironment._
  import BenchmarkUtil._

  implicit val u: Unsafe = Unsafe.unsafe

  var env: ZEnvironment[Env] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    env = BenchmarkedEnvironment.make()

  @Benchmark
  def access() = {
    env.get[Foo000]
    env.get[Foo001]
    env.get[Foo002]
    env.get[Foo003]
    env.get[Foo004]
    env.get[Foo045]
    env.get[Foo046]
    env.get[Foo047]
    env.get[Foo048]
    env.get[Foo049]
  }

  @Benchmark
  def add() =
    env.add(new Bar000).add(new Bar001).add(new Bar002).add(new Bar003).add(new Bar004)

  @Benchmark
  def addGetOne() =
    env.add(new Bar000).get[Bar000]

  @Benchmark
  def addGetMulti(bh: Blackhole) = bh.consume {
    val e = env.add(new Bar001)
    e.get[Bar001]
    e.get[Foo000]
    e.get[Foo001]
    e.get[Foo002]
    e.get[Foo003]
    e.get[Foo004]
    e.get[Foo045]
    e.get[Foo046]
    e.get[Foo047]
    e.get[Foo048]
    e.get[Foo049]
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def getService() =
    unsafe
      .run(
        ZIO.foreachDiscard(1 to 10000)(_ =>
          ZIO
            .scoped(ZIO.environment[Foo025].map { e =>
              e.get[Foo025]
            })
            .provideEnvironment(env)
        )
      )
      .getOrThrowFiberFailure()

  @Benchmark
  @OperationsPerInvocation(10000)
  def getScope() =
    unsafe
      .run(
        ZIO.foreachDiscard(1 to 10000)(_ =>
          ZIO.scoped(ZIO.environment[Scope].map(_.get[Scope])).provideEnvironment(env)
        )
      )
      .getOrThrowFiberFailure()

  @Benchmark
  def union() =
    env.union[Env](env)

}

object BenchmarkedEnvironment {

  final class Bar000
  final class Bar001
  final class Bar002
  final class Bar003
  final class Bar004

  final class Foo000
  final class Foo001
  final class Foo002
  final class Foo003
  final class Foo004
  final class Foo005
  final class Foo006
  final class Foo007
  final class Foo008
  final class Foo009

  final class Foo010
  final class Foo011
  final class Foo012
  final class Foo013
  final class Foo014
  final class Foo015
  final class Foo016
  final class Foo017
  final class Foo018
  final class Foo019

  final class Foo020
  final class Foo021
  final class Foo022
  final class Foo023
  final class Foo024
  final class Foo025
  final class Foo026
  final class Foo027
  final class Foo028
  final class Foo029

  final class Foo030
  final class Foo031
  final class Foo032
  final class Foo033
  final class Foo034
  final class Foo035
  final class Foo036
  final class Foo037
  final class Foo038
  final class Foo039

  final class Foo040
  final class Foo041
  final class Foo042
  final class Foo043
  final class Foo044
  final class Foo045
  final class Foo046
  final class Foo047
  final class Foo048
  final class Foo049

  def make(): ZEnvironment[Env] =
    ZEnvironment.empty
      .add(new Foo000)
      .add(new Foo001)
      .add(new Foo002)
      .add(new Foo003)
      .add(new Foo004)
      .add(new Foo005)
      .add(new Foo006)
      .add(new Foo007)
      .add(new Foo008)
      .add(new Foo009)
      .add(new Foo010)
      .add(new Foo011)
      .add(new Foo012)
      .add(new Foo013)
      .add(new Foo014)
      .add(new Foo015)
      .add(new Foo016)
      .add(new Foo017)
      .add(new Foo018)
      .add(new Foo019)
      .add(new Foo020)
      .add(new Foo021)
      .add(new Foo022)
      .add(new Foo023)
      .add(new Foo024)
      .add(new Foo025)
      .add(new Foo026)
      .add(new Foo027)
      .add(new Foo028)
      .add(new Foo029)
      .add(new Foo030)
      .add(new Foo031)
      .add(new Foo032)
      .add(new Foo033)
      .add(new Foo034)
      .add(new Foo035)
      .add(new Foo036)
      .add(new Foo037)
      .add(new Foo038)
      .add(new Foo039)
      .add(new Foo040)
      .add(new Foo041)
      .add(new Foo042)
      .add(new Foo043)
      .add(new Foo044)
      .add(new Foo045)
      .add(new Foo046)
      .add(new Foo047)
      .add(new Foo048)
      .add(new Foo049)

  type Env = Foo000
    with Foo001
    with Foo002
    with Foo003
    with Foo004
    with Foo005
    with Foo006
    with Foo007
    with Foo008
    with Foo009
    with Foo010
    with Foo011
    with Foo012
    with Foo013
    with Foo014
    with Foo015
    with Foo016
    with Foo017
    with Foo018
    with Foo019
    with Foo020
    with Foo021
    with Foo022
    with Foo023
    with Foo024
    with Foo025
    with Foo026
    with Foo027
    with Foo028
    with Foo029
    with Foo030
    with Foo031
    with Foo032
    with Foo033
    with Foo034
    with Foo035
    with Foo036
    with Foo037
    with Foo038
    with Foo039
    with Foo040
    with Foo041
    with Foo042
    with Foo043
    with Foo044
    with Foo045
    with Foo046
    with Foo047
    with Foo048
    with Foo049

}
