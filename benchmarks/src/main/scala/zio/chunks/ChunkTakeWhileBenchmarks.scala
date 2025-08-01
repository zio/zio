package zio.chunks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
class ChunkTakeWhileBenchmarks {
  final case class Example(value: Int, name: String)
  object Example {
    def value(v: Int): Example = Example(v, "example")
  }

  @Param(Array("1000"))
  var size: Int = _

  var chunkInt: Chunk[Int]          = _
  var vectorInt: Vector[Int]        = _
  var listInt: List[Int]            = _
  var chunkAnyRef: Chunk[Example]   = _
  var vectorAnyRef: Vector[Example] = _
  var listAnyRef: List[Example]     = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunkInt = Chunk.fromArray(array)
    vectorInt = array.toVector
    listInt = array.toList

    val anyRefArray = array.map(Example.value)
    chunkAnyRef = Chunk.fromArray(anyRefArray)
    vectorAnyRef = anyRefArray.toVector
    listAnyRef = anyRefArray.toList
  }

  @Benchmark
  def takeWhileChunkInt(): Chunk[Int] = chunkInt.takeWhile(_ < 1000)

  @Benchmark
  def takeWhileVectorInt(): Vector[Int] = vectorInt.takeWhile(_ < 1000)

  @Benchmark
  def takeWhileListInt(): List[Int] = listInt.takeWhile(_ < 1000)

  @Benchmark
  def takeWhileChunkAnyRef(): Chunk[Example] = chunkAnyRef.takeWhile(_.value < 1000)

  @Benchmark
  def takeWhileVectorAnyRef(): Vector[Example] = vectorAnyRef.takeWhile(_.value < 1000)

  @Benchmark
  def takeWhileListAnyRef(): List[Example] = listAnyRef.takeWhile(_.value < 1000)

}
