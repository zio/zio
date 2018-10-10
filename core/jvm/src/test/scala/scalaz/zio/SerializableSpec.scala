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

  def is =
    "SerializableSpec".title ^ s2"""
    Test all classes are Serializable
    verify that
      Semaphore is serializable $e1
      Clock is serializable $e2
    """

  def e1 = {
    val n = 20L
    unsafeRun(
      for {
        semaphore   <- Semaphore(n)
        count       <- semaphore.count
        bytes       <- IO.sync(serializeToBytes(semaphore))
        returnSem   <- IO.sync(getObjFromBytes[Semaphore](bytes))
        returnCount <- returnSem.count
      } yield returnCount must_=== count
    )
  }

  def e2 = {
    val live = Clock.Live
    unsafeRun(
      for {
        time1       <- live.nanoTime
        bytes       <- IO.sync(serializeToBytes(live))
        returnClock <- IO.sync(getObjFromBytes[Clock](bytes))
        time2       <- returnClock.nanoTime
      } yield (time1 < time2) must beTrue
    )
  }
}
