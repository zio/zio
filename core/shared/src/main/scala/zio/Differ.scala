package zio

import scala.annotation.tailrec

trait Differ[Value, Patch] extends Serializable { self =>

  def combine(first: Patch, second: Patch): Patch

  def diff(oldValue: Value, newValue: Value): Patch

  def empty: Patch

  def patch(patch: Patch)(oldValue: Value): Value

  final def <*>[Value2, Patch2](that: Differ[Value2, Patch2]): Differ[(Value, Value2), (Patch, Patch2)] =
    new Differ[(Value, Value2), (Patch, Patch2)] {
      def combine(first: (Patch, Patch2), second: (Patch, Patch2)): (Patch, Patch2) =
        (self.combine(first._1, second._1), that.combine(first._2, second._2))
      def diff(oldValue: (Value, Value2), newValue: (Value, Value2)): (Patch, Patch2) =
        (self.diff(oldValue._1, newValue._1), that.diff(oldValue._2, newValue._2))
      def empty: (Patch, Patch2) =
        (self.empty, that.empty)
      def patch(patch: (Patch, Patch2))(oldValue: (Value, Value2)): (Value, Value2) =
        (self.patch(patch._1)(oldValue._1), that.patch(patch._2)(oldValue._2))
    }

  final def <+>[Value2, Patch2](
    that: Differ[Value2, Patch2]
  ): Differ[Either[Value, Value2], Differ.OrPatch[Value, Value2, Patch, Patch2]] =
    new Differ[Either[Value, Value2], Differ.OrPatch[Value, Value2, Patch, Patch2]] {
      def combine(
        first: Differ.OrPatch[Value, Value2, Patch, Patch2],
        second: Differ.OrPatch[Value, Value2, Patch, Patch2]
      ): Differ.OrPatch[Value, Value2, Patch, Patch2] =
        first combine second
      def diff(
        oldValue: Either[Value, Value2],
        newValue: Either[Value, Value2]
      ): Differ.OrPatch[Value, Value2, Patch, Patch2] =
        Differ.OrPatch.diff(oldValue, newValue)(self, that)
      def empty: Differ.OrPatch[Value, Value2, Patch, Patch2] =
        Differ.OrPatch.empty
      def patch(patch: Differ.OrPatch[Value, Value2, Patch, Patch2])(
        oldValue: Either[Value, Value2]
      ): Either[Value, Value2] =
        patch(oldValue)(self, that)
    }

  final def transform[Value2](f: Value => Value2, g: Value2 => Value): Differ[Value2, Patch] =
    new Differ[Value2, Patch] {
      def combine(first: Patch, second: Patch): Patch =
        self.combine(first, second)
      def diff(oldValue: Value2, newValue: Value2): Patch =
        self.diff(g(oldValue), g(newValue))
      def empty: Patch =
        self.empty
      def patch(patch: Patch)(oldValue: Value2): Value2 =
        f(self.patch(patch)(g(oldValue)))
    }
}

object Differ {

  def chunk[Value, Patch](differ: Differ[Value, Patch]): Differ[Chunk[Value], ChunkPatch[Value, Patch]] =
    new Differ[Chunk[Value], ChunkPatch[Value, Patch]] {
      def combine(first: ChunkPatch[Value, Patch], second: ChunkPatch[Value, Patch]): ChunkPatch[Value, Patch] =
        first combine second
      def diff(oldValue: Chunk[Value], newValue: Chunk[Value]): ChunkPatch[Value, Patch] =
        ChunkPatch.diff(oldValue, newValue)(differ)
      def empty: ChunkPatch[Value, Patch] =
        ChunkPatch.empty
      def patch(patch: ChunkPatch[Value, Patch])(oldValue: Chunk[Value]): Chunk[Value] =
        patch(oldValue)(differ)
    }

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

  def map[Key, Value, Patch](differ: Differ[Value, Patch]): Differ[Map[Key, Value], MapPatch[Key, Value, Patch]] =
    new Differ[Map[Key, Value], MapPatch[Key, Value, Patch]] {
      def combine(
        first: MapPatch[Key, Value, Patch],
        second: MapPatch[Key, Value, Patch]
      ): MapPatch[Key, Value, Patch] =
        first combine second
      def diff(oldValue: Map[Key, Value], newValue: Map[Key, Value]): MapPatch[Key, Value, Patch] =
        MapPatch.diff(oldValue, newValue)(differ)

      def empty: MapPatch[Key, Value, Patch] =
        MapPatch.empty

      def patch(patch: MapPatch[Key, Value, Patch])(oldValue: Map[Key, Value]): Map[Key, Value] =
        patch(oldValue)
    }

