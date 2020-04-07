package zio

package object optics {

  /**
   * A `ZOptic` is able to get and set a piece of a whole, possibly failing. In
   * the most general possible case, the set / get types are distinct, and
   * setting may fail with a different error than getting.
   *
   * See more specific type aliases for concrete examples of what optics can be
   * used to do.
   */
  type ZOptic[-GetWhole, -SetWholeBefore, -SetPiece, +GetError, +SetError, +GetPiece, +SetWholeAfter] =
    (GetWhole => Either[GetError, GetPiece], SetPiece => SetWholeBefore => Either[SetError, SetWholeAfter])

  type ZLens[+EA, +EB, -S, +T, +A, -B]      = ZOptic[S, S, B, EA, EB, A, T]
  type ZPrism[+EA, +EB, -S, +T, +A, -B]     = ZOptic[S, Any, B, EA, EB, A, T]
  type ZTraversal[+EA, +EB, -S, +T, +A, -B] = ZOptic[S, S, List[B], EA, EB, List[A], T]

  type Lens[S, A]      = ZLens[Nothing, Nothing, S, S, A, A]
  type Optional[S, A]  = ZLens[Unit, Nothing, S, S, A, A]
  type Prism[S, A]     = ZPrism[Unit, Nothing, S, S, A, A]
  type Traversal[S, A] = ZTraversal[Nothing, Unit, S, S, A, A]

  object Lens {
    def apply[S, A](get: S => A, set: A => S => S): Lens[S, A] =
      (s => Right(get(s)), a => s => Right(set(a)(s)))
  }

  object Optional {
    def apply[S, A](get: S => Option[A], set: A => S => S): Optional[S, A] =
      (s => get(s).fold[Either[Unit, A]](Left(()))(Right(_)), a => s => Right(set(a)(s)))
  }

  object Prism {
    def apply[S, A](get: S => Option[A], set: A => S): Prism[S, A] =
      (s => get(s).fold[Either[Unit, A]](Left(()))(Right(_)), a => _ => Right(set(a)))
  }

  object Traversal {
    def apply[S, A](get: S => List[A], set: List[A] => S => Option[S]): Traversal[S, A] =
      (s => Right(get(s)), a => s => set(a)(s).fold[Either[Unit, S]](Left(()))(Right(_)))
  }
}
