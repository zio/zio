package zio.test.magnolia

import magnolia1._
import zio.test.diff.{Diff, DiffInstances, DiffResult}

import scala.quoted._

/**
 * Derives a `zio.test.diff.Diff` for case classes and sealed traits, so that a failing `assertTrue(x == y)` on them shows which fields differ. Bring the derivation into scope with `import zio.test.magnolia.DeriveDiff._`.
 *
 * A derived `Diff` for a case class compares each field with that field's `Diff` and returns a `DiffResult.Nested` with one entry per field. For a sealed trait, two values of the same subtype are compared with that subtype's `Diff`, and values of different subtypes give `DiffResult.Different(x, y)`.
 *
 * {{{
 * import zio.test.diff.Diff
 * import zio.test.magnolia.DeriveDiff._
 *
 * final case class Address(city: String, zip: Int)
 * final case class Customer(name: String, address: Address)
 *
 * val diffCustomer: Diff[Customer] = Diff[Customer]
 *
 * val result = diffCustomer.diff(Customer("Ann", Address("Oslo", 150)), Customer("Ann", Address("Bergen", 150)))
 * // Nested(Customer,List((Some(name),Identical(Ann)), (Some(address),Nested(Address,List((Some(city),Different(Oslo,Bergen,None)), (Some(zip),Identical(150)))))))
 * }}}
 *
 * On Scala 2, nested case classes and sealed traits are derived as well, and a field whose type has no `Diff` and cannot be derived, such as `java.time.Instant`, is compared with `fallback`. Calling `DeriveDiff.gen[A]` without the import also derives nested types on Scala 2. On Scala 3, each field type needs a `Diff` instance or must itself be a case class or sealed trait; for other field types, also import `fallback` with `import zio.test.magnolia.DeriveDiff.given`. In the scope of that import, `fallback` also replaces the instances of the `Diff` companion for types such as `String`.
 *
 * In the scope of `import zio.test.magnolia.DeriveDiff._`, `Diff[List[Int]]` and `Diff[Option[Int]]` are derived as sealed types (from `::` and `Nil`, and from `Some` and `None`) instead of coming from the `Diff` companion. On Scala 3, this also applies to the `List` fields of a derived case class.
 *
 * @see [[https://zio.dev/reference/test/zio-test-diff zio.test.diff.Diff]]
 */
object DeriveDiff extends AutoDerivation[Diff] with LowPri {

  /**
   * Derives the `Diff` of a case class: compares each field with that field's `Diff` and returns a `DiffResult.Nested` with the case class's short name and one entry per field, labelled with the field name. Magnolia calls it during derivation.
   */
  def join[A](caseClass: CaseClass[Diff, A]): Diff[A] =
    (x: A, y: A) => {
      val fields = caseClass.params.map { param =>
        Some(param.label) -> param.typeclass.diff(param.deref(x), param.deref(y))
      }

      DiffResult.Nested(caseClass.typeInfo.short, fields.toList)
    }

  /**
   * Derives the `Diff` of a sealed trait: when both values have the same subtype, compares them with that subtype's `Diff`; otherwise returns `DiffResult.Different(x, y)`. Magnolia calls it during derivation.
   */
  def split[A](ctx: SealedTrait[Diff, A]): Diff[A] = (x: A, y: A) =>
    ctx.choose(x) {
      case sub if sub.cast.isDefinedAt(y) => sub.typeclass.diff(sub.cast(x), sub.cast(y))
      case _                              => DiffResult.Different(x, y)
    }

  /**
   * Derives a `Diff[A]` for a case class or sealed trait with Magnolia; on Scala 3 it requires the type's `Mirror`. It is implicit, so `import zio.test.magnolia.DeriveDiff._` makes the compiler derive a `Diff` wherever one is needed.
   */
  implicit inline def gen[A: deriving.Mirror.Of]: Diff[A] =
    autoDerived[A]
}

/**
 * Provides `fallback`, the `Diff` for types that have no instance and cannot be derived.
 */
trait LowPri {
  /**
   * A `Diff` that compares two values with `==`: it returns `DiffResult.Identical(x)` when they are equal and `DiffResult.Different(x, y)` otherwise. Its `isLowPriority` is `true`, so a failing `assertTrue(x == y)` whose `Diff` is this one shows the two values instead of a diff. On Scala 2, Magnolia uses it for field types that it cannot derive. On Scala 3 it is a `given`, imported with `import zio.test.magnolia.DeriveDiff.given`.
   */
  given fallback[A]: Diff[A] = new Diff[A] {
    override def diff(x: A, y: A): DiffResult =
      if (x == y) DiffResult.Identical(x)
      else DiffResult.Different(x, y)

    override def isLowPriority: Boolean = true
  }
}
