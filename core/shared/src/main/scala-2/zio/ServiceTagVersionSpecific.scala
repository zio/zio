package zio

/**
 * Provides the implicit `Tag` materializer of the `Tag` object. Scala 3 adds a
 * `derived` method.
 */
private[zio] trait TagVersionSpecific {

  /**
   * Materializes the `Tag[A]` at compile time. When `A` is an abstract type
   * parameter, it uses the `Tag` found in implicit scope, and it does not
   * compile when there is none. Tags for intersection types are rejected: on
   * Scala 2 for `A with B` itself, and on Scala 3 for an intersection in a
   * covariant position (as in `List[A & B]`) and a union in a contravariant
   * position inside its type arguments.
   */
  implicit def materialize[A]: Tag[A] =
    macro zio.internal.macros.InternalMacros.materializeTag[A]
}
