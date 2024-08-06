package zio.stm

import zio.stm.ZSTM.internal.{LockTimeoutMaxMicros, LockTimeoutMinMicros}

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ThreadLocalRandom, TimeUnit}

private object ZSTMLockSupport {

  type Lock = ReentrantLock
  object Lock {
    def apply(): Lock = new ReentrantLock(true)
  }

  inline def lock[A](refs: collection.Set[TRef[?]])(inline f: A)(implicit rnd: ThreadLocalRandom): Boolean =
    refs.size match {
      case 0 => f; true
      case 1 => lock1(refs.head)(f)
      case n => lockN(refs, n)(f)
    }

  inline private def lock1[A](ref: TRef[?])(inline f: A): Boolean = {
    ref.lock.lock()
    try { f; true }
    finally ref.lock.unlock()
  }

  inline private def lockN[A](refs: collection.Set[TRef[?]], size: Int)(
    inline f: A
  )(implicit rnd: ThreadLocalRandom): Boolean = {
    val acquired = Array.ofDim[ReentrantLock](size)
    var locked   = true
    val it       = refs.iterator
    var i        = 0
    val timeout  = rnd.nextLong(LockTimeoutMinMicros, LockTimeoutMaxMicros)
    while (i < size && locked) {
      val lock = it.next().lock
      if (lock.tryLock(timeout, TimeUnit.MICROSECONDS)) {
        acquired(i) = lock
        i += 1
      } else locked = false
    }
    try {
      if (locked) f
      locked
    } finally {
      unlock(acquired, i)
    }
  }

  inline def tryLock[A](refs: collection.Set[TRef[?]])(inline f: A): Unit =
    refs.size match {
      case 0 => f
      case 1 => tryLock(refs.head.lock)(f)
      case n => tryLockN(refs, n)(f)
    }

  inline def tryLock[A](lock: Lock)(inline f: A): Boolean =
    if (lock.tryLock()) {
      try { f; true }
      finally lock.unlock()
    } else false

  inline private def tryLockN[A](refs: collection.Set[TRef[?]], size: Int)(inline f: A): Unit = {
    val acquired = Array.ofDim[ReentrantLock](size)
    var locked   = true
    val it       = refs.iterator
    var i        = 0
    while (i < size && locked) {
      val lock = it.next().lock
      if (lock.tryLock()) {
        acquired(i) = lock
        i += 1
      } else locked = false
    }
    try {
      if (locked) f
    } finally unlock(acquired, i)
  }

  private def unlock(locks: Array[ReentrantLock], upTo: Int): Unit = {
    var i = 0
    while (i < upTo) {
      locks(i).unlock()
      i += 1
    }
  }
}
