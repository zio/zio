package scalaz.zio.internal

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import org.specs2.Specification

/*
 * This spec is just a sanity check and tests RingBuffer correctness
 * in a single-threaded case.
 */
class RingBufferSpec extends Specification {
  def is =
    "RingBufferSpec".title ^ s2"""
    Make a bounded MutableConcurrentQueue of size 1
     returns a queue of size 2 which is the next power of 2. $e1
     `offer` of 2 items succeeds, further offers fail. $e2
     `poll` of 2 items from full queue succeeds, further `poll`s return default value. $e3
     can be serialized. $e4
    """

  def e1 = {
    val q = MutableConcurrentQueue.bounded(1)
    q.capacity must_=== 2
  }

  def e2 = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    (q.offer(1) must beTrue)
      .and(q.size must_=== 1)
      .and(q.offer(2) must beTrue)
      .and(q.size must_=== 2)
      .and(q.offer(3) must beFalse)
      .and(q.isFull must beTrue)
  }

  def e3 = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    q.offer(1)
    q.offer(2)
    (q.poll(-1) must_=== 1)
      .and(q.poll(-1) must_=== 2)
      .and(q.poll(-1) must_=== -1)
      .and(q.isEmpty must beTrue)
  }

  def e4 = {
    val q = MutableConcurrentQueue.bounded[Int](2)
    q.offer(1)
    val returnQ = RingBufferSpec.serializeAndDeserialize(q)
    returnQ.offer(2)
    (returnQ.poll(-1) must_=== 1)
      .and(returnQ.poll(-1) must_=== 2)
      .and(returnQ.poll(-1) must_=== -1)
  }
}

object RingBufferSpec {
  def serializeAndDeserialize[T](a: T): T = getObjFromBytes(serializeToBytes(a))

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
}
