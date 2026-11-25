package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A type class that splits one value into two, the inverse of [[Zippable]]: a
 * tuple `(A, B, C)` splits into `(A, B)` and `C`, and a value with `Unit` on
 * one side splits into `()` and the value.
 */
trait Unzippable[A, B] {

  /** The type of the value that `unzip` splits. */
  type In

  /** Splits `in` into a pair of values. */
  def unzip(in: In): (A, B)
}

/**
 * Instances of [[Unzippable]]. The search tries them in this order: `Unit` on
 * the left, `Unit` on the right, a tuple of 3 to 22 values, and last a pair.
 */
object Unzippable extends UnzippableLowPriority1 {

  /** An [[Unzippable]] for `A` and `B` whose input has type `C`. */
  type In[A, B, C] = Unzippable[A, B] { type In = C }

  /** Splits a value of type `A` into `()` and that value. */
  implicit def UnzippableLeftIdentity[A]:Unzippable.In[Unit, A, A] =
    new Unzippable[Unit, A] {
      type In = A
      def unzip(in: A): (Unit, A) =
        ((), in)
    }
}

/** [[Unzippable]] instances with a `Unit` on the right. */
trait UnzippableLowPriority1 extends UnzippableLowPriority2 {

  /** Splits a value of type `A` into that value and `()`. */
  implicit def UnzippableRightIdentity[A]:Unzippable.In[A, Unit, A] =
    new Unzippable[A, Unit] {
      type In = A
      def unzip(in: A): (A, Unit) =
        (in, ())
    }
}

/** [[Unzippable]] instances that take the last value off a tuple. */
trait UnzippableLowPriority2 extends UnzippableLowPriority3 {

  /** Splits a tuple of 3 values into a tuple of its first 2 and its last value. */
  implicit def Unzippable3[A, B, Z]: Unzippable.In[(A, B), Z, (A, B, Z)] =
    new Unzippable[(A, B), Z] {
      type In = (A, B, Z)
      def unzip(in: (A, B, Z)): ((A, B), Z) =
        ((in._1, in._2), in._3)
    }

  /** Splits a tuple of 4 values into a tuple of its first 3 and its last value. */
  implicit def Unzippable4[A, B, C, Z]: Unzippable.In[(A, B, C), Z, (A, B, C, Z)] =
    new Unzippable[(A, B, C), Z] {
      type In = (A, B, C, Z)
      def unzip(in: (A, B, C, Z)): ((A, B, C), Z) =
        ((in._1, in._2, in._3), in._4)
    }

  /** Splits a tuple of 5 values into a tuple of its first 4 and its last value. */
  implicit def Unzippable5[A, B, C, D, Z]: Unzippable.In[(A, B, C, D), Z, (A, B, C, D, Z)] =
    new Unzippable[(A, B, C, D), Z] {
      type In = (A, B, C, D, Z)
      def unzip(in: (A, B, C, D, Z)): ((A, B, C, D), Z) =
        ((in._1, in._2, in._3, in._4), in._5)
    }

  /** Splits a tuple of 6 values into a tuple of its first 5 and its last value. */
  implicit def Unzippable6[A, B, C, D, E, Z]: Unzippable.In[(A, B, C, D, E), Z, (A, B, C, D, E, Z)] =
    new Unzippable[(A, B, C, D, E), Z] {
      type In = (A, B, C, D, E, Z)
      def unzip(in: (A, B, C, D, E, Z)): ((A, B, C, D, E), Z) =
        ((in._1, in._2, in._3, in._4, in._5), in._6)
    }

