package zio.examples.test

import zio.random.Random
import zio.stream.ZStream
import zio.test.Predicate._
import zio.test.{ assertM, suite, testM, DefaultRunnableSpec, Gen, Sample }

object GenExampleSpec
    extends DefaultRunnableSpec(
      suite("GenExampleSpec")(
        testM("Gen uppercase chars ") {

          val chars: ZStream[Random, Nothing, Sample[Random, Char]] = Gen.printableChar.filter(_.isUpper).sample

          assertM(chars.map(_.value.isUpper).runCollect, forall(isTrue))
        }
      )
    )
