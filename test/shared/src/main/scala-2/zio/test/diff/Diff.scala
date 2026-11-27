package zio.test.diff

/**
 * A type class that compares two values of type `A` and describes how they
 * differ as a [[DiffResult]].
 *
 * Smart assertions use it: when `assertTrue(actual == expected)` fails and a
 * `Diff` for the compared type is in implicit scope, the failure message shows
 * the difference between `expected` (the old value) and `actual` (the new
 * value). It does not show one when the instance is low priority (see
 * `isLowPriority`), or when the result is a [[DiffResult.Different]] with no
 * custom rendering, as for two single-line `String`s when either is shorter
 * than 20 characters.
 *
 * The companion object has instances for `String`, `Map`, `Option`, `Set`,
 * `List`, `Vector`, `Seq`, `Chunk`, `NonEmptyChunk`, `Array`, `ArrayBuffer` and
 * `ListBuffer`, and a low-priority instance for `AnyVal` types such as `Int`
 * and `Double`. `DeriveDiff` from the zio-test-magnolia module derives
 * instances for case classes and sealed traits. A new instance can be built
 * from an existing one with `contramap`:
 *
 * {{{
 * final case class Celsius(degrees: Double)
 *
 * implicit val celsiusDiff: Diff[Celsius] = Diff[Double].contramap(_.degrees)
 *
 * val readings = Diff[List[Celsius]].diff(List(Celsius(20.5), Celsius(21.0)), List(Celsius(20.5), Celsius(22.0)))
 * // Nested(List,List((Some(0),Identical(20.5)), (Some(1),Different(21.0,22.0,None))))
 *
 * val changed = readings.hasDiff
 * // true
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/zio-test-diff/ Diff]]
 */
trait Diff[A] { self =>

  /**
   * Compares the old value `x` with the new value `y` and returns how they
   * differ as a [[DiffResult]].
   */
  def diff(x: A, y: A): DiffResult

  /**
   * Returns a `Diff[B]` that maps both values with `f` and compares the results
   * with this instance. The [[DiffResult]] it returns holds the mapped values,
   * not the `B` values, and its `isLowPriority` is `false` even when this
   * instance's is `true`.
   */
  final def contramap[B](f: B => A): Diff[B] =
    (x, y) => self.diff(f(x), f(y))

  /**
   * Returns `true` for a fallback instance that only compares with `==`, such
   * as `Diff.anyDiff` and `Diff.anyValDiff`, and `false` by default. Smart
   * assertions do not show a difference computed by a low-priority instance.
   */
  def isLowPriority: Boolean = false
}

/**
 * Summons [[Diff]] instances, renders differences, and holds the instances of
 * [[DiffInstances]].
 *
 * @see
 *   [[https://zio.dev/reference/test/zio-test-diff/ Diff]]
 */
object Diff extends DiffInstances {

  /**
   * Returns the `Diff[A]` in implicit scope.
   */
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  /**
   * Compares `oldValue` with `newValue` using the implicit `Diff[A]` and
   * renders the resulting [[DiffResult]] as a string.
   */
  def render[A: Diff](oldValue: A, newValue: A): String =
    (oldValue diffed newValue).render

  /**
   * Adds `diffed` to values of type `A` when a `Diff[A]` is in implicit scope.
   */
  implicit final class DiffOps[A](private val self: A)(implicit diff: Diff[A]) {

    /**
     * Compares this value, as the old value, with `that`, as the new value,
     * using the implicit `Diff[A]`.
     */
    def diffed(that: A): DiffResult = diff.diff(self, that)
  }
}
