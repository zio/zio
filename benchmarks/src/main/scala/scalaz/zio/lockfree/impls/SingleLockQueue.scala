package scalaz.zio.lockfree.impls

import java.util.concurrent.locks.ReentrantLock

import scalaz.zio.lockfree.MutableConcurrentQueue

import scala.reflect.ClassTag

class SingleLockQueue[A: ClassTag](override val capacity: Int) extends MutableConcurrentQueue[A] {
  val lock = new ReentrantLock()

  // GuardedBy "lock"
  private val buf: Array[A] = Array.ofDim[A](capacity)

  // GuardedBy "lock"
  var head: Long = 0

  // GuardedBy "lock"
  var tail: Long = 0

  override def offer(a: A): Boolean = {
    lock.lock()

    try {
      if (isFull()) {
        false
      } else {
        tail += 1
        buf((tail % capacity).asInstanceOf[Int]) = a
        true
      }
    } finally {
      lock.unlock()
    }
  }

  override def poll(default: A): A = {
    lock.lock()

    try {
      if (isEmpty()) {
        default
      } else {
        val el = buf((head % capacity).asInstanceOf[Int])
        head += 1
        el
      }
    } finally {
      lock.unlock()
    }
  }

  override def isEmpty(): Boolean = {
    lock.lock()

    try {
      head == tail
    } finally {
      lock.unlock()
    }
  }

  override def isFull(): Boolean = {
    lock.lock()

    try {
      head + capacity - 1 == tail
    } finally {
      lock.unlock()
    }
  }

  override def size(): Int = {
    lock.lock()

    try {
      capacity - (tail - head).toInt
    } finally {
      lock.unlock()
    }
  }

  override def enqueuedCount(): Long = {
    lock.lock()

    try {
      tail
    } finally {
      lock.unlock()
    }
  }

  override def dequeuedCount(): Long = {
    lock.lock()

    try {
      head
    } finally {
      lock.unlock()
    }
  }
}
