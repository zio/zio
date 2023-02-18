package zio

package object config {

  def fromIndexedFlat(indexedFlat: IndexedFlat): ConfigProvider =
    new ConfigProvider {

      import Config._
      import IndexedFlat._

      def extend[A, B](leftDef: Int => A, rightDef: Int => B)(left: Chunk[A], right: Chunk[B]): (Chunk[A], Chunk[B]) = {
        val leftPad = Chunk.unfold(left.length) { index =>
          if (index >= right.length) None else Some(leftDef(index) -> (index + 1))
        }
        val rightPad = Chunk.unfold(right.length) { index =>
          if (index >= left.length) None else Some(rightDef(index) -> (index + 1))
        }

        val leftExtension  = left ++ leftPad
        val rightExtension = right ++ rightPad

        (leftExtension, rightExtension)
      }

      def loop[A](prefix: IndexedFlat.ConfigPath, config: Config[A], split: Boolean)(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] =
        config match {
          case fallback: Fallback[A] =>
            loop(prefix, fallback.first, split).catchAll(e1 =>
              if (fallback.condition(e1)) loop(prefix, fallback.second, split).catchAll(e2 => ZIO.fail(e1 || e2))
              else ZIO.fail(e1)
            )

          case Described(config, _) => loop(prefix, config, split)

          case Lazy(thunk) => loop(prefix, thunk(), split)

          case MapOrFail(original, f) =>
            loop(prefix, original, split).flatMap { as =>
              ZIO.foreach(as)(a => ZIO.fromEither(f(a)).mapError(_.prefixed(prefix.map(_.value))))
            }

          case Sequence(config) =>
            for {
              keys <- indexedFlat
                        .enumerateChildrenIndexed(prefix)
                        .map(set =>
                          set.toList.flatMap { chunk =>
                            chunk.headOption.toList
                          }
                        )

              values <-
                ZIO
                  .foreach(Chunk.fromIterable(keys.toSet)) { key =>
                    loop(prefix ++ Chunk(key), config, split = true)
                  }
                  .map(_.flatten)
            } yield Chunk(values)

          case Nested(name, config) =>
            loop(prefix ++ Chunk(KeyComponent.KeyName(name)), config, split)

          case table: Table[valueType] =>
            import table.valueConfig
            for {
              prefix <- ZIO.fromEither(ConfigPath.patch(indexedFlat.patch)(prefix))
              keys   <- indexedFlat.enumerateChildrenIndexed(prefix)
              values <- ZIO.foreach(Chunk.fromIterable(keys))(key => loop(prefix ++ key, valueConfig, split))
            } yield
              if (values.isEmpty) Chunk(Map.empty[String, valueType])
              else values.transpose.map(values => keys.map(_.last.value).zip(values).toMap)

          case zipped: Zipped[leftType, rightType, c] =>
            import zipped.{left, right, zippable}
            for {
              l <- loop(prefix, left, split).either
              r <- loop(prefix, right, split).either
              result <- (l, r) match {
                          case (Left(e1), Left(e2)) => ZIO.fail(e1 && e2)
                          case (Left(e1), Right(_)) => ZIO.fail(e1)
                          case (Right(_), Left(e2)) => ZIO.fail(e2)
                          case (Right(l), Right(r)) =>
                            val path = prefix.mkString(".")

                            def lfail(index: Int): Either[Config.Error, leftType] =
                              Left(
                                Config.Error.MissingData(
                                  prefix.map(_.value),
                                  s"The element at index ${index} in a sequence at ${path} was missing"
                                )
                              )

                            def rfail(index: Int): Either[Config.Error, rightType] =
                              Left(
                                Config.Error.MissingData(
                                  prefix.map(_.value),
                                  s"The element at index ${index} in a sequence at ${path} was missing"
                                )
                              )

                            val (ls, rs) = extend(lfail, rfail)(l.map(Right(_)), r.map(Right(_)))

                            ZIO.foreach(ls.zip(rs)) { case (l, r) =>
                              ZIO.fromEither(l).zipWith(ZIO.fromEither(r))(zippable.zip(_, _))
                            }
                        }
            } yield result

          case Constant(value) =>
            ZIO.succeed(Chunk(value))

          case Fail(message) =>
            ZIO.fail(Config.Error.MissingData(prefix.map(_.value), message))

          case primitive: Primitive[A] =>
            for {
              prefix <- ZIO.fromEither(ConfigPath.patch(indexedFlat.patch)(prefix))
              vs     <- indexedFlat.loadIndexed(prefix, primitive, split)
              result <-
                if (vs.isEmpty)
                  ZIO.fail(primitive.missingError(prefix.lastOption.fold("<n/a>")(_.value)))
                else ZIO.succeed(vs)
            } yield result
        }

      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        loop(Chunk.empty, config, false).flatMap { chunk =>
          chunk.headOption match {
            case Some(a) => ZIO.succeed(a)
            case _ =>
              ZIO.fail(Config.Error.MissingData(Chunk.empty, s"Expected a single value having structure ${config}"))
          }
        }

      override def flatten: IndexedFlat = indexedFlat
    }

  /**
   * Constructs a ConfigProvider using a map and the specified delimiter string,
   * which determines how to split the keys in the map into path segments.
   */
  def fromIndexedMap(map: Map[String, String], pathDelim: String = ".", seqDelim: String = ","): ConfigProvider =
    fromIndexedFlat(new IndexedFlat {
      val escapedSeqDelim  = java.util.regex.Pattern.quote(seqDelim)
      val escapedPathDelim = java.util.regex.Pattern.quote(pathDelim)

      def makePathString(path: Chunk[String]): String = path.mkString(pathDelim)

      def unmakePathString(pathString: String): Chunk[String] = Chunk.fromArray(pathString.split(escapedPathDelim))

      def loadIndexed[A](path: IndexedFlat.ConfigPath, primitive: Config.Primitive[A], split: Boolean)(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(IndexedFlat.ConfigPath.toPath(path))
        val name        = path.lastOption.getOrElse(IndexedFlat.KeyComponent.KeyName("<unnamed>"))
        val description = primitive.description
        val valueOpt    = map.get(pathString)

        for {
          value <- ZIO
                     .fromOption(valueOpt)
                     .mapError(_ =>
                       Config.Error.MissingData(path.map(_.value), s"Expected ${pathString} to be set in properties")
                     )
          results <- ConfigProvider.Flat.util
                       .parsePrimitive(value, path.map(_.value), name.value, primitive, escapedSeqDelim, split)
        } yield results
      }

      def enumerateChildrenIndexed(path: IndexedFlat.ConfigPath)(implicit
        trace: Trace
      ): IO[Config.Error, Set[IndexedFlat.ConfigPath]] =
        ZIO.succeed {
          val keyPaths: Chunk[IndexedFlat.ConfigPath] = Chunk
            .fromIterable(map.keys)
            .map(unmakePathString)
            .map(IndexedFlat.ConfigPath.fromPath)

          keyPaths.filter(_.startsWith(path)).map(_.drop(path.length).take(1)).toSet
        }
    })
}
