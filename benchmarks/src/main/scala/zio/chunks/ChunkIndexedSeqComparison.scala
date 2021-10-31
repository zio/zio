package zio.chunks

import org.openjdk.jmh.annotations._
import zio._

import java.util.concurrent.TimeUnit
import scala.collection.IntStepper
import scala.collection.IndexedSeqView

@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@Threads(1)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
class ChunkIndexedSeqComparison {
  @Param(Array("1000"))
  var size: Int = _

  var chunk: Chunk[Int] = _
  var zipped: Chunk[(Int, Int)] = _
  var tripleZipped: Chunk[(Int, Int, Int)] = _
  var transposable: Chunk[Chunk[Int]] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
    zipped = chunk.map(num => (num, num + 1))
    tripleZipped = chunk.map(num => (num, num + 1, num + 2))
    transposable = Chunk.fromArray(Array(chunk, chunk))
  }

  @Benchmark
  def toSet(): Set[Int] = chunk.toSet

  @Benchmark
  def addString(): StringBuilder = {
    val stringBuilder = new StringBuilder()
    chunk.addString(stringBuilder)
  }

  @Benchmark
  def appended(): Chunk[Int] = chunk.appended(1)

  @Benchmark
  def appendedAll(): Chunk[Int] = chunk.appendedAll(Array(1))

  @Benchmark
  def canEqual(): Boolean = chunk.canEqual(chunk)

  @Benchmark
  def collect(): Chunk[Int] = chunk.collect(num => num + 1)

  @Benchmark
  def collectFirst(): Option[Int] = chunk.collectFirst(num => num + 1)

  @Benchmark
  def combinations(): Iterator[Chunk[Int]] = chunk.combinations(size)

  @Benchmark
  def concat(): Chunk[Int] = chunk.concat(Array(1))

  @Benchmark
  def contains(): Boolean = chunk.contains(1)

  @Benchmark
  def containsSlice(): Boolean = chunk.containsSlice(Seq(1,2,3))

  @Benchmark
  def copyToArray(): Int = chunk.copyToArray(Array())

  @Benchmark
  def count(): Int = chunk.count(num => num < size)

  @Benchmark
  def diff(): Chunk[Int] = chunk.diff(Seq(1,2,3))

  @Benchmark
  def distinct(): Chunk[Int] = chunk.distinct

  @Benchmark
  def distinctBy(): Chunk[Int] = chunk.distinctBy(num => num)

  @Benchmark
  def dropRight(): Chunk[Int] = chunk.dropRight(1)

  // @Benchmark
  // def elementWise(): ElementWiseExtractor = chunk.elementWise

  @Benchmark
  def endsWith(): Boolean = chunk.endsWith(Seq(size - 1, size))

  @Benchmark
  def filterNot(): Chunk[Int] = chunk.filterNot(num => num < size)

  @Benchmark
  def findLast(): Option[Int] = chunk.findLast(num => num == 1)

  @Benchmark
  def flatMap(): Chunk[Int] = chunk.flatMap(num => Seq(0, num))

  @Benchmark
  def fold(): Int = chunk.fold(0)((num: Int, acc: Int) => num + acc)

  @Benchmark
  def foreach(): Unit = chunk.foreach(num => num + 1)

  @Benchmark
  def groupBy(): Map[Boolean, Chunk[Int]] = 
    chunk.groupBy(num => num > 15)

  @Benchmark
  def groupMap(): Map[Boolean, Chunk[Int]] =
    chunk.groupMap(num => num > 15)(num => num + 2)
  
  @Benchmark
  def groupMapReduce(): Map[Boolean, Int] = {
    val grouper: (Int) => Boolean = (num => num > 15)
    val mapper: (Int) => Int = (num => num - 1)
    val reducer = (num1: Int, num2: Int) => num1 + num2
    chunk.groupMapReduce(grouper)(mapper)(reducer)
  }

  @Benchmark
  def grouped(): Iterator[Chunk[Int]] = chunk.grouped(size / 2)
  
  @Benchmark
  def indexOf(): Int = chunk.indexOf(1)
  
  @Benchmark
  def indexOfSlice(): Int = chunk.indexOfSlice(Seq(5,6,7)) 
  
  @Benchmark
  def indices(): Range = chunk.indices
  
  @Benchmark
  def init(): Chunk[Int] = chunk.init
  
  @Benchmark
  def inits(): Iterator[Chunk[Int]] = chunk.inits
  
  @Benchmark
  def intersect(): Chunk[Int] = chunk.intersect(Array(1))
  
  @Benchmark
  def isDefinedAt(): Boolean = chunk.isDefinedAt(size) 
  
  @Benchmark
  def isTraversableAgain(): Boolean = chunk.isTraversableAgain
  
  // @Benchmark
  // def iterableFactory(): SeqFactory[Chunk[Int]] = chunk.iterableFactory()
  
  @Benchmark
  def iterator(): Iterator[Int] = chunk.iterator
  
  @Benchmark
  def knownSize(): Int = chunk.knownSize
  
  @Benchmark
  def last(): Int = chunk.last
  
  @Benchmark
  def lastIndexOf(): Int = chunk.lastIndexOf(size)
  
  @Benchmark
  def lastIndexOfSlice(): Int = chunk.lastIndexOfSlice(Seq(size - 1, size))
  
  @Benchmark
  def lastIndexWhere(): Int = chunk.lastIndexWhere(num => num == size)
  
  @Benchmark
  def lengthCompare(): Int = chunk.lengthCompare(size)

  @Benchmark
  def max(): Int = chunk.max
  
  @Benchmark
  def maxBy(): Int = chunk.maxBy(num => num)
  
  @Benchmark
  def maxByOption(): Option[Int] = chunk.maxByOption(num => num)
  
  @Benchmark
  def maxOption(): Option[Int] = chunk.maxOption
  
  @Benchmark
  def min(): Int = chunk.min
  
  @Benchmark
  def minBy(): Int = chunk.minBy(num => num)
  
  @Benchmark
  def minByOption(): Option[Int] = chunk.minByOption(num => num)
  
  @Benchmark
  def minOption(): Option[Int] = chunk.minOption
  
  @Benchmark
  def mkString(): String = chunk.mkString
  
  @Benchmark
  def padTo(): Chunk[Int] = chunk.padTo(2 * size, 1)
  
  @Benchmark
  def partition(): (Chunk[Int], Chunk[Int]) = chunk.partition(num => num > (size / 2))
  
  @Benchmark
  def patch(): Chunk[Int] = chunk.patch(0, Seq(1,2,3,4,5), 5)
  
  @Benchmark
  def permutations(): Iterator[Chunk[Int]] = chunk.permutations
  
  @Benchmark
  def prepended(): Chunk[Int] = chunk.prepended(0)
  
  @Benchmark
  def prependedAll(): Chunk[Int] = chunk.prependedAll(Seq(-1,0))
  
  @Benchmark
  def product(): Int = chunk.product
  
  @Benchmark
  def reduce(): Int = chunk.reduce((curr, acc) => curr + acc)
  
  @Benchmark
  def reduceLeft(): Int = chunk.reduceLeft((curr, acc) => curr + acc)
  
  @Benchmark
  def reduceLeftOption(): Option[Int] = chunk.reduceLeftOption((curr, acc) => curr + acc)
  
  @Benchmark
  def reduceOption(): Option[Int] = chunk.reduceOption((curr, acc) => curr + acc)
  
  @Benchmark
  def reduceRight(): Int = chunk.reduceRight((curr, acc) => curr + acc)
  
  @Benchmark
  def reduceRightOption(): Option[Int] = chunk.reduceRightOption((curr, acc) => curr + acc)
  
  @Benchmark
  def reverse(): Chunk[Int] = chunk.reverse
  
  @Benchmark
  def reverseIterator(): Iterator[Int] = chunk.reverseIterator
  
  @Benchmark
  def sameElements(): Boolean = chunk.sameElements(chunk)
  
  @Benchmark
  def scan(): Chunk[Int] = chunk.scan(0)((num1, num2) => num1 + num2)
  
  @Benchmark
  def scanLeft(): Chunk[Int] = chunk.scanLeft(0)((num1, num2) => num1 + num2)
  
  @Benchmark
  def scanRight(): Chunk[Int] = chunk.scanRight(0)((num1, num2) => num1 + num2)
  
  @Benchmark
  def segmentLength(): Int = chunk.segmentLength(num => num < size)
  
  @Benchmark
  def sizeBenchmark(): Int = chunk.size
  
  @Benchmark
  def sizeCompare(): Int = chunk.sizeCompare(size - 1)
  
  @Benchmark
  def slice(): Chunk[Int] = chunk.slice(0, size)
  
  @Benchmark
  def sliding(): Iterator[Chunk[Int]] = chunk.sliding(2,3)
  
  @Benchmark
  def sortBy(): Chunk[Int] = chunk.sortBy(num => num)
  
  @Benchmark
  def sortWith(): Chunk[Int] = chunk.sortWith((num1, num2) => num1 > num2)
  
  @Benchmark
  def sorted(): Chunk[Int] = chunk.sorted
  
  @Benchmark
  def span(): (Chunk[Int], Chunk[Int]) = chunk.span(num => num > (size / 2))
  
  @Benchmark
  def startsWith(): Boolean = chunk.startsWith(Seq(1, 2))
  
  @Benchmark
  def stepper(): IntStepper = chunk.stepper
  
  @Benchmark
  def sum(): Int = chunk.sum
  
  @Benchmark
  def tail(): Chunk[Int] = chunk.tail
  
  @Benchmark
  def tails(): Iterator[Chunk[Int]] = chunk.tails
  
  @Benchmark
  def takeRight(): Chunk[Int] = chunk.takeRight(size)

  @Benchmark
  def tapEach(): Chunk[Int] = chunk.tapEach(num => num + 1)
  
  // @Benchmark
  // def to(): Seq[Int] = chunk.to(SeqFactory())

  // @Benchmark
  // def toBuffer(): Buffer[Int] = chunk.toBuffer
  
  @Benchmark
  def toIndexedSeq(): IndexedSeq[Int] = chunk.toIndexedSeq
  
  @Benchmark
  def toIterable(): Chunk[Int] = chunk.toIterable
  
  // @Benchmark
  // def toMap(): Map[]
  
  @Benchmark
  def toSeq(): Chunk[Int] = chunk.toSeq
  
  @Benchmark
  def transpose(): Chunk[Chunk[Int]] = transposable.transpose

  @Benchmark
  def unzip(): (Chunk[Int], Chunk[Int]) = zipped.unzip

  @Benchmark
  def unzip3(): (Chunk[Int], Chunk[Int], Chunk[Int]) = tripleZipped.unzip3

  @Benchmark
  def updated(): Chunk[Int] = chunk.updated(0, 0)
  
  @Benchmark
  def view(): IndexedSeqView[Int] = chunk.view
  
  // needs some withFilter stuff for this method to work
  // @Benchmark
  // def withFilter(): WithFilter[Int] = chunk.withFilter(num => num > size / 2)
}