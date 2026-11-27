package zio.test

import zio.{Random, Unsafe, ZIO}

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Identifies one group of specs while a spec runs, so that execution events
 * can name the group they belong to.
 *
 * @param id
 *   the number of the group. Each group gets a new number, and the tests in a
 *   group share their group's number.
 */
case class SuiteId(id: Int)

/**
 * Constructors of [[SuiteId]].
 */
object SuiteId {
  /**
   * The id with number 0, which stands for the top level of a run,
   * outside every group.
   */
  val global: SuiteId = SuiteId(0)

  private val counter = new AtomicInteger(1)

  /**
   * Returns a new `SuiteId` whose number comes from a global counter that
   * starts at 1 and grows by one on each call. Despite the name, the numbers
   * are not random.
   */
  val newRandom: ZIO[Any, Nothing, SuiteId] =
    ZIO.succeed(newRandomUnsafe(Unsafe))

  private[test] def newRandomUnsafe(implicit unsafe: Unsafe): SuiteId =
    SuiteId(counter.getAndIncrement())
}
