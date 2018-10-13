package scalaz.zio

import java.io._

class SerializableSpec extends AbstractRTSSpec {

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

  def serializeAndBack[T](a: T): IO[_, T] =
    for {
      obj       <- IO.sync(serializeToBytes(a))
      returnObj <- IO.sync(getObjFromBytes[T](obj))
    } yield returnObj

  def is =
    "SerializableSpec".title ^ s2"""
    Test all classes are Serializable
    verify that
      Semaphore is serializable $e1
      Clock is serializable $e2
      Queue is serializable $e3
      Ref is serializable $e4
      IO is serializable $e5
      KleisliIO is serializable $e6
    """

  def e1 = {
    val n = 20L
    unsafeRun(
      for {
        semaphore   <- Semaphore(n)
        count       <- semaphore.count
        returnSem   <- serializeAndBack(semaphore)
        returnCount <- returnSem.count
      } yield returnCount must_=== count
    )
  }

  def e2 = {
    val live = Clock.Live
    unsafeRun(
      for {
        time1       <- live.nanoTime
        returnClock <- serializeAndBack(live)
        time2       <- returnClock.nanoTime
      } yield (time1 < time2) must beTrue
    )
  }

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
        ref       <- Ref(current)
        returnRef <- serializeAndBack(ref)
        value     <- returnRef.get
      } yield value must_=== current
    )
  }

  def e5 = {
    val list = List("1", "2", "3")
    val io   = IO.point(list)
    unsafeRun(
      for {
        returnIO <- serializeAndBack(io)
        result   <- returnIO
      } yield result must_=== list
    )
  }

  def e6 = {
    import KleisliIO._
    val v = lift[Int, Int](_ + 1)
    unsafeRun(
      for {
        returnKleisli <- serializeAndBack(v)
        computeV      <- returnKleisli.run(9)
      } yield computeV must_=== 10
    )
  }
}
