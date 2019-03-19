package scalaz.zio.stm

import java.util.concurrent.atomic.AtomicReference

import scalaz.zio.UIO
import scalaz.zio.stm.STM.internal._

/**
 * A variable that can be modified as part of a transactional effect.
 */
class TRef[A] private (
  val id: Long,
  @volatile var versioned: Versioned[A],
  val todo: AtomicReference[Map[Long, Todo]]
) {
  self =>

  final val debug: UIO[Unit] =
    UIO(println(toString))

  /**
   * Retrieves the value of the `TRef`.
   */
  final val get: STM[Nothing, A] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      TRez.Succeed(entry.unsafeGet[A])
    })

  /**
   * Sets the value of the `tvar`.
   */
  final def set(newValue: A): STM[Nothing, Unit] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      entry unsafeSet newValue

      succeedUnit
    })

  override final def toString =
    s"TRef(id = $id, versioned.value = ${versioned.value}, todo = ${todo.get})"

  /**
   * Updates the value of the variable.
   */
  final def update(f: A => A): STM[Nothing, A] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      val newValue = f(entry.unsafeGet[A])

      entry unsafeSet newValue

      TRez.Succeed(newValue)
    })

  /**
   * Updates the value of the variable, returning a function of the specified
   * value.
   */
  final def modify[B](f: A => (B, A)): STM[Nothing, B] =
    new STM(journal => {
      val entry = getOrMakeEntry(journal)

      val (retValue, newValue) = f(entry.unsafeGet[A])

      entry unsafeSet newValue

      TRez.Succeed(retValue)
    })

  private final def getOrMakeEntry(journal: Journal): Entry =
    if (journal contains id) journal(id)
    else {
      val expected = versioned
      val entry    = Entry(self, expected.value, expected)
      journal update (id, entry)
      entry
    }
}

object TRef {

  /**
   * Makes a new `TRef` that is initialized to the specified value.
   */
  final def make[A](a: => A): STM[Nothing, TRef[A]] =
    new STM(journal => {
      val id = makeTRefId()

      val value     = a
      val versioned = new Versioned(value)

      val todo = new AtomicReference[Map[Long, Todo]](Map())

      val tvar = new TRef(id, versioned, todo)

      journal update (id, Entry(tvar, value, versioned))

      TRez.Succeed(tvar)
    })

  /**
   * A convenience method that makes a `TRef` and immediately commits the
   * transaction to extract the value out.
   */
  final def makeCommit[A](a: => A): UIO[TRef[A]] =
    STM.atomically(make(a))
}
