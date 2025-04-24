package zio

/**
 * A `FunctionConstructor[Input]` knows how to construct a `ZIO` value from a
 * function of type `In`. This allows the type of the `ZIO` value constructed to
 * depend on `In`.
 */
sealed abstract class FunctionConstructor[In] {

  /**
   * The type of the `ZIO` value.
   */
  type Out

  /**
   * Constructs a `ZIO` value from the specified input.
   */
  def apply(in: In)(implicit trace: Trace): Out
}

object FunctionConstructor {

  type WithOut[In, Out0] = FunctionConstructor[In] { type Out = Out0 }

  implicit def function1Constructor[A: Tag, Z]: FunctionConstructor.WithOut[A => Z, ZIO[A, Nothing, Z]] =
    new FunctionConstructor[A => Z] {
      type Out = ZIO[A, Nothing, Z]
      def apply(f: A => Z)(implicit trace: Trace): ZIO[A, Nothing, Z] =
        ZIO.serviceWith[A](f)
    }

  implicit def function2Constructor[A: Tag, B: Tag, Z]
    : FunctionConstructor.WithOut[(A, B) => Z, ZIO[A with B, Nothing, Z]] =
    new FunctionConstructor[(A, B) => Z] {
      type Out = ZIO[A with B, Nothing, Z]
      def apply(f: (A, B) => Z)(implicit trace: Trace): ZIO[A with B, Nothing, Z] =
        ZIO.environmentWith[A with B](env => f(env.get[A], env.get[B]))
    }

  implicit def function3Constructor[A: Tag, B: Tag, C: Tag, Z]
    : FunctionConstructor.WithOut[(A, B, C) => Z, ZIO[A with B with C, Nothing, Z]] =
    new FunctionConstructor[(A, B, C) => Z] {
      type Out = ZIO[A with B with C, Nothing, Z]
      def apply(f: (A, B, C) => Z)(implicit trace: Trace): ZIO[A with B with C, Nothing, Z] =
        ZIO.environmentWith[A with B with C](env => f(env.get[A], env.get[B], env.get[C]))
    }

  implicit def function4Constructor[A: Tag, B: Tag, C: Tag, D: Tag, Z]
    : FunctionConstructor.WithOut[(A, B, C, D) => Z, ZIO[A with B with C with D, Nothing, Z]] =
    new FunctionConstructor[(A, B, C, D) => Z] {
      type Out = ZIO[A with B with C with D, Nothing, Z]
      def apply(f: (A, B, C, D) => Z)(implicit trace: Trace): ZIO[A with B with C with D, Nothing, Z] =
        ZIO.environmentWith[A with B with C with D](env => f(env.get[A], env.get[B], env.get[C], env.get[D]))
    }

  implicit def function5Constructor[A: Tag, B: Tag, C: Tag, D: Tag, F: Tag, Z]
    : FunctionConstructor.WithOut[(A, B, C, D, F) => Z, ZIO[
      A with B with C with D with F,
      Nothing,
      Z
    ]] =
    new FunctionConstructor[(A, B, C, D, F) => Z] {
      type Out = ZIO[A with B with C with D with F, Nothing, Z]
      def apply(
        f: (A, B, C, D, F) => Z
      )(implicit trace: Trace): ZIO[A with B with C with D with F, Nothing, Z] =
        ZIO.environmentWith[A with B with C with D with F](env =>
          f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[F])
        )
    }

  implicit def function6Constructor[A: Tag, B: Tag, C: Tag, D: Tag, F: Tag, G: Tag, Z]
    : FunctionConstructor.WithOut[(A, B, C, D, F, G) => Z, ZIO[
      A with B with C with D with F with G,
      Nothing,
      Z
    ]] =
    new FunctionConstructor[(A, B, C, D, F, G) => Z] {
      type Out = ZIO[A with B with C with D with F with G, Nothing, Z]
      def apply(
        f: (A, B, C, D, F, G) => Z
      )(implicit trace: Trace): ZIO[A with B with C with D with F with G, Nothing, Z] =
        ZIO.environmentWith[A with B with C with D with F with G](env =>
          f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[F], env.get[G])
        )
    }

  implicit def function7Constructor[A: Tag, B: Tag, C: Tag, D: Tag, F: Tag, G: Tag, H: Tag, Z]
    : FunctionConstructor.WithOut[(A, B, C, D, F, G, H) => Z, ZIO[
      A with B with C with D with F with G with H,
      Nothing,
      Z
    ]] =
    new FunctionConstructor[(A, B, C, D, F, G, H) => Z] {
      type Out = ZIO[A with B with C with D with F with G with H, Nothing, Z]
      def apply(
        f: (A, B, C, D, F, G, H) => Z
      )(implicit trace: Trace): ZIO[A with B with C with D with F with G with H, Nothing, Z] =
        ZIO.environmentWith[A with B with C with D with F with G with H](env =>
          f(env.get[A], env.get[B], env.get[C], env.get[D], env.get[F], env.get[G], env.get[H])
        )
    }
}
