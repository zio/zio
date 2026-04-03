package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

trait Zippable[-A, -B] {
  type Out
  def zip(left: A, right: B): Out
  def discardsLeft: Boolean =
    false
  def discardsRight: Boolean =
    false
}

object Zippable extends ZippableLowPriority1 {

  type Out[-A, -B, C] = Zippable[A, B] { type Out = C }

  implicit def ZippableLeftIdentity[A]: Zippable.Out[Unit, A, A] =
    Zippable.zippableLeftIdentity.asInstanceOf[Zippable.Out[Unit, A, A]]

  private val zippableLeftIdentity: Zippable.Out[Unit, Any, Any] =
    new Zippable[Unit, Any] {
      type Out = Any
      override val discardsLeft: Boolean =
        true
      def zip(left: Unit, right: Any): Any =
        right
    }
}

trait ZippableLowPriority1 extends ZippableLowPriority2 {

  implicit def ZippableRightIdentity[A]: Zippable.Out[A, Unit, A] =
    ZippableLowPriority1.zippableRightIdentity.asInstanceOf[Zippable.Out[A, Unit, A]]
}

object ZippableLowPriority1 {
  private val zippableRightIdentity: Zippable.Out[Any, Unit, Any] =
    new Zippable[Any, Unit] {
      type Out = Any
      override val discardsRight: Boolean =
        true
      def zip(left: Any, right: Unit): Any =
        left
    }
}

trait ZippableLowPriority2 extends ZippableLowPriority3 {

  implicit def Zippable3[A, B, Z]: Zippable.Out[(A, B), Z, (A, B, Z)] =
    new Zippable[(A, B), Z] {
      type Out = (A, B, Z)
      def zip(left: (A, B), right: Z): (A, B, Z) =
        (left._1, left._2, right)
    }

  implicit def Zippable4[A, B, C, Z]: Zippable.Out[(A, B, C), Z, (A, B, C, Z)] =
    new Zippable[(A, B, C), Z] {
      type Out = (A, B, C, Z)
      def zip(left: (A, B, C), right: Z): (A, B, C, Z) =
        (left._1, left._2, left._3, right)
    }

  implicit def Zippable5[A, B, C, D, Z]: Zippable.Out[(A, B, C, D), Z, (A, B, C, D, Z)] =
    new Zippable[(A, B, C, D), Z] {
      type Out = (A, B, C, D, Z)
      def zip(left: (A, B, C, D), right: Z): (A, B, C, D, Z) =
        (left._1, left._2, left._3, left._4, right)
    }

  implicit def Zippable6[A, B, C, D, E, Z]: Zippable.Out[(A, B, C, D, E), Z, (A, B, C, D, E, Z)] =
    new Zippable[(A, B, C, D, E), Z] {
      type Out = (A, B, C, D, E, Z)
      def zip(left: (A, B, C, D, E), right: Z): (A, B, C, D, E, Z) =
        (left._1, left._2, left._3, left._4, left._5, right)
    }

  implicit def Zippable7[A, B, C, D, E, F, Z]: Zippable.Out[(A, B, C, D, E, F), Z, (A, B, C, D, E, F, Z)] =
    new Zippable[(A, B, C, D, E, F), Z] {
      type Out = (A, B, C, D, E, F, Z)
      def zip(left: (A, B, C, D, E, F), right: Z): (A, B, C, D, E, F, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, right)
    }

  implicit def Zippable8[A, B, C, D, E, F, G, Z]: Zippable.Out[(A, B, C, D, E, F, G), Z, (A, B, C, D, E, F, G, Z)] =
    new Zippable[(A, B, C, D, E, F, G), Z] {
      type Out = (A, B, C, D, E, F, G, Z)
      def zip(left: (A, B, C, D, E, F, G), right: Z): (A, B, C, D, E, F, G, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, right)
    }

  implicit def Zippable9[A, B, C, D, E, F, G, H, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H), Z, (A, B, C, D, E, F, G, H, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H), Z] {
      type Out = (A, B, C, D, E, F, G, H, Z)
      def zip(left: (A, B, C, D, E, F, G, H), right: Z): (A, B, C, D, E, F, G, H, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, right)
    }

  implicit def Zippable10[A, B, C, D, E, F, G, H, I, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I), Z, (A, B, C, D, E, F, G, H, I, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I), right: Z): (A, B, C, D, E, F, G, H, I, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, right)
    }

  implicit def Zippable11[A, B, C, D, E, F, G, H, I, J, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J), Z, (A, B, C, D, E, F, G, H, I, J, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I, J), right: Z): (A, B, C, D, E, F, G, H, I, J, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, left._10, right)
    }

  implicit def Zippable12[A, B, C, D, E, F, G, H, I, J, K, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J, K), Z, (A, B, C, D, E, F, G, H, I, J, K, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I, J, K), right: Z): (A, B, C, D, E, F, G, H, I, J, K, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, left._10, left._11, right)
    }

  implicit def Zippable13[A, B, C, D, E, F, G, H, I, J, K, L, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J, K, L), Z, (A, B, C, D, E, F, G, H, I, J, K, L, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I, J, K, L), right: Z): (A, B, C, D, E, F, G, H, I, J, K, L, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          right
        )
    }

  implicit def Zippable14[A, B, C, D, E, F, G, H, I, J, K, L, M, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J, K, L, M), Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I, J, K, L, M), right: Z): (A, B, C, D, E, F, G, H, I, J, K, L, M, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          right
        )
    }

  implicit def Zippable15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          right
        )
    }

  implicit def Zippable16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          right
        )
    }

  implicit def Zippable17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          right
        )
    }

  implicit def Zippable18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          right
        )
    }

  implicit def Zippable19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          right
        )
    }

  implicit def Zippable20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          left._19,
          right
        )
    }

  implicit def Zippable21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          left._19,
          left._20,
          right
        )
    }

  implicit def Zippable22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          left._19,
          left._20,
          left._21,
          right
        )
    }
}

trait ZippableLowPriority3 {

  implicit def Zippable2[A, B]: Zippable.Out[A, B, (A, B)] =
    new Zippable[A, B] {
      type Out = (A, B)
      def zip(left: A, right: B): Out = (left, right)
    }
}
