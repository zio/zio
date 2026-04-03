package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
class ConfigProviderBenchmark {

  @Param(Array("10", "1000"))
  var keyCount: Int = _

  val chunkSize = 10

  var mapForFlatConfig: Map[String, String] = _

  var mapForIndexedConfig: Map[String, String] = _

  @Setup
  def setup(): Unit = {
    val flatBuilder = Map.newBuilder[String, String]
    (0 until keyCount).foreach(i => flatBuilder += (s"k$i" -> s"v$i"))
    mapForFlatConfig = flatBuilder.result()

    val indexedBuilder = Map.newBuilder[String, String]
    for (i <- 0 until keyCount; j <- 0 until chunkSize) yield indexedBuilder += (s"k$i.items[$j]" -> s"v$i$j")
    mapForIndexedConfig = indexedBuilder.result()
  }

  @Benchmark
  def loadFlatConfig(): Unit = {
    val cp = ConfigProvider.fromMap(mapForFlatConfig)
    unsafeRun(ZIO.foreachDiscard(0 until keyCount)(i => cp.load(Config.string(s"k$i"))))
  }

  @Benchmark
  def loadIndexedConfig(): Unit = {
    val cp = ConfigProvider.fromMap(mapForIndexedConfig)
    unsafeRun(ZIO.foreachDiscard(0 until keyCount) { i =>
      cp.load(Config.chunkOf("items", Config.string).nested(s"k$i"))
        .filterOrFail[Throwable](_.length == chunkSize)(new Exception("Invalid chunk size"))
    })
  }

}
