package zio.examples.test

import java.util.function.Predicate

import zio.random.Random
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._

object GenExampleSpec
    extends DefaultRunnableSpec(
      suite("GenExampleSpec")(
        testM("Gen uppercase chars ") {

          val chars: ZStream[Random, Nothing, Sample[Random, Char]] = Gen.printableChar.filter(_.isUpper).sample

          assertM(chars.map(_.value.isUpper).runCollect, forall(isTrue))

        },
        testM("Gen product through composition") {

          case class CharInt(c: Char, i: Int)

          val chars = Gen.printableChar.sample.map(_.value).filter(_.isUpper)

          val ints = Gen.int(0, 1000).sample.map(_.value)

          val charIntGen = (chars zip ints).map(CharInt.tupled)

          val isUpper: Assertion[Char] = assertion[Char]("String is not empty")()(_.isUpper)

          val customPredicate = assertionDirect[CharInt]("Char is uppercase AND Int is within the range")()( ci => isUpper(ci.c) && isWithin(0, 1000).run(ci.i))

          assertM(charIntGen.runCollect, forall(customPredicate))

        },
      )
    )
