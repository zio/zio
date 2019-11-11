package zio.test

import zio.test.Assertion._

object CompileSpec
    extends ZIOBaseSpec(
      suite("CompileSpec")(
        testM("compile must return Right if the specified string is valid Scala code") {
          assertM(compile("1 + 1"), isRight(anything))
        },
        testM("compile must return Left with an error message otherwise") {
          val expected = "value ++ is not a member of Int"
          if (TestVersion.isScala2) assertM(compile("1 ++ 1"), isLeft(equalTo(expected)))
          else assertM(compile("1 ++ 1"), isLeft(anything))
        }
      )
    )
