package zio

import scala.annotation.tailrec

trait Differ[Value, Patch] extends Serializable {

  def combine(first: Patch, second: Patch): Patch

  def diff(oldValue: Value, newValue: Value): Patch

  def empty: Patch

  def patch(patch: Patch)(oldValue: Value): Value
}

object Differ {

  def environment[A]: Differ[ZEnvironment[A], ZEnvironment.Patch[A, A]] =
    new Differ[ZEnvironment[A], ZEnvironment.Patch[A, A]] {
      def combine(first: ZEnvironment.Patch[A, A], second: ZEnvironment.Patch[A, A]): ZEnvironment.Patch[A, A] =
        first combine second
      def diff(oldValue: ZEnvironment[A], newValue: ZEnvironment[A]): ZEnvironment.Patch[A, A] =
        ZEnvironment.Patch.diff(oldValue, newValue)
      def empty: ZEnvironment.Patch[A, A] =
        ZEnvironment.Patch.empty
      def patch(patch: ZEnvironment.Patch[A, A])(oldValue: ZEnvironment[A]): ZEnvironment[A] =
        patch(oldValue)
    }

  def update[A](f: (A, A) => A): Differ[A, A => A] =
    new Differ[A, A => A] {
      def combine(first: A => A, second: A => A): A => A =
        a => second(first(a))
      def diff(oldValue: A, newValue: A): A => A =
        _ => newValue
      def empty: A => A =
        identity
      def patch(patch: A => A)(oldValue: A): A =
        f(oldValue, patch(oldValue))

    }

  def set[A]: Differ[Set[A], SetPatch[A]] =
    new Differ[Set[A], SetPatch[A]] {
      def combine(first: SetPatch[A], second: SetPatch[A]): SetPatch[A] =
        first combine second
      def diff(oldValue: Set[A], newValue: Set[A]): SetPatch[A] =
        SetPatch.diff(oldValue, newValue)
      def empty: SetPatch[A] =
        SetPatch.empty
      def patch(patch: SetPatch[A])(oldValue: Set[A]): Set[A] =
        patch(oldValue)
    }

  sealed trait SetPatch[A] { self =>
    import SetPatch._

    def apply(oldValue: Set[A]): Set[A] = {

      @tailrec
      def loop(set: Set[A], patches: List[SetPatch[A]]): Set[A] =
        patches match {
          case Add(a) :: patches =>
            loop(set + a, patches)
          case AndThen(first, second) :: patches =>
            loop(set, first :: second :: patches)
          case Empty() :: patches =>
            loop(set, patches)
          case Remove(a) :: patches =>
            loop(set - a, patches)
          case Nil =>
            set
        }

      loop(oldValue, List(self))
    }

    def combine(that: SetPatch[A]): SetPatch[A] =
      AndThen(self, that)
  }

  object SetPatch {

    def diff[A](oldValue: Set[A], newValue: Set[A]): SetPatch[A] = {
      val (removed, patch) = newValue.foldLeft[(Set[A], SetPatch[A])](oldValue -> empty) { case ((set, patch), a) =>
        if (set.contains(a)) (set - a, patch)
        else (set, patch.combine(Add(a)))
      }
      removed.foldLeft(patch)((patch, a) => patch.combine(Remove(a)))
    }

    def empty[A]: SetPatch[A] =
      Empty()

    final case class Add[A](value: A)                                    extends SetPatch[A]
    final case class AndThen[A](first: SetPatch[A], second: SetPatch[A]) extends SetPatch[A]
    final case class Empty[A]()                                          extends SetPatch[A]
    final case class Remove[A](value: A)                                 extends SetPatch[A]
  }

}
