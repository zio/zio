package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
class ZEnvironmentBenchmark {
  import BenchmarkUtil._
  import BenchmarkedEnvironment._

  implicit val u: Unsafe = Unsafe.unsafe

  var env: ZEnvironment[Env]           = _
  var smallEnv: ZEnvironment[SmallEnv] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    env = BenchmarkedEnvironment.makeLarge()
    smallEnv = BenchmarkedEnvironment.makeSmall()
  }

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
  @OperationsPerInvocation(100000)
  def addGetRepeat(bh: Blackhole) = {
    var i = 0
    var e = env
    while (i < 100000) {
      e = e
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
      bh.consume(e.get[Foo040])
      i += 1
    }
  }

  @Benchmark
  def addGetMulti(bh: Blackhole) = {
    val e = env.add(new Bar001)
    bh.consume(e.get[Bar001])
    bh.consume(e.get[Foo000])
    bh.consume(e.get[Foo001])
    bh.consume(e.get[Foo002])
    bh.consume(e.get[Foo003])
    bh.consume(e.get[Foo004])
    bh.consume(e.get[Foo045])
    bh.consume(e.get[Foo046])
    bh.consume(e.get[Foo047])
    bh.consume(e.get[Foo048])
    bh.consume(e.get[Foo049])
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def accessAfterScoped() = {
    val _ = env.get[Foo025]
    unsafe.run(
      ZIO
        .foreachDiscard(1 to 10000)(_ => ZIO.scoped(ZIO.environmentWith[Foo025](_.get[Foo025])))
        .provideEnvironment(env)
    )
  }

  @Benchmark
  @OperationsPerInvocation(10000)
  def accessAfterScopedUncached() =
    unsafe.run(
      ZIO
        .foreachDiscard(1 to 10000)(_ => ZIO.scoped(ZIO.environmentWith[Foo025](_.get[Foo025])))
        .provideEnvironment(env)
    )

  @Benchmark
  @OperationsPerInvocation(10000)
  def accessScope() =
    unsafe.run(
      ZIO
        .foreachDiscard(1 to 10000)(_ => ZIO.scoped(ZIO.environmentWith[Scope](_.get[Scope])))
        .provideEnvironment(env)
    )

  @Benchmark
  def union() =
    env.unionAll(smallEnv)

  @Benchmark
  def prune() =
    env.prune[Foo001 & Foo002 & Foo003]

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

  def makeSmall(): ZEnvironment[SmallEnv] =
    ZEnvironment.empty
      .add(new Bar000)
      .add(new Bar001)
      .add(new Bar002)
      .add(new Bar003)
      .add(new Bar004)

  def makeLarge(): ZEnvironment[Env] =
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

  type SmallEnv = Bar000 & Bar001 & Bar002 & Bar003 & Bar004

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

/*
[info] Benchmark                                 Mode  Cnt         Score        Error  Units
[info] ZEnvironmentBenchmark.access             thrpt    3  15317713.880 ± 691407.634  ops/s
[info] ZEnvironmentBenchmark.accessAfterScoped  thrpt    3    547842.919 ± 102911.143  ops/s
[info] ZEnvironmentBenchmark.accessScope        thrpt    3    485502.858 ±  80725.715  ops/s
[info] ZEnvironmentBenchmark.add                thrpt    3   3404552.760 ± 166969.278  ops/s
[info] ZEnvironmentBenchmark.addGetMulti        thrpt    3     58094.725 ±   7625.087  ops/s
[info] ZEnvironmentBenchmark.addGetOne          thrpt    3    707347.468 ±  47408.525  ops/s
[info] ZEnvironmentBenchmark.addGetRepeat       thrpt    3    569658.211 ±  22241.694  ops/s
[info] ZEnvironmentBenchmark.prune              thrpt    3     84737.132 ±   6659.910  ops/s
[info] ZEnvironmentBenchmark.union              thrpt    3   1117896.335 ± 162350.919  ops/s
 */

/*
[info] Benchmark                                 Mode  Cnt         Score          Error  Units
[info] ZEnvironmentBenchmark.access             thrpt    3  11980520.857 ± 11410618.481  ops/s
[info] ZEnvironmentBenchmark.accessAfterScoped  thrpt    3    501940.803 ±   741989.749  ops/s
[info] ZEnvironmentBenchmark.accessScope        thrpt    3   2095640.835 ±    47546.164  ops/s
[info] ZEnvironmentBenchmark.add                thrpt    3   1896121.353 ±    90542.715  ops/s
[info] ZEnvironmentBenchmark.addGetMulti        thrpt    3    137253.329 ±     6402.602  ops/s
[info] ZEnvironmentBenchmark.addGetOne          thrpt    3   8441052.400 ±   585066.613  ops/s
[info] ZEnvironmentBenchmark.addGetRepeat       thrpt    3    237182.895 ±    15012.450  ops/s
[info] ZEnvironmentBenchmark.prune              thrpt    3    217658.535 ±    22414.355  ops/s
[info] ZEnvironmentBenchmark.union              thrpt    3   2206037.664 ±    59785.571  ops/s

 */
