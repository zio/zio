package zio.internal

import java.util.concurrent.atomic.AtomicBoolean

class LockedRef[A](var ref: A) {
  var locked = new AtomicBoolean(false)

  def get: A = ref

  def set(a: A): A = {
    this.ref = a
    this.ref
  }

  def exclusiveSet(a: A): Unit = {
    lock()
    this.ref = a
    unlock()
  }

  def lock(): Unit = {
    var loop = true
    while (loop) {
      if (!locked.compareAndSet(false, true)) {
        loop = false
      }
    }
  }

  def unlock(): Unit = {
    var loop = true
    while (loop) {
      if (!locked.compareAndSet(true, false)) {
        loop = false
      }
    }
  }

}
