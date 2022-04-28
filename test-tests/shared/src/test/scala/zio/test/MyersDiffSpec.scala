package zio.test

import zio.Chunk
import zio.test.Assertion._
import zio.test.internal.myers.{DiffResult, MyersDiff}

object MyersDiffSpec extends ZIOBaseSpec {

  def spec = suite("MyersDiffSpec")(
    test("diffing works for only additions both ways") {
      val original = ""
      val modified = "ADDITIONS"

      assert(MyersDiff.diffWords(original, modified).applyChanges(original))(equalTo(modified)) &&
      assert(MyersDiff.diffWords(original, modified).invert.applyChanges(modified))(equalTo(original))
    },
    test("diffing works for only deletions both ways") {
      val original = "DELETIONS"
      val modified = ""

      assert(MyersDiff.diffWords(original, modified).applyChanges(original))(equalTo(modified)) &&
      assert(MyersDiff.diffWords(original, modified).invert.applyChanges(modified))(equalTo(original))
    },
    test("diffing works for two empty strings both ways") {
      val original = ""
      val modified = ""

      assert(MyersDiff.diffWords(original, modified).applyChanges(original))(equalTo(modified)) &&
      assert(MyersDiff.diffWords(original, modified).invert.applyChanges(modified))(equalTo(original))
    },
    test("diffing works for Myers example both ways") {
      val original = "ABCABBA"
      val modified = "CBABAC"

      assert(MyersDiff.diffWords(original, modified).applyChanges(original))(equalTo(modified)) &&
      assert(MyersDiff.diffWords(original, modified).invert.applyChanges(modified))(equalTo(original))
    },
    test("diffing for Myers example produces a sane DiffResult") {
      val original = "ABCABBA"
      val modified = "CBABAC"

      import zio.test.internal.myers.Action._
      assertTrue(
        MyersDiff.diffWords(original, modified) ==
          DiffResult(
            Chunk(
              Delete("A"),
              Insert("C"),
              Keep("B"),
              Delete("C"),
              Keep("A"),
              Keep("B"),
              Delete("B"),
              Keep("A"),
              Insert("C")
            )
          )
      )
    },
    test("diffing works for all random strings both ways") {
      check(Gen.string, Gen.string) { (original, modified) =>
        assert(MyersDiff.diffWords(original, modified).applyChanges(original))(equalTo(modified)) &&
        assert(MyersDiff.diffWords(original, modified).invert.applyChanges(modified))(equalTo(original))
      }
    }
  )
}
