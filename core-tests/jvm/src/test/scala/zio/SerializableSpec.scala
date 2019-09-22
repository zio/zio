package zio

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }

import zio.test.Assertion._
import SerializableSpecHelpers._
import zio.test._

object SerializableSpec
    extends ZIOBaseSpec(
      suite("SerializableSpec")(
        testM("Semaphore is serializable") {
          val n = 20L
          for {
            semaphore   <- Semaphore.make(n)
            count       <- semaphore.available
            returnSem   <- serializeAndBack(semaphore)
            returnCount <- returnSem.available
          } yield assert(returnCount, equalTo(count))
        },
        testM("Clock is serializable") {
          for {
            time1 <- clock.nanoTime
            time2 <- serializeAndBack(clock.nanoTime).flatten
          } yield assert(time1, isLessThanEqualTo(time2))
        },
        testM("Queue is serializable") {
          for {
            queue       <- Queue.bounded[Int](100)
            _           <- queue.offer(10)
            returnQueue <- serializeAndBack(queue)
            v1          <- returnQueue.take
            _           <- returnQueue.offer(20)
            v2          <- returnQueue.take
          } yield assert(v1, equalTo(10)) &&
            assert(v2, equalTo(20))
        },
        testM("Ref is serializable") {
          val current = "This is some value"
          for {
            ref       <- Ref.make(current)
            returnRef <- serializeAndBack(ref)
            value     <- returnRef.get
          } yield assert(value, equalTo(current))
        },
        testM("IO is serializable") {
          val list = List("1", "2", "3")
          val io   = IO.succeed(list)
          for {
            returnIO <- serializeAndBack(io)
            result   <- returnIO
          } yield assert(result, equalTo(list))
        },
        testM("FunctionIO is serializable") {
          import FunctionIO._
          val v = fromFunction[Int, Int](_ + 1)
          for {
            returnKleisli <- serializeAndBack(v)
            computeV      <- returnKleisli.run(9)
          } yield assert(computeV, equalTo(10))
        },
        testM("FiberStatus is serializable") {
          val list = List("1", "2", "3")
          val io   = IO.succeed(list)
          for {
            fiber          <- io.fork
            status         <- fiber.await
            returnedStatus <- serializeAndBack(status)
          } yield {
            assert(returnedStatus.getOrElse(_ => List.empty), equalTo(list))
          }
        },
        testM("Duration is serializable") {
          import zio.duration.Duration
          val duration = Duration.fromNanos(1)
          for {
            returnDuration <- serializeAndBack(duration)
          } yield assert(returnDuration, equalTo(duration))
        }
      )
    )

object SerializableSpecHelpers {
  def serializeAndBack[T](a: T): IO[_, T] =
    for {
      obj       <- IO.effectTotal(serializeToBytes(a))
      returnObj <- IO.effectTotal(getObjFromBytes[T](obj))
    } yield returnObj

  def serializeToBytes[T](a: T): Array[Byte] = {
    val bf  = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bf)
    oos.writeObject(a)
    oos.close()
    bf.toByteArray
  }

  def getObjFromBytes[T](bytes: Array[Byte]): T = {
    val ios = new ObjectInputStream(new ByteArrayInputStream(bytes))
    ios.readObject().asInstanceOf[T]
  }

  def serializeAndDeserialize[T](a: T): T = getObjFromBytes(serializeToBytes(a))
}
