package zio.internal

import zio.Chunk
import zio.test._
import zio.ZIOBaseSpec

object GrowableArraySpec extends ZIOBaseSpec {
  import zio.internal.PinchableArray

  def make(hint: Int = 0): PinchableArray[String] = new PinchableArray[String](hint)

  val initialState =
    test("initial state") {
      val a = make()

      assertTrue(a.length == 0)
    }

  val addAFew =
    test("add a few elements") {
      val a = make()

      a += "1"
      a += "2"
      a += "3"

      assertTrue(a.asChunk() == Chunk("1", "2", "3"))
    }

  val addMany =
    test("add many elements") {
      val range      = (0 to 100).map(_.toString)
      val chunkRange = Chunk.fromIterable(range)

      val a = make(1)

      (0 to 100).foreach { number =>
        a += number.toString
      }

      assertTrue(a.length == chunkRange.size)
      assertTrue(a.asChunk() == chunkRange)
    }

  val buildResets =
    test("pinch does a reset") {
      val range      = (0 to 100).map(_.toString)
      val chunkRange = Chunk.fromIterable(range)
      val a          = make(1)

      (0 to 100).foreach { number =>
        a += number.toString
      }

      assertTrue(chunkRange == a.pinch() && a.size == 0)
    }

  val iterating =
    test("iteration") {
      val range      = (0 to 100).map(_.toString)
      val chunkRange = Chunk.fromIterable(range)
      val a          = make(1)

      (0 to 100).foreach { number =>
        a += number.toString
      }

      var i = ""
      a.foreach(i += _)

      assertTrue(i == chunkRange.reduce(_ + _))
    }

  def spec =
    suite("GrowableArraySpec")(
      initialState,
      addAFew,
      addMany,
      buildResets,
      iterating
    )
}
