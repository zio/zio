package zio

import zio.internal.IsFatal

import scala.annotation.tailrec

/**
 * A `Differ[Value, Patch]` knows how to compare an old value and new value of
 * type `Value` to produce a patch of type `Patch` that describes the
 * differences between those values. A `Differ` also knows how to apply a patch
 * to an old value to produce a new value that represents the old value updated
 * with the changes described by the patch.
 *
 * A `Differ` can be used to construct a `FiberRef` supporting compositional
 * updates using the `FiberRef.makePatch` constructor.
 *
 * The `Differ` companion object contains constructors for `Differ` values for
 * common data types such as `Chunk`, `Map`, and `Set``. In addition, `Differ`
 * values can be transformed using the `transform` operator and combined using
 * the `orElseEither` and `zip` operators. This allows creating `Differ` values
 * for arbitrarily complex data types compositionally.
 */
trait Differ[Value, Patch] extends Serializable { self =>

  /**
   * Combines two patches to produce a new patch that describes the updates of
   * the first patch and then the updates of the second patch. The combine
   * operation should be associative. In addition, if the combine operation is
   * commutative then joining multiple fibers concurrently will result in
   * deterministic `FiberRef` values.
   */
  def combine(first: Patch, second: Patch): Patch

  /**
   * Constructs a patch describing the updates to a value from an old value and
   * a new value.
   */
  def diff(oldValue: Value, newValue: Value): Patch

  /**
   * An empty patch that describes no changes.
   */
  def empty: Patch

  /**
   * Applies a patch to an old value to produce a new value that is equal to the
   * old value with the updates described by the patch.
   */
  def patch(patch: Patch)(oldValue: Value): Value

  /**
   * A symbolic alias for `zip`.
   */
  final def <*>[Value2, Patch2](that: Differ[Value2, Patch2]): Differ[(Value, Value2), (Patch, Patch2)] =
    self.zip(that)

  /**
   * A symbolic alias for `orElseEither`.
   */
  final def <+>[Value2, Patch2](
    that: Differ[Value2, Patch2]
  ): Differ[Either[Value, Value2], Differ.OrPatch[Value, Value2, Patch, Patch2]] =
    self.orElseEither(that)

  /**
   * Combines this differ and the specified differ to produce a differ that
   * knows how to diff the sum of their values.
   */
  final def orElseEither[Value2, Patch2](
    that: Differ[Value2, Patch2]
  ): Differ[Either[Value, Value2], Differ.OrPatch[Value, Value2, Patch, Patch2]] =
    new Differ[Either[Value, Value2], Differ.OrPatch[Value, Value2, Patch, Patch2]] {
      def combine(
        first: Differ.OrPatch[Value, Value2, Patch, Patch2],
        second: Differ.OrPatch[Value, Value2, Patch, Patch2]
      ): Differ.OrPatch[Value, Value2, Patch, Patch2] =
        first.combine(second)
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

  /**
   * Transforms the type of values that this differ knows how to differ using
   * the specified functions that map the new and old value types to each other.
   */
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

  /**
   * Combines this differ and the specified differ to produce a new differ that
   * knows how to diff the product of their values.
   */
  final def zip[Value2, Patch2](that: Differ[Value2, Patch2]): Differ[(Value, Value2), (Patch, Patch2)] =
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
}

object Differ {

  /**
   * Constructs a differ that knows how to diff a `Chunk` of values given a
   * differ that knows how to diff the values.
   */
  def chunk[Value, Patch](differ: Differ[Value, Patch]): Differ[Chunk[Value], ChunkPatch[Value, Patch]] =
    new Differ[Chunk[Value], ChunkPatch[Value, Patch]] {
      def combine(first: ChunkPatch[Value, Patch], second: ChunkPatch[Value, Patch]): ChunkPatch[Value, Patch] =
        first.combine(second)
      def diff(oldValue: Chunk[Value], newValue: Chunk[Value]): ChunkPatch[Value, Patch] =
        ChunkPatch.diff(oldValue, newValue)(differ)
      def empty: ChunkPatch[Value, Patch] =
        ChunkPatch.empty
      def patch(patch: ChunkPatch[Value, Patch])(oldValue: Chunk[Value]): Chunk[Value] =
        patch(oldValue)(differ)
    }

