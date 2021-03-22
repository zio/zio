package zio.chunks

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Scope, Setup, State}
import zio.Chunk

import java.util.concurrent.TimeUnit
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ChunkCreationBenchmarks {

  var a01: Int = _
  var a02: Int = _
  var a03: Int = _
  var a04: Int = _
  var a05: Int = _
  var a06: Int = _
  var a07: Int = _
  var a08: Int = _
  var a09: Int = _
  var a10: Int = _
  var a11: Int = _
  var a12: Int = _
  var a13: Int = _
  var a14: Int = _
  var a15: Int = _
  var a16: Int = _
  var a17: Int = _
  var a18: Int = _
  var a19: Int = _
  var a20: Int = _
  var a21: Int = _
  var a22: Int = _
  var a23: Int = _
  var a24: Int = _
  var a25: Int = _
  var a26: Int = _
  var a27: Int = _
  var a28: Int = _
  var a29: Int = _
  var a30: Int = _
  var a31: Int = _
  var a32: Int = _

  @Setup
  def setup(): Unit = {
    a01 = 1
    a02 = 2
    a03 = 3
    a04 = 4
    a05 = 5
    a06 = 6
    a07 = 7
    a08 = 8
    a09 = 9
    a10 = 10
    a11 = 11
    a12 = 12
    a13 = 13
    a14 = 14
    a15 = 15
    a16 = 16
    a17 = 17
    a18 = 18
    a19 = 19
    a20 = 20
    a21 = 21
    a22 = 22
    a23 = 23
    a24 = 24
    a25 = 25
    a26 = 26
    a27 = 27
    a28 = 28
    a29 = 29
    a30 = 30
    a31 = 31
    a32 = 32

  }

  @Benchmark
  def arrayCreate01(): Array[Int] = Array(a01)

  @Benchmark
  def arrayCreate02(): Array[Int] = Array(a01, a02)

  @Benchmark
  def arrayCreate04(): Array[Int] = Array(a01, a02, a03, a04)

  @Benchmark
  def arrayCreate08(): Array[Int] = Array(a01, a02, a03, a04, a05, a06, a07, a08)

  @Benchmark
  def arrayCreate16(): Array[Int] =
    Array(a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, a16)

  @Benchmark
  def arrayCreate32(): Array[Int] =
    Array(
      a01,
      a02,
      a03,
      a04,
      a05,
      a06,
      a07,
      a08,
      a09,
      a10,
      a11,
      a12,
      a13,
      a14,
      a15,
      a16,
      a17,
      a18,
      a19,
      a20,
      a21,
      a22,
      a23,
      a24,
      a25,
      a26,
      a27,
      a28,
      a29,
      a30,
      a31,
      a32
    )

  @Benchmark
  def vectorCreate01(): Vector[Int] = Vector(a01)

  @Benchmark
  def vectorCreate02(): Vector[Int] = Vector(a01, a02)

  @Benchmark
  def vectorCreate04(): Vector[Int] = Vector(a01, a02, a03, a04)

  @Benchmark
  def vectorCreate08(): Vector[Int] = Vector(a01, a02, a03, a04, a05, a06, a07, a08)

  @Benchmark
  def vectorCreate16(): Vector[Int] =
    Vector(a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, a16)

  @Benchmark
  def vectorCreate32(): Vector[Int] =
    Vector(
      1,
      a02,
      a03,
      a04,
      a05,
      a06,
      a07,
      a08,
      a09,
      a10,
      a11,
      a12,
      a13,
      a14,
      a15,
      a16,
      a17,
      a18,
      a19,
      a20,
      a21,
      a22,
      a23,
      a24,
      a25,
      a26,
      a27,
      a28,
      a29,
      a30,
      a31,
      a32
    )

  @Benchmark
  def chunkCreate01(): Chunk[Int] = Chunk(a01)

  @Benchmark
  def chunkCreate02(): Chunk[Int] = Chunk(a01, a02)

  @Benchmark
  def chunkCreate04(): Chunk[Int] = Chunk(a01, a02, a03, a04)

  @Benchmark
  def chunkCreate08(): Chunk[Int] = Chunk(a01, a02, a03, a04, a05, a06, a07, a08)

  @Benchmark
  def chunkCreate16(): Chunk[Int] =
    Chunk(a01, a02, a03, a04, a05, a06, a07, a08, a09, a10, a11, a12, a13, a14, a15, a16)

  @Benchmark
  def chunkCreate32(): Chunk[Int] =
    Chunk(
      a01,
      a02,
      a03,
      a04,
      a05,
      a06,
      a07,
      a08,
      a09,
      a10,
      a11,
      a12,
      a13,
      a14,
      a15,
      a16,
      a17,
      a18,
      a19,
      a20,
      a21,
      a22,
      a23,
      a24,
      a25,
      a26,
      a27,
      a28,
      a29,
      a30,
      a31,
      a32
    )

}