  sealed trait ChunkPatch[Value, Patch] { self =>
    import ChunkPatch._

    def apply(oldValue: Chunk[Value])(differ: Differ[Value, Patch]): Chunk[Value] = {

      @tailrec
      def loop(chunk: Chunk[Value], patches: List[ChunkPatch[Value, Patch]]): Chunk[Value] =
        patches match {
          case Append(values) :: patches =>
            loop(chunk ++ values, patches)
          case AndThen(first, second) :: patches =>
            loop(chunk, first :: second :: patches)
          case Empty() :: patches =>
            loop(chunk, patches)
          case Slice(from, until) :: patches =>
            loop(chunk.slice(from, until), patches)
          case Update(index, patch) :: patches =>
            loop(chunk.updated(index, differ.patch(patch)(chunk(index))), patches)
          case Nil =>
            chunk
        }

      loop(oldValue, List(self))
    }

    def combine(that: ChunkPatch[Value, Patch]): ChunkPatch[Value, Patch] =
      AndThen(self, that)
  }

  object ChunkPatch {

    def diff[Value, Patch](oldValue: Chunk[Value], newValue: Chunk[Value])(
      differ: Differ[Value, Patch]
    ): ChunkPatch[Value, Patch] = {
      var i           = 0
      val oldIterator = oldValue.chunkIterator
      val newIterator = newValue.chunkIterator
      var patch       = empty[Value, Patch]
      while (oldIterator.hasNextAt(i) && newIterator.hasNextAt(i)) {
        val oldValue   = oldIterator.nextAt(i)
        val newValue   = newIterator.nextAt(i)
        val valuePatch = differ.diff(oldValue, newValue)
        if (valuePatch != empty) { patch = patch combine Update(i, valuePatch) }
        i += 1
      }
      if (oldIterator.hasNextAt(i)) {
        patch combine Slice(0, i)
      }
      if (newIterator.hasNextAt(i)) {
        patch combine Append(newValue.drop(i))
      }
      patch
    }

    def empty[Value, Patch]: ChunkPatch[Value, Patch] =
      Empty()

    final case class Append[Value, Patch](values: Chunk[Value])     extends ChunkPatch[Value, Patch]
    final case class Slice[Value, Patch](from: Int, until: Int)     extends ChunkPatch[Value, Patch]
    final case class Update[Value, Patch](index: Int, patch: Patch) extends ChunkPatch[Value, Patch]
    final case class AndThen[Value, Patch](first: ChunkPatch[Value, Patch], second: ChunkPatch[Value, Patch])
        extends ChunkPatch[Value, Patch]
    final case class Empty[Value, Patch]() extends ChunkPatch[Value, Patch]
  }

  sealed trait MapPatch[Key, Value, Patch] { self =>
    import MapPatch._

    def apply(oldValue: Map[Key, Value]): Map[Key, Value] = {

      @tailrec
      def loop(map: Map[Key, Value], patches: List[MapPatch[Key, Value, Patch]]): Map[Key, Value] =
        patches match {
          case Add(key, value) :: patches =>
            loop(map + ((key, value)), patches)
          case AndThen(first, second) :: patches =>
            loop(map, first :: second :: patches)
          case Empty() :: patches =>
            loop(map, patches)
          case Remove(key) :: patches =>
            loop(map - key, patches)
          case Nil =>
            map
        }

      loop(oldValue, List(self))
    }

    def combine(that: MapPatch[Key, Value, Patch]): MapPatch[Key, Value, Patch] =
      AndThen(self, that)
  }

  object MapPatch {

    def diff[Key, Value, Patch](oldValue: Map[Key, Value], newValue: Map[Key, Value])(
      differ: Differ[Value, Patch]
    ): MapPatch[Key, Value, Patch] = {
      val (removed, patch) = newValue.foldLeft[(Map[Key, Value], MapPatch[Key, Value, Patch])](oldValue -> empty) {
        case ((map, patch), (key, newValue)) =>
          map.get(key) match {
            case Some(oldValue) =>
              val valuePatch = differ.diff(oldValue, newValue)
              if (valuePatch == empty)
                map - key -> patch
              else
                map - key -> patch.combine(Update(key, valuePatch))
            case _ =>
              map - key -> patch.combine(Add(key, newValue))
          }
      }
      removed.foldLeft(patch) { case (patch, (key, _)) => patch.combine(Remove(key)) }
    }