  /**
   * Constructs a differ that knows how to diff `ZEnvironment` values.
   */
  def environment[A]: Differ[ZEnvironment[A], ZEnvironment.Patch[A, A]] =
    new Differ[ZEnvironment[A], ZEnvironment.Patch[A, A]] {
      def combine(first: ZEnvironment.Patch[A, A], second: ZEnvironment.Patch[A, A]): ZEnvironment.Patch[A, A] =
        first.combine(second)
      def diff(oldValue: ZEnvironment[A], newValue: ZEnvironment[A]): ZEnvironment.Patch[A, A] =
        ZEnvironment.Patch.diff(oldValue, newValue)
      def empty: ZEnvironment.Patch[A, A] =
        ZEnvironment.Patch.empty
      def patch(patch: ZEnvironment.Patch[A, A])(oldValue: ZEnvironment[A]): ZEnvironment[A] =
        patch(oldValue)
    }

  /**
   * Constructs a differ that knows how to diff `IsFatal` values.
   */
  def isFatal: Differ[IsFatal, IsFatal.Patch] =
    new Differ[IsFatal, IsFatal.Patch] {
      def combine(first: IsFatal.Patch, second: IsFatal.Patch): IsFatal.Patch =
        first.combine(second)
      def diff(oldValue: IsFatal, newValue: IsFatal): IsFatal.Patch =
        IsFatal.Patch.diff(oldValue, newValue)
      def empty: IsFatal.Patch =
        IsFatal.Patch.empty
      def patch(patch: IsFatal.Patch)(oldValue: IsFatal): IsFatal =
        patch(oldValue)
    }

  /**
   * Constructs a differ that knows how to diff a `Map` of keys and values given
   * a differ that knows how to diff the values.
   */
  def map[Key, Value, Patch](differ: Differ[Value, Patch]): Differ[Map[Key, Value], MapPatch[Key, Value, Patch]] =
    new Differ[Map[Key, Value], MapPatch[Key, Value, Patch]] {
      def combine(
        first: MapPatch[Key, Value, Patch],
        second: MapPatch[Key, Value, Patch]
      ): MapPatch[Key, Value, Patch] =
        first.combine(second)
      def diff(oldValue: Map[Key, Value], newValue: Map[Key, Value]): MapPatch[Key, Value, Patch] =
        MapPatch.diff(oldValue, newValue)(differ)
      def empty: MapPatch[Key, Value, Patch] =
        MapPatch.empty
      def patch(patch: MapPatch[Key, Value, Patch])(oldValue: Map[Key, Value]): Map[Key, Value] =
        patch(oldValue)(differ)
    }

  /**
   * Constructs a differ that knows how to diff `RuntimeFlags` values.
   */
  val runtimeFlags: Differ[RuntimeFlags, RuntimeFlags.Patch] =
    new Differ[RuntimeFlags, RuntimeFlags.Patch] {
      def combine(first: RuntimeFlags.Patch, second: RuntimeFlags.Patch): RuntimeFlags.Patch =
        RuntimeFlags.Patch.andThen(first, second)
      def diff(oldValue: RuntimeFlags, newValue: RuntimeFlags): RuntimeFlags.Patch =
        RuntimeFlags.diff(oldValue, newValue)
      def empty: RuntimeFlags.Patch =
        RuntimeFlags.Patch.empty
      def patch(patch: RuntimeFlags.Patch)(oldValue: RuntimeFlags): RuntimeFlags =
        RuntimeFlags.patch(patch)(oldValue)
    }

  /**
   * Constructs a differ that knows how to diff a `Set` of values.
   */
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

