package zio.internal

class LockedRef[A](var ref: A) {
  var locked = false

  def get: A = ref
  def set(a: A): A = {
    this.ref = a
    this.ref
  }

  def exclusiveSet(a: A): A = {
    lock()
    set(a)
    unlock()
    a
  }

  def lock(): Unit = {
    var loop = true
    while (loop) {
      if (!locked) {
        locked = true
        loop = false
      }
    }
  }

  def unlock(): Unit =
    locked = false

}
