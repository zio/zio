package zio.internal

import zio.Random
import zio.test.Assertion.equalTo
import zio.test.{Gen, ZSpec, assert, checkAll}
import zio.{Has, ZIOBaseSpec}

import scala.util.Random.nextInt

object StackBoolSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("StackBoolSpec")(
    testM("Size tracking") {
      checkAll(gen)(list => assert(StackBool.fromIterable(list).size.toInt)(equalTo(list.length)))
    },
    testM("From/to list identity") {
      checkAll(gen)(list => assert(StackBool.fromIterable(list).toList)(equalTo(list)))
    },
    testM("Push/pop example") {
      checkAll(gen) { list =>
        val stack = StackBool()

        list.foreach(stack.push)

        list.reverse.foldLeft(assert(true)(equalTo(true))) { case (result, flag) =>
          result && assert(stack.popOrElse(!flag))(equalTo(flag))
        }
      }
    },
    testM("Peek/pop identity") {
      checkAll(gen) { list =>
        val stack = StackBool()

        list.foreach(stack.push)

        list.reverse.foldLeft(assert(true)(equalTo(true))) { case (result, flag) =>
          val peeked = stack.peekOrElse(!flag)
          val popped = stack.popOrElse(!flag)

          result && assert(peeked)(equalTo(popped))
        }
      }
    },
    test("GetOrElse index out of bounds") {
      val stack  = StackBool()
      val result = stack.getOrElse(100, true)
      assert(result)(equalTo(true))
    }
  )

  val gen: Gen[Has[Random], List[Boolean]] = Gen.listOfN(nextInt(200))(Gen.boolean)
}