  /**
   * Constructs a differ that knows how to diff `Supervisor` values.
   */
  def supervisor: Differ[Supervisor[Any], Supervisor.Patch] =
    new Differ[Supervisor[Any], Supervisor.Patch] {
      def combine(first: Supervisor.Patch, second: Supervisor.Patch): Supervisor.Patch =
        first.combine(second)
      def diff(oldValue: Supervisor[Any], newValue: Supervisor[Any]): Supervisor.Patch =
        Supervisor.Patch.diff(oldValue, newValue)
      def empty: Supervisor.Patch =
        Supervisor.Patch.empty
      def patch(patch: Supervisor.Patch)(oldValue: Supervisor[Any]): Supervisor[Any] =
        patch(oldValue)
    }

  /**
   * Constructs a differ that just diffs two values by returning a function that
   * sets the value to the new value. This differ does not support combining
   * multiple updates to the value compositionally and should only be used when
   * there is no compositional way to update them.
   */
  def update[A]: Differ[A, A => A] =
    new Differ[A, A => A] {
      def combine(first: A => A, second: A => A): A => A =
        if (first == empty) second
        else if (second == empty) first
        else first.andThen(second)
      def diff(oldValue: A, newValue: A): A => A =
        if (oldValue == newValue) empty else Function.const(newValue)
      def empty: A => A =
        ZIO.identityFn
      def patch(patch: A => A)(oldValue: A): A =
        patch(oldValue)
    }

  /**
   * A patch which describes updates to a chunk of values.
   */
  sealed trait ChunkPatch[Value, Patch] { self =>
    import ChunkPatch._

    /**
     * Applies a chunk patch to a chunk of values to produce a new chunk of
     * values which represents the original chunk of values updated with the
     * changes described by this patch.
     */
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

    /**
     * Combines two chunk patches to produce a new chunk patch that describes
     * applying their changes sequentially.
     */
    def combine(that: ChunkPatch[Value, Patch]): ChunkPatch[Value, Patch] =
      AndThen(self, that)
  }

  object ChunkPatch {

    /**
     * Constructs a chunk patch from a new and old chunk of values and a differ
     * for the values.
     */
    def diff[Value, Patch](oldValue: Chunk[Value], newValue: Chunk[Value])(
      differ: Differ[Value, Patch]
    ): ChunkPatch[Value, Patch] =
      if (oldValue == newValue) Empty()
      else {
        var i           = 0
        val oldIterator = oldValue.chunkIterator
        val newIterator = newValue.chunkIterator
        var patch       = empty[Value, Patch]
        while (oldIterator.hasNextAt(i) && newIterator.hasNextAt(i)) {
          val oldValue   = oldIterator.nextAt(i)
          val newValue   = newIterator.nextAt(i)
          val valuePatch = differ.diff(oldValue, newValue)
          if (valuePatch != differ.empty) { patch = patch combine Update(i, valuePatch) }
          i += 1
        }
        if (oldIterator.hasNextAt(i)) {
          patch = patch combine Slice(0, i)
        }
        if (newIterator.hasNextAt(i)) {
          patch = patch combine Append(newValue.drop(i))
        }
        patch
      }

    /**
     * Constructs an empty chunk patch.
     */
    def empty[Value, Patch]: ChunkPatch[Value, Patch] =
      Empty()

    private final case class Append[Value, Patch](values: Chunk[Value])     extends ChunkPatch[Value, Patch]
    private final case class Slice[Value, Patch](from: Int, until: Int)     extends ChunkPatch[Value, Patch]
    private final case class Update[Value, Patch](index: Int, patch: Patch) extends ChunkPatch[Value, Patch]
    private final case class AndThen[Value, Patch](first: ChunkPatch[Value, Patch], second: ChunkPatch[Value, Patch])
        extends ChunkPatch[Value, Patch]
    private final case class Empty[Value, Patch]() extends ChunkPatch[Value, Patch]
  }

  /**
   * A patch which describes updates to a map of keys and values.
   */
  sealed trait MapPatch[Key, Value, Patch] { self =>
    import MapPatch._

