package zio

import java.io._

import zio.SerializableSpec.TestException
import zio.internal.stacktracer.ZTraceElement
import zio.random.Random

class SerializableSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def serializeAndBack[T](a: T): IO[_, T] = {
    import SerializableSpec._

    for {
      obj       <- IO.effectTotal(serializeToBytes(a))
      returnObj <- IO.effectTotal(getObjFromBytes[T](obj))
    } yield returnObj
  }

  def is =
    "SerializableSpec".title ^ s2"""
    Test all classes are Serializable
    verify that
      Semaphore is serializable $e1
      Clock is serializable $e2
      Queue is serializable $e3
      Ref is serializable $e4
      IO is serializable $e5
      FunctionIO is serializable $e6
      FiberStatus is serializable $e7
      Duration is serializable $e8
      Cause is serializable 
        $cause1 
        $cause2 
        $cause3
        $cause4
        $cause5
        $cause6
      Exit is serializable
        $exit1
        $exit2
        $exit3
      FiberFailure is serializable $fiberFailure
      InterruptStatus is serializable
        $interruptStatus1
        $interruptStatus2
      Promise is serializable $promise
      ZSchedule is serializable $zschedule
      Chunk is serializable
       $chunkArr 
       $chunkConcat
       $chunkSingle
       $chunkSlice
       $chunkEmpty
       $chunkVector
      FiberRef is serializable $fiberRef
      ZManaged is serializable $zmanaged
      SuperviseStatus is serializable
        $superviseStatus1
        $superviseStatus2
      ZTrace is serializable $ztrace
      TracingStatus is serializable
       $tracingStatusTraced
       $tracingStatusUntraced
      Random is serializable $random
      System is serializable $system
    """

  def e1 = {
    val n = 20L
    unsafeRun(
      for {
        semaphore   <- Semaphore.make(n)
        count       <- semaphore.available
        returnSem   <- serializeAndBack(semaphore)
        returnCount <- returnSem.available
      } yield returnCount must_=== count
    )
  }

  def e2 =
    unsafeRun(
      for {
        time1 <- clock.nanoTime
        time2 <- serializeAndBack(clock.nanoTime).flatten
      } yield (time1 <= time2) must beTrue
    )

  def e3 = unsafeRun(
    for {
      queue       <- Queue.bounded[Int](100)
      _           <- queue.offer(10)
      returnQueue <- serializeAndBack(queue)
      v1          <- returnQueue.take
      _           <- returnQueue.offer(20)
      v2          <- returnQueue.take
    } yield (v1 must_=== 10) and (v2 must_=== 20)
  )

  def e4 = {
    val current = "This is some value"
    unsafeRun(
      for {
        ref       <- Ref.make(current)
        returnRef <- serializeAndBack(ref)
        value     <- returnRef.get
      } yield value must_=== current
    )
  }

  def e5 = {
    val list = List("1", "2", "3")
    val io   = IO.succeed(list)
    unsafeRun(
      for {
        returnIO <- serializeAndBack(io)
        result   <- returnIO
      } yield result must_=== list
    )
  }

  def e6 = {
    import FunctionIO._
    val v = fromFunction[Int, Int](_ + 1)
    unsafeRun(
      for {
        returnKleisli <- serializeAndBack(v)
        computeV      <- returnKleisli.run(9)
      } yield computeV must_=== 10
    )
  }

  def e7 = {
    val list = List("1", "2", "3")
    val io   = IO.succeed(list)
    val exit = unsafeRun(
      for {
        fiber          <- io.fork
        status         <- fiber.await
        returnedStatus <- serializeAndBack(status)
      } yield returnedStatus
    )
    val result = exit match {
      case Exit.Success(value) => value
      case _                   => List.empty
    }
    result must_=== list
  }

  def e8 = {
    import zio.duration.Duration
    val duration = Duration.fromNanos(1)
    val returnDuration = unsafeRun(
      for {
        returnDuration <- serializeAndBack(duration)
      } yield returnDuration
    )

    returnDuration must_=== duration
  }

  def cause1 = {
    val cause = Cause.die(TestException("test"))
    cause must_=== SerializableSpec.serializeAndDeserialize(cause)
  }

  def cause2 = {
    val cause = Cause.fail("test")
    cause must_=== SerializableSpec.serializeAndDeserialize(cause)
  }

  def cause3 = {
    val cause = Cause.traced(Cause.fail("test"), ZTrace(0L, List.empty, List.empty, None))
    cause must_=== SerializableSpec.serializeAndDeserialize(cause)
  }

  def cause4 = {
    val cause = Cause.interrupt
    cause must_=== SerializableSpec.serializeAndDeserialize(cause)
  }

  def cause5 = {
    val cause = Cause.fail("test") && Cause.interrupt
    cause must_=== SerializableSpec.serializeAndDeserialize(cause)
  }

  def cause6 = {
    val cause = Cause.fail("test") ++ Cause.interrupt
    cause must_=== SerializableSpec.serializeAndDeserialize(cause)
  }

  def exit1 = {
    val exit = Exit.succeed("test")
    exit must_=== SerializableSpec.serializeAndDeserialize(exit)
  }

  def exit2 = {
    val exit = Exit.fail("test")
    exit must_=== SerializableSpec.serializeAndDeserialize(exit)
  }

  def exit3 = {
    val exit = Exit.die(TestException("test"))
    exit must_=== SerializableSpec.serializeAndDeserialize(exit)
  }

  def fiberFailure = {
    val failure = FiberFailure(Cause.interrupt)
    failure must_=== SerializableSpec.serializeAndDeserialize(failure)
  }

  def interruptStatus1 = {
    val interruptStatus = InterruptStatus.interruptible
    interruptStatus must_=== SerializableSpec.serializeAndDeserialize(interruptStatus)
  }

  def interruptStatus2 = {
    val interruptStatus = InterruptStatus.uninterruptible
    interruptStatus must_=== SerializableSpec.serializeAndDeserialize(interruptStatus)
  }

  def promise = {
    val test = for {
      promise           <- Promise.make[Nothing, String]
      _                 <- promise.succeed("test")
      value             <- promise.await
      deserialized      <- serializeAndBack(promise)
      deserializedValue <- deserialized.await
    } yield value must_=== deserializedValue

    unsafeRun(test)
  }

  def zschedule = {
    val schedule = Schedule.recurs(5)
    val test = for {
      out1 <- schedule.run(List(1, 2, 3, 4, 5))
      out2 <- SerializableSpec.serializeAndDeserialize(schedule).run(List(1, 2, 3, 4, 5))
    } yield out1 must_=== out2

    unsafeRun(test)
  }

  def chunkSingle = {
    val chunk = Chunk.single(1)
    val res = for {
      chunk <- serializeAndBack(chunk)
    } yield chunk

    unsafeRun(res) must_=== chunk
  }

  def chunkArr = {
    val chunk = Chunk.fromArray(Array(1, 2, 3))
    val res = for {
      chunk <- serializeAndBack(chunk)
    } yield chunk

    unsafeRun(res) must_=== chunk
  }

  def chunkConcat = {
    val chunk = Chunk.single(1) ++ Chunk.single(2)
    val res = for {
      chunk <- serializeAndBack(chunk)
    } yield chunk

    unsafeRun(res) must_=== chunk
  }

  def chunkSlice = {
    val chunk = Chunk.fromArray((1 to 100).toArray).take(10)
    val res = for {
      chunk <- serializeAndBack(chunk)
    } yield chunk

    unsafeRun(res) must_=== chunk
  }

  def chunkEmpty = {
    val chunk = Chunk.empty
    val res = for {
      chunk <- serializeAndBack(chunk)
    } yield chunk

    unsafeRun(res) must_=== chunk
  }

  def chunkVector = {
    val chunk = Chunk.fromIterable(Vector(1, 2, 3))
    val res = for {
      chunk <- serializeAndBack(chunk)
    } yield chunk

    unsafeRun(res) must_=== chunk
  }

  def fiberRef = {
    val value = 10
    val res = for {
      init   <- FiberRef.make(value)
      ref    <- serializeAndBack(init)
      result <- ref.get
    } yield result

    unsafeRun(res) must_=== value
  }

  def zmanaged = {
    val res = for {
      managed <- serializeAndBack(ZManaged.make(UIO.unit)(_ => UIO.unit))
      result  <- managed.use(_ => UIO.unit)
    } yield result

    unsafeRun(res) must_=== (())
  }

  def superviseStatus1 = {
    val supervised = SuperviseStatus.supervised
    supervised must_=== SerializableSpec.serializeAndDeserialize(supervised)
  }

  def superviseStatus2 = {
    val unsupervised = SuperviseStatus.unsupervised
    unsupervised must_=== SerializableSpec.serializeAndDeserialize(unsupervised)
  }

  def ztrace = {
    val trace = ZTrace(
      0L,
      List(ZTraceElement.NoLocation("test")),
      List(ZTraceElement.SourceLocation("file.scala", "Class", "method", 123)),
      None
    )

    trace must_=== SerializableSpec.serializeAndDeserialize(trace)
  }

  def tracingStatusTraced = {
    val traced = TracingStatus.Traced
    val res = for {
      result <- serializeAndBack(traced)
    } yield result

    unsafeRun(res) must_=== traced
  }

  def tracingStatusUntraced = {
    val untraced = TracingStatus.Untraced
    val res = for {
      result <- serializeAndBack(untraced)
    } yield result

    unsafeRun(res) must_=== untraced
  }

  def random = {
    val rnd = Random.Live
    rnd must_=== SerializableSpec.serializeAndDeserialize(rnd)
  }

  def system = {
    val res = for {
      system <- serializeAndBack(zio.system.System.Live)
      result <- system.system.property("notpresent")
    } yield result

    unsafeRun(res) must_=== Option.empty
  }

}

object SerializableSpec {

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

  private case class TestException(msg: String) extends RuntimeException(msg)

}
