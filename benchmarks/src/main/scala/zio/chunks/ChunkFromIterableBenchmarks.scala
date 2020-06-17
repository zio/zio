package zio.chunks

import java.util.concurrent.TimeUnit

import scala.reflect.ClassTag

import org.openjdk.jmh.annotations._

import zio.Chunk
import zio.Chunk.{ fromArray, Tags }

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ChunkFromIterableBenchmarks {

  var array: Array[Int] = _
  var list: List[Int]   = _

  @Param(Array("1000"))
  var size: Int = _

  @Setup(Level.Trial)
  def setup() = {
    array = (1 to size).toArray
    list = array.toList
  }

  @Benchmark
  def fromIterableArray(): Chunk[Int] = Chunk.fromIterable(array)

  @Benchmark
  def fromIterableArrayUnsafe(): Chunk[Int] = fromIterableUnsafe(array)

  @Benchmark
  def fromIterableList(): Chunk[Int] = Chunk.fromIterable(list)

  @Benchmark
  def fromIterableListUnsafe(): Chunk[Int] = fromIterableUnsafe(list)

  private def fromIterableUnsafe[A](it: Iterable[A]): Chunk[A] =
    it match {
      case iterable =>
        val first                   = iterable.head
        implicit val A: ClassTag[A] = Tags.fromValue(first)
        fromArray(it.toArray)
    }

}