    /**
     * Applies a map patch to a map of keys and values to produce a new map of
     * keys and values values which represents the original map of keys and
     * values updated with the changes described by this patch.
     */
    def apply(oldValue: Map[Key, Value])(differ: Differ[Value, Patch]): Map[Key, Value] = {

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
          case Update(key, patch) :: patches =>
            loop(map.get(key).fold(map)(oldValue => map.updated(key, differ.patch(patch)(oldValue))), patches)
          case Nil =>
            map
        }

      loop(oldValue, List(self))
    }

    /**
     * Combines two map patches to produce a new map patch that describes
     * applying their changes sequentially.
     */
    def combine(that: MapPatch[Key, Value, Patch]): MapPatch[Key, Value, Patch] =
      AndThen(self, that)
  }

  object MapPatch {

    /**
     * Constructs a map patch from a new and old map of keys and values and a
     * differ for the values.
     */
    def diff[Key, Value, Patch](oldValue: Map[Key, Value], newValue: Map[Key, Value])(
      differ: Differ[Value, Patch]
    ): MapPatch[Key, Value, Patch] =
      if (oldValue == newValue) Empty()
      else {
        val (removed, patch) = newValue.foldLeft[(Map[Key, Value], MapPatch[Key, Value, Patch])](oldValue -> empty) {
          case ((map, patch), (key, newValue)) =>
            map.get(key) match {
              case Some(oldValue) =>
                val valuePatch = differ.diff(oldValue, newValue)
                if (valuePatch == differ.empty)
                  map - key -> patch
                else
                  map - key -> patch.combine(Update(key, valuePatch))
              case _ =>
                map - key -> patch.combine(Add(key, newValue))
            }
        }
        removed.foldLeft(patch) { case (patch, (key, _)) => patch.combine(Remove(key)) }
      }

    /**
     * Constructs an empty map patch.
     */
    def empty[Key, Value, Patch]: MapPatch[Key, Value, Patch] =
      Empty()

    private final case class Add[Key, Value, Patch](key: Key, value: Value)    extends MapPatch[Key, Value, Patch]
    private final case class Remove[Key, Value, Patch](key: Key)               extends MapPatch[Key, Value, Patch]
    private final case class Update[Key, Value, Patch](key: Key, patch: Patch) extends MapPatch[Key, Value, Patch]
    private final case class Empty[Key, Value, Patch]()                        extends MapPatch[Key, Value, Patch]
    private final case class AndThen[Key, Value, Patch](
      first: MapPatch[Key, Value, Patch],
      second: MapPatch[Key, Value, Patch]
    ) extends MapPatch[Key, Value, Patch]
  }

  /**
   * A patch which describes updates to either one value or another.
   */
  sealed trait OrPatch[Value, Value2, Patch, Patch2] { self =>
    import OrPatch._

    /**
     * Applies an or patch to a value to produce a new value which represents
     * the original value updated with the changes described by this patch.
     */
    def apply(
      oldValue: Either[Value, Value2]
    )(left: Differ[Value, Patch], right: Differ[Value2, Patch2]): Either[Value, Value2] = {

      @tailrec
      def loop(
        either: Either[Value, Value2],
        patches: List[OrPatch[Value, Value2, Patch, Patch2]]
      ): Either[Value, Value2] =
        patches match {
          case AndThen(first, second) :: patches =>
            loop(either, first :: second :: patches)
          case Empty() :: patches =>
            loop(either, patches)
          case UpdateLeft(patch) :: patches =>
            either match {
              case Left(value)   => loop(Left(left.patch(patch)(value)), patches)
              case Right(value2) => loop(Right(value2), patches)
            }
          case UpdateRight(patch) :: patches =>
            either match {
              case Left(value)   => loop(Left(value), patches)
              case Right(value2) => loop(Right(right.patch(patch)(value2)), patches)
            }
          case SetLeft(value) :: patches =>
            loop(Left(value), patches)
          case SetRight(value2) :: patches =>
            loop(Right(value2), patches)
          case Nil =>
            either
        }

      loop(oldValue, List(self))
    }

