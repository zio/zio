package zio

import zio.test.Assertion._
import zio.test._

object ChunkBuilderSpec extends ZIOBaseSpec {

  def spec = suite("ChunkBuilderSpec")(
    suite("Boolean")(
      test("addOne")(
        check(Gen.chunkOf(Gen.boolean)) { as =>
          val builder = new ChunkBuilder.Boolean
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.boolean)) { as =>
          val builder = new ChunkBuilder.Boolean
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Boolean
        assert(builder.toString)(equalTo("ChunkBuilder.Boolean"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.boolean)) { as =>
          val builder = new ChunkBuilder.Boolean
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Byte")(
      test("addOne")(
        check(Gen.chunkOf(Gen.byte)) { as =>
          val builder = new ChunkBuilder.Byte
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.byte)) { as =>
          val builder = new ChunkBuilder.Byte
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Byte
        assert(builder.toString)(equalTo("ChunkBuilder.Byte"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.byte)) { as =>
          val builder = new ChunkBuilder.Byte
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Char")(
      test("addOne")(
        check(Gen.chunkOf(Gen.char)) { as =>
          val builder = new ChunkBuilder.Char
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.char)) { as =>
          val builder = new ChunkBuilder.Char
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Char
        assert(builder.toString)(equalTo("ChunkBuilder.Char"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.char)) { as =>
          val builder = new ChunkBuilder.Char
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Double")(
      test("addOne")(
        check(Gen.chunkOf(Gen.double)) { as =>
          val builder = new ChunkBuilder.Double
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.double)) { as =>
          val builder = new ChunkBuilder.Double
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Double
        assert(builder.toString)(equalTo("ChunkBuilder.Double"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.double)) { as =>
          val builder = new ChunkBuilder.Double
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Float")(
      test("addOne")(
        check(Gen.chunkOf(Gen.float)) { as =>
          val builder = new ChunkBuilder.Float
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.float)) { as =>
          val builder = new ChunkBuilder.Float
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Float
        assert(builder.toString)(equalTo("ChunkBuilder.Float"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.float)) { as =>
          val builder = new ChunkBuilder.Float
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Int")(
      test("addOne")(
        check(Gen.chunkOf(Gen.int)) { as =>
          val builder = new ChunkBuilder.Int
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.int)) { as =>
          val builder = new ChunkBuilder.Int
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Int
        assert(builder.toString)(equalTo("ChunkBuilder.Int"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.int)) { as =>
          val builder = new ChunkBuilder.Int
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Long")(
      test("addOne")(
        check(Gen.chunkOf(Gen.long)) { as =>
          val builder = new ChunkBuilder.Long
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.long)) { as =>
          val builder = new ChunkBuilder.Long
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Long
        assert(builder.toString)(equalTo("ChunkBuilder.Long"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.long)) { as =>
          val builder = new ChunkBuilder.Long
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("Short")(
      test("addOne")(
        check(Gen.chunkOf(Gen.short)) { as =>
          val builder = new ChunkBuilder.Short
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.short)) { as =>
          val builder = new ChunkBuilder.Short
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("toString") {
        val builder = new ChunkBuilder.Short
        assert(builder.toString)(equalTo("ChunkBuilder.Short"))
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.short)) { as =>
          val builder = new ChunkBuilder.Short
          testIsEmpty(builder, as)
        }
      }
    ),
    suite("AnyRef")(
      test("addOne")(
        check(Gen.chunkOf(Gen.string)) { as =>
          val builder = ChunkBuilder.make[String]()
          as.foreach(builder += _)
          assert(builder.result())(equalTo(as))
        }
      ),
      test("addAll") {
        check(Gen.chunkOf(Gen.string)) { as =>
          val builder = ChunkBuilder.make[String]()
          builder ++= as
          assert(builder.result())(equalTo(as))
        }
      },
      test("isEmpty") {
        check(Gen.chunkOf1(Gen.string)) { as =>
          val builder = ChunkBuilder.make[String]()
          testIsEmpty(builder, as)
        }
      }
    )
  )

  private def testIsEmpty[A](builder: ChunkBuilder[A], as: NonEmptyChunk[A]): TestResult = {
    val a0 = builder.isEmpty
    builder.sizeHint(16)
    val a1 = builder.isEmpty
    builder += as(0)
    val a2 = builder.isEmpty
    builder ++= as
    val a3 = builder.isEmpty
    builder.clear()
    val a4 = builder.isEmpty
    assertTrue(a0, a1, !a2, !a3, a4)
  }
}
