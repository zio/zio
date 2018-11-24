package scalaz.zio.stream

import org.specs2._
import org.specs2.specification.core.SpecStructure

class ChunkSpec extends Specification with ScalaCheck {
  def is: SpecStructure =
    "ChunkSpec".title ^
      s2"""
  chunk apply $apply
  chunk length $length
  chunk equality prop $equality
  chunk inequality $inequality
  flatMap chunk $flatMap
  map chunk $map
  materialize chunk $materialize
  foldLeft chunk $foldLeft
  filter chunk $filter
  drop chunk $drop
  take chunk $take
  dropWhile chunk $dropWhile
  takeWhile chunk $takeWhile
  toArray $toArray
  foreach $foreach
  concat chunk $concat
  An Array-based chunk that is filtered empty and mapped must not throw NPEs. $nullArrayBug
  toArray on concat of a slice must work properly. $toArrayOnConcatOfSlice
  toArray on concat of empty and integers must work properly. $toArrayOnConcatOfEmptyAndInts
  Chunk.filter that results in an empty Chunk must use Chunk.empty $filterConstFalseResultsInEmptyChunk
    """

  import ArbitraryChunk._
  import org.scalacheck._

  private def apply = {
    implicit val chunkGen: Gen[(Chunk[Int], Int)] = for {
      chunk <- Arbitrary.arbitrary[Chunk[Int]].filter(_.length > 0)
      len   <- Gen.chooseNum(0, chunk.length - 1)
    } yield (chunk, len)

    prop { t: (Chunk[Int], Int) =>
      t._1.apply(t._2) must_=== t._1.toSeq.apply(t._2)
    }.setArbitrary(Arbitrary(chunkGen))
  }

  private def length =
    prop { chunk: Chunk[Int] =>
      chunk.length must_=== chunk.toSeq.length
    }

  private def equality =
    prop((c1: Chunk[Int], c2: Chunk[Int]) => c1.equals(c2) must_=== c1.toSeq.equals(c2.toSeq))

  private def inequality =
    Chunk(1, 2, 3, 4, 5) must_!== Chunk(1, 2, 3, 4, 5, 6)

  private def flatMap =
    prop { (c: Chunk[Int], f: Int => Chunk[Int]) =>
      c.flatMap(f).toSeq must_=== c.toSeq.flatMap(f.andThen(_.toSeq))
    }

  private def map =
    prop { (c: Chunk[Int], f: Int => String) =>
      c.map(f).toSeq must_=== c.toSeq.map(f)
    }

  private def materialize =
    prop { c: Chunk[Int] =>
      c.materialize.toSeq must_=== c.toSeq
    }

  private def foldLeft =
    prop { (s0: String, f: (String, Int) => String, c: Chunk[Int]) =>
      c.foldLeft(s0)(f) must_=== c.toArray.foldLeft(s0)(f)
    }

  private def filter =
    prop { (chunk: Chunk[String], p: String => Boolean) =>
      chunk.filter(p).toSeq must_=== chunk.toSeq.filter(p)
    }

  private def drop =
    prop { (chunk: Chunk[Int], n: Int) =>
      chunk.drop(n).toSeq must_=== chunk.toSeq.drop(n)
    }

  private def take =
    prop { (c: Chunk[Int], n: Int) =>
      c.take(n).toSeq must_=== c.toSeq.take(n)
    }

  private def nullArrayBug = {
    val c = Chunk.fromArray(Array(1, 2, 3, 4, 5))

    // foreach should not throw
    c.foreach(_ => ())

    c.filter(_ => false).map(_ * 2).length must_=== 0
  }

  private def concat = prop { (c1: Chunk[Int], c2: Chunk[Int]) =>
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

  private def filterConstFalseResultsInEmptyChunk =
    Chunk.fromArray(Array(1, 2, 3)).filter(_ => false) must_=== Chunk.empty

  private def dropWhile =
    prop { (c: Chunk[Int], p: Int => Boolean) =>
      c.dropWhile(p).toSeq must_=== c.toSeq.dropWhile(p)
    }

  private def takeWhile = prop { (c: Chunk[Int], p: Int => Boolean) =>
    c.takeWhile(p).toSeq must_=== c.toSeq.takeWhile(p)
  }

  private def toArray = prop { (c: Chunk[Int]) =>
    c.toArray.toSeq must_=== c.toSeq
  }

  private def foreach = prop { (c: Chunk[Int]) =>
    var sum = 0
    c.foreach(sum += _)

    sum must_=== c.toSeq.sum
  }
}
