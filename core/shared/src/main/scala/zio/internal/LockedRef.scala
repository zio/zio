package zio.internal

import java.util.concurrent.atomic.AtomicBoolean

class LockedRef[A](var ref: A) {
  var locked = new AtomicBoolean(false)

  def get: A = ref

  def set(a: A): A = {
    ref = a
    ref
  }

  def synchronized(f: => Unit): Unit = {
    lock()
    try f
    finally unlock()
  }

  def lock(): Unit = {
    var loop = true
    while (loop) {
      if (locked.compareAndSet(false, true)) {
        loop = false
      }
    }
  }

  def unlock(): Unit = {
    var loop = true
    while (loop) {
      if (locked.compareAndSet(true, false)) {
        loop = false
      }
    }
  }

}
