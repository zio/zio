package zio.test.refined.types

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.time.{Day, Hour, Millis, Minute, Month, Second}
import zio.test.Gen
import zio.test.magnolia.DeriveGen

/**
 * Brings the instances of `TimeInstances` into scope: `import
 * zio.test.refined.types.time._`.
 */
object time extends TimeInstances

/**
 * Instances for the `Month`, `Day`, `Hour`, `Minute`, `Second` and `Millis`
 * aliases of `eu.timepit.refined.types.time`.
 */
trait TimeInstances {

  /**
   * A generator of `Month` built with `Gen.int(1, 12)`: from 1 to 12, both
   * inclusive.
   */
  val monthGen: Gen[Any, Month] = Gen.int(1, 12).map(Refined.unsafeApply)

  /**
   * A generator of `Day` built with `Gen.int(1, 31)`: from 1 to 31, both
   * inclusive.
   */
  val dayGen: Gen[Any, Day] = Gen.int(1, 31).map(Refined.unsafeApply)

  /**
   * A generator of `Hour` built with `Gen.int(1, 23)`: from 1 to 23, both
   * inclusive. `Hour` also admits 0, which this generator does not produce.
   */
  val hourGen: Gen[Any, Hour] = Gen.int(1, 23).map(Refined.unsafeApply)

  /**
   * A generator of `Minute` built with `Gen.int(1, 59)`: from 1 to 59, both
   * inclusive. `Minute` also admits 0, which this generator does not produce.
   */
  val minuteGen: Gen[Any, Minute] = Gen.int(1, 59).map(Refined.unsafeApply)

  /**
   * A generator of `Second` built with `Gen.int(1, 59)`: from 1 to 59, both
   * inclusive. `Second` also admits 0, which this generator does not produce.
   */
  val secondGen: Gen[Any, Second] = Gen.int(1, 59).map(Refined.unsafeApply)

  /**
   * A generator of `Millis` built with `Gen.int(1, 999)`: from 1 to 999, both
   * inclusive. `Millis` also admits 0, which this generator does not produce.
   */
  val millsGen: Gen[Any, Millis] = Gen.int(1, 999).map(Refined.unsafeApply)

  /**
   * The implicit `DeriveGen` for `Month`; it wraps `monthGen`.
   */
  implicit def monthDeriveGen: DeriveGen[Month] = DeriveGen.instance(monthGen)

  /**
   * The implicit `DeriveGen` for `Day`; it wraps `dayGen`.
   */
  implicit def dayDeriveGen: DeriveGen[Day] = DeriveGen.instance(dayGen)

  /**
   * The implicit `DeriveGen` for `Hour`; it wraps `hourGen`.
   */
  implicit def hourDeriveGen: DeriveGen[Hour] = DeriveGen.instance(hourGen)

  /**
   * The implicit `DeriveGen` for `Minute`; it wraps `minuteGen`.
   */
  implicit def minuteDeriveGen: DeriveGen[Minute] = DeriveGen.instance(minuteGen)

  /**
   * The implicit `DeriveGen` for `Second`; it wraps `secondGen`.
   */
  implicit def secondDeriveGen: DeriveGen[Second] = DeriveGen.instance(secondGen)

  /**
   * The implicit `DeriveGen` for `Millis`; it wraps `millsGen`.
   */
  implicit def millsDeriveGen: DeriveGen[Millis] = DeriveGen.instance(millsGen)
}