    /**
     * Combines two or patches to produce a new or patch that describes applying
     * their changes sequentially.
     */
    def combine(that: OrPatch[Value, Value2, Patch, Patch2]): OrPatch[Value, Value2, Patch, Patch2] =
      AndThen(self, that)

  }

  object OrPatch {

    /**
     * Constructs an or patch from a new and old value and a differ for the
     * values.
     */
    def diff[Value, Value2, Patch, Patch2](
      oldValue: Either[Value, Value2],
      newValue: Either[Value, Value2]
    )(left: Differ[Value, Patch], right: Differ[Value2, Patch2]): OrPatch[Value, Value2, Patch, Patch2] =
      (oldValue, newValue) match {
        case (Left(oldValue), Left(newValue)) =>
          val valuePatch = left.diff(oldValue, newValue)
          if (valuePatch == left.empty) Empty()
          else UpdateLeft(valuePatch)
        case (Right(oldValue), Right(newValue)) =>
          val valuePatch = right.diff(oldValue, newValue)
          if (valuePatch == right.empty) Empty()
          else UpdateRight(valuePatch)
        case (Left(_), Right(newValue)) =>
          SetRight(newValue)
        case (Right(_), Left(newValue)) =>
          SetLeft(newValue)
      }

    /**
     * Constructs an empty or patch.
     */
    def empty[Value, Value2, Patch, Patch2]: OrPatch[Value, Value2, Patch, Patch2] =
      Empty()

    private final case class AndThen[Value, Value2, Patch, Patch2](
      first: OrPatch[Value, Value2, Patch, Patch2],
      second: OrPatch[Value, Value2, Patch, Patch2]
    ) extends OrPatch[Value, Value2, Patch, Patch2]
    private final case class Empty[Value, Value2, Patch, Patch2]() extends OrPatch[Value, Value2, Patch, Patch2]
    private final case class SetLeft[Value, Value2, Patch, Patch2](value: Value)
        extends OrPatch[Value, Value2, Patch, Patch2]
    private final case class SetRight[Value, Value2, Patch, Patch2](value: Value2)
        extends OrPatch[Value, Value2, Patch, Patch2]
    private final case class UpdateLeft[Value, Value2, Patch, Patch2](patch: Patch)
        extends OrPatch[Value, Value2, Patch, Patch2]
    private final case class UpdateRight[Value, Value2, Patch, Patch2](patch: Patch2)
        extends OrPatch[Value, Value2, Patch, Patch2]
  }

  /**
   * A patch which describes updates to a set of values.
   */
  sealed trait SetPatch[A] { self =>
    import SetPatch._

    /**
     * Applies a set patch to a set of values to produce a new set of values
     * which represents the original set of values updated with the changes
     * described by this patch.
     */
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

    /**
     * Combines two set patches to produce a new set patch that describes
     * applying their changes sequentially.
     */
    def combine(that: SetPatch[A]): SetPatch[A] =
      AndThen(self, that)
  }

  object SetPatch {

    /**
     * Constructs a set patch from a new set of values.
     */
    def diff[A](oldValue: Set[A], newValue: Set[A]): SetPatch[A] =
      if (oldValue == newValue) Empty()
      else {
        val (removed, patch) = newValue.foldLeft[(Set[A], SetPatch[A])](oldValue -> empty) { case ((set, patch), a) =>
          if (set.contains(a)) (set - a, patch)
          else (set, patch.combine(Add(a)))
        }
        removed.foldLeft(patch)((patch, a) => patch.combine(Remove(a)))
      }

    /**
     * Constructs an empty set patch.
     */
    def empty[A]: SetPatch[A] =
      Empty()

    private final case class Add[A](value: A)                                    extends SetPatch[A]
    private final case class AndThen[A](first: SetPatch[A], second: SetPatch[A]) extends SetPatch[A]
    private final case class Empty[A]()                                          extends SetPatch[A]
    private final case class Remove[A](value: A)                                 extends SetPatch[A]
  }
}
