package zio

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContextExecutor }

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Keep, Sink => AkkaSink, Source => AkkaSource }
import cats.effect.{ IO => CatsIO }
import fs2.{ Chunk => FS2Chunk, Stream => FS2Stream }
import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._
import zio.stream._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class StreamBenchmarks {
  @Param(Array("10000"))
  var chunkCount: Int = _

  @Param(Array("5000"))
  var chunkSize: Int = _

  implicit val system: ActorSystem          = ActorSystem("benchmarks")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  @TearDown
  def shutdown(): Unit = {
    system.terminate()
    ()
  }

  @Benchmark
  def akkaChunkFilterMapSum: Long = {
    val chunks = (1 to chunkCount).map(i => Array.fill(chunkSize)(i))
    val program = AkkaSource
      .fromIterator(() => chunks.iterator.flatten)
      .filter(_ % 2 == 0)
      .map(_.toLong)
      .toMat(AkkaSink.fold(0L)(_ + _))(Keep.right)

    Await.result(program.run(), Duration.Inf)
  }

  @Benchmark
  def fs2ChunkFilterMapSum: Long = {
    val chunks = (1 to chunkCount).map(i => FS2Chunk.array(Array.fill(chunkSize)(i)))
    val stream = FS2Stream(chunks: _*)
      .flatMap(FS2Stream.chunk(_))
      .filter(_ % 2 == 0)
      .map(_.toLong)
      .covary[CatsIO]
      .compile
      .fold(0L)(_ + _)
    stream.unsafeRunSync()
  }

  @Benchmark
  def zioChunkFilterMapSum: Long = {
    val chunks = (1 to chunkCount).map(i => Chunk.fromArray(Array.fill(chunkSize)(i)))
    val stream = ZStream
      .fromChunks(chunks: _*)
      .filter(_ % 2 == 0)
      .map(_.toLong)

    val sink   = ZSink.foldLeftChunks(0L)((s, as: Chunk[Long]) => as.fold(s)(_ + _))
    val result = stream.run(sink)

    unsafeRun(result)
  }

  @Benchmark
  def zioChunkChunkFilterMapSum: Long = {
    val chunks = Chunk.fromArray((1 to chunkCount).toArray).flatMap(i => Chunk.fromArray(Array.fill(chunkSize)(i)))
    chunks
      .filter(_ % 2 == 0)
      .map(_.toLong)
      .fold(0L)(_ + _)
  }

  @Benchmark
  def groupByBenchmark: Chunk[Unit] = {
    val chunks = (1 to chunkCount).map(_ => Chunk.fromIterable(1 to (chunkSize / 10)))
    val result = ZStream
      .fromChunks(chunks: _*)
      .groupByKey(_ % 10)((_, s) => s.drain)
      .runCollect
    unsafeRun(result)
  }

}

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class CSVStreamBenchmarks {
  @Param(Array("5000"))
  var chunkSize: Int = _

  @Param(Array("100"))
  var rows: Int = _

  @Param(Array("100"))
  var cols: Int = _

  var genCsvChunks: Array[Array[Char]] = _

  implicit val system: ActorSystem          = ActorSystem("benchmarks")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  @Setup
  def setup(): Unit =
    genCsvChunks = CSV.genChoppedCsv(rows, cols, chunkSize)

  @TearDown
  def shutdown(): Unit = {
    system.terminate()
    ()
  }

  @Benchmark
  def akkaCsvTokenize(): Done = {
    val chunks = genCsvChunks

    val program = AkkaSource
      .fromIterator(() => chunks.iterator.flatten)
      .scan((Vector.empty[Char], Vector.empty[CSV.Token])) { case ((acc, _), char) =>
        if (char == CSV.ColumnSep) {
          Vector.empty[Char] ->
            ((if (acc.nonEmpty)
                Vector(CSV.Column(acc.mkString))
              else Vector.empty[CSV.Token]) ++
              Vector(CSV.NewCol))
        } else if (char == CSV.RowSep) {
          Vector.empty[Char] ->
            ((if (acc.nonEmpty)
                Vector(CSV.Column(acc.mkString))
              else Vector.empty[CSV.Token]) ++
              Vector(CSV.NewCol))
        } else (acc :+ char) -> Vector.empty[CSV.Token]
      }
      .mapConcat(t => t._2)
      .toMat(AkkaSink.ignore)(Keep.right)

    Await.result(program.run(), Duration.Inf)
  }

  @Benchmark
  def fs2CsvTokenize(): Unit = {
    val chunks = genCsvChunks.map(FS2Chunk.array)
    val stream = FS2Stream(chunks.toIndexedSeq: _*)
      .flatMap(FS2Stream.chunk)
      .mapAccumulate(Vector.empty[Char]) { case (acc, char) =>
        if (char == CSV.ColumnSep) {
          Vector.empty[Char] ->
            ((if (acc.nonEmpty)
                Vector(CSV.Column(acc.mkString))
              else Vector.empty[CSV.Token]) ++
              Vector(CSV.NewCol))
        } else if (char == CSV.RowSep) {
          Vector.empty[Char] ->
            ((if (acc.nonEmpty)
                Vector(CSV.Column(acc.mkString))
              else Vector.empty[CSV.Token]) ++
              Vector(CSV.NewCol))
        } else (acc :+ char) -> Vector.empty[CSV.Token]
      }
      .flatMap(t => FS2Stream(t._2))
      .covary[CatsIO]
      .compile
      .drain

    stream.unsafeRunSync()
  }

  @Benchmark
  def zioCsvTokenize(): Unit = {
    val chunks = genCsvChunks.map(Chunk.fromArray)
    val stream = ZStream
      .fromChunks(chunks.toIndexedSeq: _*)
      .mapAccum[Vector[Char], Chunk[CSV.Token]](Vector.empty[Char]) { case (acc, char) =>
        if (char == CSV.ColumnSep) {
          Vector.empty[Char] ->
            ((if (acc.nonEmpty)
                Chunk(CSV.Column(acc.mkString))
              else Chunk.empty) ++
              Chunk(CSV.NewCol))
        } else if (char == CSV.RowSep) {
          Vector.empty[Char] ->
            ((if (acc.nonEmpty)
                Chunk(CSV.Column(acc.mkString))
              else Chunk.empty) ++
              Chunk(CSV.NewCol))
        } else (acc :+ char) -> Chunk.empty
      }
      .mapConcatChunk(identity)

    unsafeRun(stream.run(ZSink.drain))
  }
}

object CSV {
  sealed trait Token
  case object NewCol               extends Token
  case object NewRow               extends Token
  case class Column(value: String) extends Token

  val ColumnSep = ','
  val RowSep    = '\n'

  def alphanumeric(random: scala.util.Random, min: Int, max: Int): String = {
    val n = random.nextInt(max - min + 1) + min

    random.alphanumeric.take(n).mkString
  }

  def genCsv(cols: Int, rows: Int, min: Int = 5, max: Int = 100): String = {
    val random: scala.util.Random = new scala.util.Random(rows * cols * (min + 1) * max)

    val builder = new scala.collection.mutable.StringBuilder()

    for {
      _   <- (0 to rows).toList
      col <- (0 to cols).toList
    } {
      if (col > 0) builder
      builder ++= ColumnSep.toString

      builder ++= alphanumeric(random, min, max)

      if (col == cols - 1)
        builder ++= RowSep.toString
    }

    builder.toString
  }

  def chop(n: Int, s: String): Array[Array[Char]] =
    s.grouped(n).toArray.map(_.toArray)

  def genChoppedCsv(rows: Int, cols: Int, chunkSize: Int): Array[Array[Char]] =
    chop(chunkSize, genCsv(rows, cols))
}
