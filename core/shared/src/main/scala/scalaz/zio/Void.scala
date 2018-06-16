package scalaz.zio

/**
 * An uninhabited type. Because there are no values of this type, it represents a guarantee, at compile-time, that values of type `Void` may not exist.
 * The `absurd` method can be used to eliminate terms of a sum type that have type `Void`, e.g.
 * `Either[Void, A]` may be simplified via `absurd` to `A`.
 * This type is similar to `Nothing` but does not suffer from weird edge cases in Scalac (e.g https://github.com/scala/bug/issues/9453).
 */
abstract final class Void {
  def absurd[A]: A
}
