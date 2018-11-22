package scalaz.zio.stream

import org.specs2._
import org.specs2.specification.core.SpecStructure

class ChunkSpec extends Specification with ScalaCheck {
  def is: SpecStructure = "ChunkSpec".title ^
    s2"""
  chunk apply $applyChunk
  chunk length $chunkLength
  chunk equality $chunkEquality
  chunk equality prop $chunkEqualityProp
  chunk inequality $chunkInequality
  flatMap chunk $flatMapChunk
  map chunk $mapChunk
  materialize chunk $materializeChunk
  foldLeft chunk $foldLeftChunk
  filter chunk $filterChunk
  drop chunk $dropChunk
  drop singleton chunk $dropSingletonChunk
  drop slice chunk $dropSliceChunk
  take chunk $takeChunk
  take singleton chunk $takeSingletonChunk
  take slice chunk $takeSliceChunk
  dropWhile chunk $dropWhile
  dropWhile singleton chunk $dropWhileSingleton
  dropWhile slice chunk $dropWhileSlice
  dropWhile array chunk $dropWhileArray
  takeWhile chunk $takeWhile
  takeWhile singleton chunk $takeWhileSingleton
  takeWhile slice chunk $takeWhileSlice
  takeWhile array chunk $takeWhileArray
  concat chunk $concatChunk
  An Array-based chunk that is filtered empty and mapped must not throw NPEs. $nullArrayBug
  toArray on concat of a slice must work properly. $toArrayOnConcatOfSlice
  toArray on concat of empty and integers must work properly. $toArrayOnConcatOfEmptyAndInts
  Chunk.filter that results in an empty Chunk must use Chunk.empty $filterConstFalseResultsInEmptyChunk
  Chunk.Slice toArray $sliceToArray
  Chunk.Slice foreach $sliceForeach
  Chunk.slice toArray $sliceToArray
    """

  import ArbitraryChunk._
  import org.scalacheck._

  private def applyChunk = {
    implicit val abStringGen: Gen[(Chunk[Int], Int)] = for {
      chunk <- Arbitrary.arbitrary[Chunk[Int]].filter(_.length > 0)
      len <- Gen.chooseNum(0, chunk.length - 1)
    } yield (chunk, len)

    prop { t: (Chunk[Int], Int) =>
      t._1.apply(t._2) must_=== t._1.toSeq.apply(t._2)
    }.setArbitrary(Arbitrary(abStringGen))
  }

  private def chunkLength =
    prop { chunk: Chunk[Int] => chunk.length must_=== chunk.toSeq.length }

  private def chunkEqualityProp =
    prop((c1: Chunk[Int], c2: Chunk[Int]) => c1.equals(c2) must_=== c1.toSeq.equals(c2.toSeq))

  private def chunkEquality =
    Chunk(1, 2, 3, 4, 5) must_=== Chunk(1, 2, 3, 4, 5)

  private def chunkInequality =
    Chunk(1, 2, 3, 4, 5) must_!== Chunk(1, 2, 3, 4, 5, 6)

  private def flatMapChunk =
    prop { (c: Chunk[Int], f: Int => Chunk[Int]) =>
      c.flatMap(f).toSeq must_=== c.toSeq.flatMap(f.andThen(_.toSeq))
    }

  private def mapChunk =
    prop { (c: Chunk[Int], f: Int => String) =>
      c.map(f).toSeq must_=== c.toSeq.map(f)
    }

  private def materializeChunk =
    prop { c: Chunk[Int] => c.materialize.toSeq must_=== c.toSeq }

  private def foldLeftChunk =
    prop { (s0: String, f: (String, Int) => String, c: Chunk[Int]) =>
      c.foldLeft(s0)(f) must_=== c.toArray.foldLeft(s0)(f)
    }

  private def filterChunk =
    prop { (chunk: Chunk[String], p: String => Boolean) => chunk.filter(p).toSeq must_=== chunk.toSeq.filter(p) }

  private def dropChunk =
    prop { (chunk: Chunk[Int], n: Int) => chunk.drop(n).toSeq must_=== chunk.toSeq.drop(n) }

  private def dropSingletonChunk =
    Chunk(1).drop(4) must_=== Chunk.empty

  private def takeChunk =
    prop {
      (c: Chunk[Int], n: Int) =>
        c.take(n).toSeq must_=== c.toSeq.take(n)
    }

  private def takeSingletonChunk =
    Chunk(1).take(4) must_=== Chunk(1)

  private def nullArrayBug = {
    val c = Chunk.fromArray(Array(1, 2, 3, 4, 5))

    // foreach should not throw
    c.foreach(_ => ())

    c.filter(_ => false).map(_ * 2).length must_=== 0
  }

  private def concatChunk = prop { (c1: Chunk[Int], c2: Chunk[Int]) =>
    (c1 ++ c2).toSeq must_=== (c1.toSeq ++ c2.toSeq)
  }

  private def toArrayOnConcatOfSlice = {
    val onlyOdd: Int => Boolean = _ % 2 != 0
    val concat = Chunk(1, 1, 1).filter(onlyOdd) ++
      Chunk(2, 2, 2).filter(onlyOdd) ++
      Chunk(3, 3, 3).filter(onlyOdd)

    val array = concat.toArray

    array must_=== Array(1, 1, 1, 3, 3, 3)
  }

  private def toArrayOnConcatOfEmptyAndInts =
    (Chunk.empty ++ Chunk.fromArray(Array(1, 2, 3))).toArray must_=== Array(1, 2, 3)

  private def filterConstFalseResultsInEmptyChunk = Chunk.fromArray(Array(1, 2, 3)).filter(_ => false) must_=== Chunk.empty

  private def dropWhile =
    prop {
      (c: Chunk[Int], p: Int => Boolean) =>
        c.dropWhile(p).toSeq must_=== c.toSeq.dropWhile(p)
    }

  private def takeWhile = prop { (c: Chunk[Int], p: Int => Boolean) =>
    c.takeWhile(p).toArray.toSeq must_=== c.toArray.toSeq.takeWhile(p)
  }

  private def sliceToArray =
    Chunk.fromArray(Array(1, 2, 3, 4)).dropWhile(_ < 3).toArray must_=== Array(3, 4)

  private def dropSliceChunk =
    Chunk(1, 2, 3, 4, 5).dropWhile(_ < 3).drop(2) must_=== Chunk(5)

  private def takeSliceChunk =
    Chunk(1, 2, 3, 4, 5).dropWhile(_ < 3).take(2) must_=== Chunk(3, 4)

  private def dropWhileSingleton =
    Chunk(1).dropWhile(_ == 1) must_=== Chunk.empty

  private def dropWhileSlice =
    Chunk(1, 2, 3, 4, 5).dropWhile(_ == 1).dropWhile(_ < 4) must_=== Chunk(4, 5)

  def dropWhileArray =
    Chunk(1, 2, 3, 4, 5).dropWhile(_ <= 3) must_=== Chunk(4, 5)

  def takeWhileSlice = Chunk(1, 2, 3, 4, 5).takeWhile(_ <= 4).takeWhile(_ <= 2) must_=== Chunk(1, 2)

  def takeWhileArray = Chunk(1, 2, 3, 4, 5).takeWhile(_ <= 3) must_=== Chunk(1, 2, 3)

  def sliceForeach = {
    var sum = 0

    val c = Chunk(1, 1, 1, 1, 1).take(3)
    c.foreach(sum += _)

    sum must_=== 3
  }

  private def takeWhileSingleton =
    Chunk(1).takeWhile(_ == 1) must_=== Chunk(1)
}
