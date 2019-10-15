package zio.stream

import zio._
import ZStream.Pull

private[stream] sealed abstract class Structure[-R, +E, +A] { self =>
  import Structure._

  def ++[R1 <: R, E1 >: E, A1 >: A](other: => Structure[R1, E1, A1]): Structure[R1, E1, A1] =
    Concat(self, () => other)

  def map[B](f: A => B): Structure[R, E, B] =
    self match {
      case Iterator(iter)          => Structure.Iterator(iter.map(_.map(f)))
      case Concat(hd, tl)          => Structure.Concat(hd.map(f), () => tl().map(f))
      case s: FlatMap[R, E, a0, A] => Structure.FlatMap(s.fa, (el: a0) => s.f(el).map(f))
      case Effect(zio)             => Structure.Effect(zio.map(f))
    }

  def mapM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): Structure[R1, E1, B] =
    self match {
      case Concat(hd, tl)          => Concat(hd.mapM(f), () => tl().mapM(f))
      case Iterator(iter)          => Iterator(iter.map(_.flatMap(f(_).mapError(Some(_)))))
      case s: FlatMap[R, E, a0, A] => Structure.FlatMap(s.fa, (el: a0) => s.f(el).mapM(f))
      case Effect(zio)             => Structure.Effect(zio.flatMap(f))
    }

  def flatMap[R1 <: R, E1 >: E, B](f: A => Structure[R1, E1, B]): Structure[R1, E1, B] =
    FlatMap[R1, E1, A, B](self, f)

  def process: ZManaged[R, E, Pull[R, E, A]] =
    self match {
      case Iterator(iter) => iter
      case _ =>
        for {
          refs <- (Ref.make[Structure[R, E, _]](self) <*>
                   Ref.make[Pull[R, E, _]](Pull.end) <*>
                   Ref.make[Exit[_, _] => URIO[R, _]](_ => UIO.unit) <*>
                   Ref.make[List[(Pull[R, E, _], Exit[_, _] => URIO[R, _], Structure[R, E, _])]](Nil)).toManaged_
          (currStruct <*> currPull <*> currFin <*> stack) = refs
          pull <- ZManaged.reserve(
                   Reservation(
                     open(self, currFin, currPull).as(pull(currStruct, currFin, currPull, stack)),
                     e =>
                       for {
                         fs    <- currFin.get.zipWith(stack.get.map(_.map(_._2)))(_ :: _)
                         exits <- ZIO.foreach(fs)(_(e).run)
                         _     <- ZIO.done(Exit.collectAll(exits).getOrElse(Exit.unit))
                       } yield ()
                   )
                 )
        } yield pull
    }

  private[this] def open(
    struct: Structure[R, E, Any],
    currFin: Ref[Exit[_, _] => URIO[R, _]],
    currPull: Ref[Pull[R, E, _]]
  ): ZIO[R, E, Unit] =
    ZIO.uninterruptibleMask { restore =>
      val proc = struct match {
        case Iterator(iter) => iter
        case FlatMap(fa, _) => fa.process
        case Effect(zio)    => ZManaged.succeed(zio.mapError(Some(_)))
        case Concat(hd, _)  => hd.process
      }

      for {
        reservation <- proc.reserve
        _           <- currFin.set(reservation.release)
        _           <- restore(reservation.acquire.tap(currPull.set))
      } yield ()
    }

  private[this] def pull(
    currStruct: Ref[Structure[R, E, Any]],
    currFin: Ref[Exit[_, _] => URIO[R, _]],
    currPull: Ref[Pull[R, E, Any]],
    stack: Ref[List[(Pull[R, E, _], Exit[_, _] => URIO[R, _], Structure[R, E, _])]]
  ): Pull[R, E, A] =
    currPull.get.flatten.foldM(
      {
        case e @ Some(_) => ZIO.fail(e)
        case None =>
          ZIO.uninterruptibleMask {
            restore =>
              currStruct.get.flatMap {
                case Concat(_, tl) =>
                  val tl0 = tl()
                  currFin.get.flatMap(_.apply(Exit.interrupt)) *>
                    currStruct.set(tl0) *> restore(open(tl0, currFin, currPull).mapError(Some(_)))
                case _ =>
                  stack.get.flatMap {
                    case Nil => Pull.end
                    case (p, fin, s) :: rest =>
                      currFin.get.flatMap(_.apply(Exit.interrupt)) *>
                        stack.set(rest) *>
                        currPull.set(p) *>
                        currFin.set(fin) *>
                        currStruct.set(s)
                  }
              }
          } *> pull(currStruct, currFin, currPull, stack)
      },
      v =>
        currStruct.get.flatMap {
          case Iterator(_) | Concat(_, _) =>
            UIO.succeed(v.asInstanceOf[A])

          case Effect(_) =>
            currPull.set(Pull.end).as(v.asInstanceOf[A])

          case FlatMap(Effect(_), k) =>
            // This combination is special cased because with a stream represented
            // by Effect, we don't have to push anything onto the stack when switching
            // to the stream produced by k for the resulting element. This is because
            // Effect represents a stream with one element that has no finalizer, so
            // there's no reason for us to re-visit it after evaluation.
            ZIO.uninterruptibleMask { restore =>
              val next = k.asInstanceOf[Any => Structure[R, E, Any]](v)

              currStruct.set(next) *>
                restore(open(next, currFin, currPull).mapError(Some(_)))
            } *> pull(currStruct, currFin, currPull, stack)

          case FlatMap(_, k) =>
            ZIO.uninterruptibleMask { restore =>
              val next = k.asInstanceOf[Any => Structure[R, E, Any]](v)

              (for {
                st  <- stack.get
                p   <- currPull.get
                fin <- currFin.get
                s   <- currStruct.get
                _   <- stack.set((p, fin, s) :: st)
                _   <- currStruct.set(next)
              } yield ()) *> restore(open(next, currFin, currPull).mapError(Some(_)))
            } *> pull(currStruct, currFin, currPull, stack)
        }
    )
}

private[stream] object Structure {
  final case class Iterator[-R, +E, +A](iter: ZManaged[R, E, Pull[R, E, A]])                  extends Structure[R, E, A]
  final case class Concat[-R, +E, +A](hd: Structure[R, E, A], tl: () => Structure[R, E, A])   extends Structure[R, E, A]
  final case class FlatMap[-R, +E, A, +B](fa: Structure[R, E, A], f: A => Structure[R, E, B]) extends Structure[R, E, B]
  final case class Effect[-R, +E, A](zio: ZIO[R, E, A])                                       extends Structure[R, E, A]
}
