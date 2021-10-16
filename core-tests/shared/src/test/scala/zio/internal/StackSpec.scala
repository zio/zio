package zio.internal

import zio.test.Assertion.equalTo
import zio.test.{Gen, Sized, ZSpec, assert, checkAll}
import zio.{Has, Random, ZIOBaseSpec}

object StackSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackSpec")(
    test("Size tracking") {
      checkAll(gen)(list => assert(Stack.fromIterable(list).size)(equalTo(list.length)))
    },
    test("From/to list identity") {
      checkAll(gen)(list => assert(Stack.fromIterable(list).toList)(equalTo(list)))
    },
    test("Push/pop example") {
      checkAll(gen) { list =>
        val stack = Stack[Boolean]()

        list.foreach(stack.push)

        list.reverse.foldLeft(assert(true)(equalTo(true))) { case (result, flag) =>
          result && assert(stack.popOrElse(!flag))(equalTo(flag))
        }
      }
    },
    test("Peek/pop identity") {
      checkAll(gen) { list =>
        val stack = Stack[Boolean]()

        list.foreach(stack.push)

        list.reverse.foldLeft(assert(true)(equalTo(true))) { case (result, flag) =>
          val peeked = stack.peekOrElse(!flag)
          val popped = stack.popOrElse(!flag)

          result && assert(peeked)(equalTo(popped))
        }
      }
    }
  )

  sealed trait Boolean {
    def unary_! : Boolean = this match {
      case True  => False
      case False => True
    }
  }
  case object True  extends Boolean
  case object False extends Boolean

  val gen: Gen[Has[Random] with Has[Sized], List[Boolean]] =
    Gen.large(n => Gen.listOfN(n)(Gen.elements(True, False)))
}