  /** Splits a tuple of 7 values into a tuple of its first 6 and its last value. */
  implicit def Unzippable7[A, B, C, D, E, F, Z]: Unzippable.In[(A, B, C, D, E, F), Z, (A, B, C, D, E, F, Z)] =
    new Unzippable[(A, B, C, D, E, F), Z] {
      type In = (A, B, C, D, E, F, Z)
      def unzip(in: (A, B, C, D, E, F, Z)): ((A, B, C, D, E, F), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6), in._7)
    }

  /** Splits a tuple of 8 values into a tuple of its first 7 and its last value. */
  implicit def Unzippable8[A, B, C, D, E, F, G, Z]: Unzippable.In[(A, B, C, D, E, F, G), Z, (A, B, C, D, E, F, G, Z)] =
    new Unzippable[(A, B, C, D, E, F, G), Z] {
      type In = (A, B, C, D, E, F, G, Z)
      def unzip(in: (A, B, C, D, E, F, G, Z)): ((A, B, C, D, E, F, G), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7), in._8)
    }

  /** Splits a tuple of 9 values into a tuple of its first 8 and its last value. */
  implicit def Unzippable9[A, B, C, D, E, F, G, H, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H), Z, (A, B, C, D, E, F, G, H, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H), Z] {
      type In = (A, B, C, D, E, F, G, H, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, Z)): ((A, B, C, D, E, F, G, H), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8), in._9)
    }

  /** Splits a tuple of 10 values into a tuple of its first 9 and its last value. */
  implicit def Unzippable10[A, B, C, D, E, F, G, H, I, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H, I), Z, (A, B, C, D, E, F, G, H, I, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H, I), Z] {
      type In = (A, B, C, D, E, F, G, H, I, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, I, Z)): ((A, B, C, D, E, F, G, H, I), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8, in._9), in._10)
    }

  /** Splits a tuple of 11 values into a tuple of its first 10 and its last value. */
  implicit def Unzippable11[A, B, C, D, E, F, G, H, I, J, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H, I, J), Z, (A, B, C, D, E, F, G, H, I, J, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, I, J, Z)): ((A, B, C, D, E, F, G, H, I, J), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8, in._9, in._10), in._11)
    }

  /** Splits a tuple of 12 values into a tuple of its first 11 and its last value. */
  implicit def Unzippable12[A, B, C, D, E, F, G, H, I, J, K, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H, I, J, K), Z, (A, B, C, D, E, F, G, H, I, J, K, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, I, J, K, Z)): ((A, B, C, D, E, F, G, H, I, J, K), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8, in._9, in._10, in._11), in._12)
    }

  /** Splits a tuple of 13 values into a tuple of its first 12 and its last value. */
  implicit def Unzippable13[A, B, C, D, E, F, G, H, I, J, K, L, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H, I, J, K, L), Z, (A, B, C, D, E, F, G, H, I, J, K, L, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, I, J, K, L, Z)): ((A, B, C, D, E, F, G, H, I, J, K, L), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8, in._9, in._10, in._11, in._12), in._13)
    }

  /** Splits a tuple of 14 values into a tuple of its first 13 and its last value. */
  implicit def Unzippable14[A, B, C, D, E, F, G, H, I, J, K, L, M, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H, I, J, K, L, M), Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)): ((A, B, C, D, E, F, G, H, I, J, K, L, M), Z) =
        ((in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8, in._9, in._10, in._11, in._12, in._13), in._14)
    }

  /** Splits a tuple of 15 values into a tuple of its first 14 and its last value. */
  implicit def Unzippable15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z]
    : Unzippable.In[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)
      def unzip(in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N), Z) =
        (
          (in._1, in._2, in._3, in._4, in._5, in._6, in._7, in._8, in._9, in._10, in._11, in._12, in._13, in._14),
          in._15
        )
    }

  /** Splits a tuple of 16 values into a tuple of its first 15 and its last value. */
  implicit def Unzippable16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15
          ),
          in._16
        )
    }

  /** Splits a tuple of 17 values into a tuple of its first 16 and its last value. */
  implicit def Unzippable17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15,
            in._16
          ),
          in._17
        )
    }

  /** Splits a tuple of 18 values into a tuple of its first 17 and its last value. */
  implicit def Unzippable18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15,
            in._16,
            in._17
          ),
          in._18
        )
    }

  /** Splits a tuple of 19 values into a tuple of its first 18 and its last value. */
  implicit def Unzippable19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15,
            in._16,
            in._17,
            in._18
          ),
          in._19
        )
    }

  /** Splits a tuple of 20 values into a tuple of its first 19 and its last value. */
  implicit def Unzippable20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15,
            in._16,
            in._17,
            in._18,
            in._19
          ),
          in._20
        )
    }

  /** Splits a tuple of 21 values into a tuple of its first 20 and its last value. */
  implicit def Unzippable21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15,
            in._16,
            in._17,
            in._18,
            in._19,
            in._20
          ),
          in._21
        )
    }

  /** Splits a tuple of 22 values into a tuple of its first 21 and its last value. */
  implicit def Unzippable22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z]: Unzippable.In[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
  ] =
    new Unzippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), Z] {
      type In = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
      def unzip(
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
      ): ((A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), Z) =
        (
          (
            in._1,
            in._2,
            in._3,
            in._4,
            in._5,
            in._6,
            in._7,
            in._8,
            in._9,
            in._10,
            in._11,
            in._12,
            in._13,
            in._14,
            in._15,
            in._16,
            in._17,
            in._18,
            in._19,
            in._20,
            in._21
          ),
          in._22
        )
    }
}

/** The [[Unzippable]] instance with the lowest priority. */
trait UnzippableLowPriority3 {

  /** Splits the pair `(A, B)` into its two values. */
  implicit def Unzippable2[A, B]: Unzippable.In[A, B, (A, B)] =
    new Unzippable[A, B] {
      type In = (A, B)
      def unzip(in: (A, B)): (A, B) =
        (in._1, in._2)
    }
}
