package zio.stm

import java.util.concurrent.ThreadLocalRandom

private object ZSTMLockSupport {

  final class Lock
  object Lock {
    def apply(fair: Boolean = false): Lock = new Lock
  }

  @inline def lock[A](refs: collection.Set[TRef[?]])(f: => A)(implicit rnd: ThreadLocalRandom): Boolean = {
    val _ = f
    true
  }

  @inline def tryLock[A](refs: collection.Set[TRef[?]])(f: => A): Unit = {
    val _ = f
    ()
  }

  @inline def tryLock[A](lock: Lock)(f: => A): Boolean = {
    val _ = f
    true
  }
}