    def empty[Key, Value, Patch]: MapPatch[Key, Value, Patch] =
      Empty()

    final case class Add[Key, Value, Patch](key: Key, value: Value)    extends MapPatch[Key, Value, Patch]
    final case class Remove[Key, Value, Patch](key: Key)               extends MapPatch[Key, Value, Patch]
    final case class Update[Key, Value, Patch](key: Key, patch: Patch) extends MapPatch[Key, Value, Patch]
    final case class Empty[Key, Value, Patch]()                        extends MapPatch[Key, Value, Patch]
    final case class AndThen[Key, Value, Patch](first: MapPatch[Key, Value, Patch], second: MapPatch[Key, Value, Patch])
        extends MapPatch[Key, Value, Patch]
  }

  sealed trait OrPatch[Value, Value2, Patch, Patch2] { self =>
    import OrPatch._

    def apply(
      oldValue: Either[Value, Value2]
    )(left: Differ[Value, Patch], right: Differ[Value2, Patch2]): Either[Value, Value2] = {

      @tailrec
      def loop(
        either: Either[Value, Value2],
        patches: List[OrPatch[Value, Value2, Patch, Patch2]]
      ): Either[Value, Value2] =
        patches match {
          case AndThen(first, second) :: patches => loop(either, first :: second :: patches)
          case Empty() :: patches                => loop(either, patches)
          case UpdateLeft(patch) :: patches =>
            either match {
              case Left(value) => loop(Left(left.patch(patch)(value)), patches)
              case Right(_)    => ???
            }
          case UpdateRight(patch) :: patches =>
            either match {
              case Right(value2) => loop(Right(right.patch(patch)(value2)), patches)
              case Left(_)       => ???
            }
          case SetLeft(value) :: patches   => loop(Left(value), patches)
          case SetRight(value2) :: patches => loop(Right(value2), patches)
          case Nil                         => either
        }

      loop(oldValue, List(self))
    }

    def combine(that: OrPatch[Value, Value2, Patch, Patch2]): OrPatch[Value, Value2, Patch, Patch2] =
      AndThen(self, that)

  }

  object OrPatch {

    def diff[Value, Value2, Patch, Patch2](
      oldValue: Either[Value, Value2],
      newValue: Either[Value, Value2]
    )(left: Differ[Value, Patch], right: Differ[Value2, Patch2]): OrPatch[Value, Value2, Patch, Patch2] =
      (oldValue, newValue) match {
        case (Left(oldValue), Left(newValue))   => UpdateLeft(left.diff(oldValue, newValue))
        case (Right(oldValue), Right(newValue)) => UpdateRight(right.diff(oldValue, newValue))
        case (Left(_), Right(newValue))         => SetRight(newValue)
        case (Right(_), Left(newValue))         => SetLeft(newValue)
      }

    def empty[Value, Value2, Patch, Patch2]: OrPatch[Value, Value2, Patch, Patch2] =
      Empty()

    final case class AndThen[Value, Value2, Patch, Patch2](
      first: OrPatch[Value, Value2, Patch, Patch2],
      second: OrPatch[Value, Value2, Patch, Patch2]
    ) extends OrPatch[Value, Value2, Patch, Patch2]
    final case class Empty[Value, Value2, Patch, Patch2]()                 extends OrPatch[Value, Value2, Patch, Patch2]
    final case class SetLeft[Value, Value2, Patch, Patch2](value: Value)   extends OrPatch[Value, Value2, Patch, Patch2]
    final case class SetRight[Value, Value2, Patch, Patch2](value: Value2) extends OrPatch[Value, Value2, Patch, Patch2]
    final case class UpdateLeft[Value, Value2, Patch, Patch2](patch: Patch)
        extends OrPatch[Value, Value2, Patch, Patch2]
    final case class UpdateRight[Value, Value2, Patch, Patch2](patch: Patch2)
        extends OrPatch[Value, Value2, Patch, Patch2]
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
