package zio.test.refined.types

/**
 * Brings the implicit `DeriveGen` instances for the aliases of `eu.timepit.refined.types` into scope: `import zio.test.refined.types.all._`. It covers the `char`, `digit` (hash strings), `net`, `numeric`, `string` and `time` aliases, for example `PosInt`, `LowerCaseChar`, `Month`, `SHA256`, `NonEmptyString` and `PortNumber`. The `*Gen` members return plain `Gen` values, and the implicit members wrap them as `DeriveGen`.
 *
 * The generators build the refined value with `Refined.unsafeApply`, so the predicate is not checked. The documentation of each member gives the range or shape it produces.
 *
 * {{{
 * import eu.timepit.refined.types.time.Month
 * import zio.test._
 * import zio.test.magnolia.DeriveGen
 * import zio.test.refined.types.all._
 *
 * val spec: Spec[Any, Nothing] =
 *   test("a Month is between 1 and 12") {
 *     check(DeriveGen[Month])(m => assertTrue(m.value >= 1 && m.value <= 12))
 *   }
 * }}}
 */
object all extends AllTypesInstances

/**
 * All the instances of the `types` package: `CharInstances`, `DigitInstances`, `NetInstances`, `NumericInstances`, `StringInstance` and `TimeInstances`. The objects `all` of this package and `zio.test.refined.all` extend it.
 */
trait AllTypesInstances
    extends CharInstances
    with DigitInstances
    with NetInstances
    with NumericInstances
    with StringInstance
    with TimeInstances
