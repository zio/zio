package zio.examples.test

import zio.ZIO
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

          val customAssertion = assertionDirect[CharInt]("Char is uppercase AND Int is within the range")()(
            ci => isUpper(ci.c) && isWithin(0, 1000).run(ci.i)
          )

          assertM(charIntGen.runCollect, forall(customAssertion))

        },
        testM("Gen Non-empty binary tree") {

          sealed trait Tree[+A]
          case class Leaf[A](value: A)                      extends Tree[A]
          case class Node[A](left: Tree[A], right: Tree[A]) extends Tree[A]
          case object EmptyNode                             extends Tree[Nothing]

          def genTree[A](g: Gen[Random, A]): Gen[Random, Tree[A]] =
            Gen.oneOf(Gen.oneOf(g.map(Leaf(_)), Gen.const(EmptyNode)), for {
              left  <- Gen.suspend(genTree(g))
              right <- Gen.suspend(genTree(g))
            } yield Node(left, right))

          val nonEmptyTree: ZIO[Random, Nothing, List[Tree[Int]]] = genTree(Gen.int(0, 10)).filter {
            case EmptyNode => false
            case _         => true
          }.sample.map(_.value).runCollect

          val assertion = not(equalTo(EmptyNode))

          assertM(nonEmptyTree, forall(assertion))

        }
      )
    )
