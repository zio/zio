package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.implicitNotFound

/**
 * Evidence that the success type `A` of an effect is a subtype of `B`, which
 * also converts an `A` to a `B`. Operators that need a particular success type,
 * such as `ZIO#absolve` (an `Either`) or `ZIO#head` (a `Seq`), ask for it, and
 * the compiler names both types when it is missing.
 */
@implicitNotFound("\nThis operator requires that the output type be a subtype of ${B}\nBut the actual type was ${A}.")
sealed abstract class IsSubtypeOfOutput[-A, +B] extends (A => B) with Serializable

/** Provides [[IsSubtypeOfOutput]] instances. */
object IsSubtypeOfOutput {
  private val instance: IsSubtypeOfOutput[Any, Any] = new IsSubtypeOfOutput[Any, Any] { def apply(a: Any): Any = a }

  /** Provides the evidence when `A <:< B` holds. */
  implicit def impl[A, B](implicit subtype: A <:< B): IsSubtypeOfOutput[A, B] =
    instance.asInstanceOf[IsSubtypeOfOutput[A, B]]

  /** Provides the evidence for `Nothing`, which is a subtype of every `B`. */
  implicit def implNothing[B]: IsSubtypeOfOutput[Nothing, B] =
    instance.asInstanceOf[IsSubtypeOfOutput[Nothing, B]]
}

/**
 * Evidence that the error type `A` of an effect is a subtype of `B`, which also
 * converts an `A` to a `B`. Operators that need a particular error type, such
 * as `ZIO#orDie` or `Cause#squash` (a `Throwable`), ask for it, and the
 * compiler names both types when it is missing.
 */
@implicitNotFound("\nThis operator requires that the error type be a subtype of ${B}\nBut the actual type was ${A}.")
sealed abstract class IsSubtypeOfError[-A, +B] extends (A => B) with Serializable

/** Provides [[IsSubtypeOfError]] instances. */
object IsSubtypeOfError {
  private val instance: IsSubtypeOfError[Any, Any] = new IsSubtypeOfError[Any, Any] { def apply(a: Any): Any = a }

  /** Provides the evidence when `A <:< B` holds. */
  implicit def impl[A, B](implicit subtype: A <:< B): IsSubtypeOfError[A, B] =
    instance.asInstanceOf[IsSubtypeOfError[A, B]]

  /** Provides the evidence for `Nothing`, which is a subtype of every `B`. */
  implicit def implNothing[B]: IsSubtypeOfError[Nothing, B] =
    instance.asInstanceOf[IsSubtypeOfError[Nothing, B]]
}
