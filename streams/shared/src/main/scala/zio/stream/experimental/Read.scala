package zio.stream.experimental

import zio._

object Read {

  def chunked[R, E, I, O](f: Read[R, E, Option[I], O]): Read[R, E, Option[Chunk[I]], O] =
    ZIO.foreach_(_)(ZIO.foreach_(_)(i => f(Some(i)))) *> f(None)

  def foreach[R, E, I, O](f: Read[R, E, Option[I], O]): Read[R, E, Option[Iterable[I]], O] =
    ZIO.foreach_(_)(ZIO.foreach_(_)(i => f(Some(i)))) *> f(None)

  def sequence[R, E, I, O](f: Read[R, E, Option[I], Option[O]]): Read[R, E, Option[Iterable[I]], Option[Chunk[O]]] =
    _.fold[ZIO[R, E, Option[Chunk[O]]]](ZIO.none)(is =>
      ZRef
        .make(ChunkBuilder.make[O]())
        .flatMap(ZIO.foldLeft(is)(_)((z, i) => f(Some(i)).flatMap(o => z.update(b => o.fold(b)(b += _)).as(z))))
        .flatMap(_.get.map { b =>
          val o = b.result()
          if (o.isEmpty) None else Some(o)
        })
    )
}
