/*
 * Copyright 2022-2023 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

import java.time.format.DateTimeFormatter

/**
 * A ConfigProvider is a service that provides configuration given a description
 * of the structure of that configuration.
 */
trait ConfigProvider { self =>

  /**
   * Loads the specified configuration, or fails with a config error.
   */
  def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A]

  /**
   * Returns a new config provider that will automatically nest all
   * configuration under the specified property name. This can be utilized to
   * aggregate separate configuration sources that are all required to load a
   * single configuration value.
   */
  final def nested(name: String): ConfigProvider =
    ConfigProvider.fromFlat(self.flatten.nested(name))

  /**
   * Returns a new config provider that preferentially loads configuration data
   * from this one, but which will fall back to the specified alterate provider
   * if there are any issues loading the configuration from this provider.
   */
  final def orElse(that: ConfigProvider): ConfigProvider =
    ConfigProvider.fromFlat(self.flatten.orElse(that.flatten))

  /**
   * Flattens this config provider into a simplified config provider that knows
   * only how to deal with flat (key/value) properties.
   */
  def flatten: ConfigProvider.Flat =
    new ConfigProvider.Flat {
      def load[A](path: Chunk[String], config: Config.Primitive[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]] =
        ZIO.die(new NotImplementedError("ConfigProvider#flatten"))
      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
        ZIO.die(new NotImplementedError("ConfigProvider#flatten"))
    }
}
object ConfigProvider {

  /**
   * A simplified config provider that knows only how to deal with flat
   * (key/value) properties. Because these providers are common, there is
   * special support for implementing them.
   */
  trait Flat { self =>
    def load[A](path: Chunk[String], config: Config.Primitive[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]]

    def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]]

    final def orElse(that: Flat): Flat =
      new Flat {
        def load[A](path: Chunk[String], config: Config.Primitive[A])(implicit
          trace: Trace
        ): IO[Config.Error, Chunk[A]] =
          self.load(path, config).catchAll(e1 => that.load(path, config).catchAll(e2 => ZIO.fail(e1 || e2)))
        def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
          for {
            l <- self.enumerateChildren(path).either
            r <- that.enumerateChildren(path).either
            result <- (l, r) match {
                        case (Left(e1), Left(e2)) => ZIO.fail(e1 && e2)
                        case (Left(e1), Right(_)) => ZIO.fail(e1)
                        case (Right(_), Left(e2)) => ZIO.fail(e2)
                        case (Right(l), Right(r)) => ZIO.succeed(l ++ r)
                      }
          } yield result
      }

    final def nested(name: String): Flat =
      new Flat {
        def load[A](path: Chunk[String], config: Config.Primitive[A])(implicit
          trace: Trace
        ): IO[Config.Error, Chunk[A]] =
          self.load(name +: path, config)
        def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
          self.enumerateChildren(name +: path)
      }
  }
  object Flat {
    object util {
      def splitPathString(text: String, escapedDelim: String): Chunk[String] =
        Chunk.fromArray(text.split("\\s*" + escapedDelim + "\\s*"))

      def parsePrimitive[A](
        text: String,
        path: Chunk[String],
        name: String,
        primitive: Config.Primitive[A],
        escapedDelim: String
      ): IO[Config.Error, Chunk[A]] = {
        val name    = path.lastOption.getOrElse("<unnamed>")
        val unsplit = primitive == Config.Secret

        if (unsplit) ZIO.fromEither(primitive.parse(text)).map(Chunk(_))
        else
          ZIO
            .foreach(splitPathString(text, escapedDelim))(s => ZIO.fromEither(primitive.parse(s.trim)))
            .mapError(_.prefixed(path))
      }
    }
  }

  /**
   * A config provider layer that loads configuration from interactive console
   * prompts, using the default Console service.
   */
  lazy val console: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(consoleProvider)

  lazy val consoleProvider: ConfigProvider =
    consoleProvider()

  def consoleProvider(seqDelim: String = ","): ConfigProvider =
    fromFlat(new Flat {
      def load[A](path: Chunk[String], primitive: Config.Primitive[A])(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] = {
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = primitive.description
        val sourceError = (e: Throwable) =>
          Config.Error.SourceUnavailable(
            path :+ name,
            "There was a problem reading configuration from the console",
            Cause.fail(e)
          )
        val isText = primitive == Config.Text || primitive == Config.Secret

        for {
          _       <- Console.printLine(s"Please enter ${description} for property ${name}:").mapError(sourceError)
          line    <- Console.readLine.mapError(sourceError)
          results <- Flat.util.parsePrimitive(line, path, name, primitive, ",")
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
        (for {
          _    <- Console.printLine(s"Enter the keys you want for the table ${path}, separated by commas:")
          keys <- Console.readLine.map(_.split(",").map(_.trim))
        } yield keys.toSet).mapError(e =>
          Config.Error
            .SourceUnavailable(path, "There was a problem reading configuration from the console", Cause.fail(e))
        )
    })

  lazy val defaultProvider: ConfigProvider =
    envProvider.orElse(propsProvider)

  /**
   * A config provider layer that loads configuration from environment
   * variables, using the default System service.
   */
  lazy val env: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(envProvider)

  /**
   * A config provider that loads configuration from environment variables,
   * using the default System service.
   */
  lazy val envProvider: ConfigProvider =
    fromEnv()

  /**
   * Constructs a ConfigProvider that loads configuration information from
   * environment variables, using the default System service and the specified
   * delimiter strings.
   */
  def fromEnv(pathDelim: String = "_", seqDelim: String = ","): ConfigProvider =
    fromFlat(new Flat {
      val sourceUnavailable = (path: Chunk[String]) =>
        (e: Throwable) =>
          Config.Error.SourceUnavailable(path, "There was a problem reading environment variables", Cause.fail(e))

      val escapedSeqDelim                                     = java.util.regex.Pattern.quote(seqDelim)
      val escapedPathDelim                                    = java.util.regex.Pattern.quote(pathDelim)
      def makePathString(path: Chunk[String]): String         = path.mkString(pathDelim).toUpperCase
      def unmakePathString(pathString: String): Chunk[String] = Chunk.fromArray(pathString.split(escapedPathDelim))

      def load[A](path: Chunk[String], primitive: Config.Primitive[A])(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(path)
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = primitive.description

        for {
          valueOpt <- zio.System.env(pathString).mapError(sourceUnavailable(path))
          value <-
            ZIO
              .fromOption(valueOpt)
              .mapError(_ => Config.Error.MissingData(path, s"Expected ${pathString} to be set in the environment"))
          results <- Flat.util.parsePrimitive(value, path, name, primitive, escapedSeqDelim)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
        zio.System.envs.map { envs =>
          val keyPaths = Chunk.fromIterable(envs.keys).map(_.toUpperCase).map(unmakePathString)

          keyPaths.filter(_.startsWith(path)).map(_.drop(path.length).take(1)).flatten.toSet

        }.mapError(sourceUnavailable(path))
    })

  /**
   * Constructs a new ConfigProvider from a key/value (flat) provider, where
   * nesting is embedded into the string keys.
   */
  def fromFlat(flat: Flat): ConfigProvider =
    new ConfigProvider {
      import Config._

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

      def loop[A](prefix: Chunk[String], config: Config[A])(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] =
        config match {
          case fallback: Fallback[A] =>
            loop(prefix, fallback.first).catchAll(e1 =>
              loop(prefix, fallback.second).catchAll(e2 => ZIO.fail(e1 || e2))
            )

          case Described(config, _) => loop(prefix, config)

          case Lazy(thunk) => loop(prefix, thunk())

          case MapOrFail(original, f) =>
            loop(prefix, original).flatMap { as =>
              ZIO.foreach(as)(a => ZIO.fromEither(f(a)).mapError(_.prefixed(prefix)))
            }

          case Sequence(config) =>
            loop(prefix, config).map(Chunk(_))

          case Nested(name, config) =>
            loop(prefix ++ Chunk(name), config)

          case table: Table[valueType] =>
            import table.valueConfig
            for {
              keys   <- flat.enumerateChildren(prefix)
              values <- ZIO.foreach(Chunk.fromIterable(keys))(key => loop(prefix ++ Chunk(key), valueConfig))
            } yield
              if (values.isEmpty) Chunk(Map.empty[String, valueType])
              else values.transpose.map(values => keys.zip(values).toMap)

          case zipped: Zipped[leftType, rightType, c] =>
            import zipped.{left, right, zippable}
            for {
              l <- loop(prefix, left).either
              r <- loop(prefix, right).either
              result <- (l, r) match {
                          case (Left(e1), Left(e2)) => ZIO.fail(e1 && e2)
                          case (Left(e1), Right(_)) => ZIO.fail(e1)
                          case (Right(_), Left(e2)) => ZIO.fail(e2)
                          case (Right(l), Right(r)) =>
                            val path = prefix.mkString(".")

                            def lfail(index: Int): Either[Config.Error, leftType] =
                              Left(
                                Config.Error.MissingData(
                                  prefix,
                                  s"The element at index ${index} in a sequence at ${path} was missing"
                                )
                              )

                            def rfail(index: Int): Either[Config.Error, rightType] =
                              Left(
                                Config.Error.MissingData(
                                  prefix,
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
            ZIO.fail(Config.Error.MissingData(prefix, message))

          case primitive: Primitive[A] =>
            for {
              vs <- flat.load(prefix, primitive)
              result <- if (vs.isEmpty)
                          ZIO.fail(primitive.missingError(prefix.lastOption.getOrElse("<n/a>")))
                        else ZIO.succeed(vs)
            } yield result
        }

      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        loop(Chunk.empty, config).flatMap { chunk =>
          chunk.headOption match {
            case Some(a) => ZIO.succeed(a)
            case _ =>
              ZIO.fail(Config.Error.MissingData(Chunk.empty, s"Expected a single value having structure ${config}"))
          }
        }

      override def flatten: Flat = flat
    }

  /**
   * Constructs a ConfigProvider using a map and the specified delimiter string,
   * which determines how to split the keys in the map into path segments.
   */
  def fromMap(map: Map[String, String], pathDelim: String = ".", seqDelim: String = ","): ConfigProvider =
    fromFlat(new Flat {
      val escapedSeqDelim                                     = java.util.regex.Pattern.quote(seqDelim)
      val escapedPathDelim                                    = java.util.regex.Pattern.quote(pathDelim)
      def makePathString(path: Chunk[String]): String         = path.mkString(pathDelim)
      def unmakePathString(pathString: String): Chunk[String] = Chunk.fromArray(pathString.split(escapedPathDelim))

      def load[A](path: Chunk[String], primitive: Config.Primitive[A])(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(path)
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = primitive.description
        val valueOpt    = map.get(pathString)

        for {
          value <- ZIO
                     .fromOption(valueOpt)
                     .mapError(_ => Config.Error.MissingData(path, s"Expected ${pathString} to be set in properties"))
          results <- Flat.util.parsePrimitive(value, path, name, primitive, escapedSeqDelim)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
        ZIO.succeed {
          val keyPaths = Chunk.fromIterable(map.keys).map(unmakePathString)

          keyPaths.filter(_.startsWith(path)).map(_.drop(path.length).take(1)).flatten.toSet
        }
    })

  /**
   * Constructs a ConfigProvider that loads configuration information from
   * system properties, using the default System service and the specified
   * delimiter strings.
   */
  def fromProps(pathDelim: String = ".", seqDelim: String = ","): ConfigProvider =
    fromFlat(new Flat {
      val sourceUnavailable = (path: Chunk[String]) =>
        (e: Throwable) => Config.Error.SourceUnavailable(path, "There was a problem reading properties", Cause.fail(e))

      val escapedSeqDelim                                     = java.util.regex.Pattern.quote(seqDelim)
      val escapedPathDelim                                    = java.util.regex.Pattern.quote(pathDelim)
      def makePathString(path: Chunk[String]): String         = path.mkString(pathDelim)
      def unmakePathString(pathString: String): Chunk[String] = Chunk.fromArray(pathString.split(escapedPathDelim))

      def load[A](path: Chunk[String], primitive: Config.Primitive[A])(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(path)
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = primitive.description

        for {
          valueOpt <- zio.System.property(pathString).mapError(sourceUnavailable(path))
          value <- ZIO
                     .fromOption(valueOpt)
                     .mapError(_ => Config.Error.MissingData(path, s"Expected ${pathString} to be set in properties"))
          results <- Flat.util.parsePrimitive(value, path, name, primitive, escapedSeqDelim)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Set[String]] =
        zio.System.properties.map { envs =>
          val keyPaths = Chunk.fromIterable(envs.keys).map(unmakePathString)

          keyPaths.filter(_.startsWith(path)).map(_.drop(path.length).take(1)).flatten.toSet
        }.mapError(sourceUnavailable(path))
    })

  /**
   * A config provider layer that loads configuration from system properties,
   * using the default System service.
   */
  lazy val props: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(propsProvider)

  /**
   * A configuration provider that loads configuration from system properties,
   * using the default System service.
   */
  lazy val propsProvider: ConfigProvider =
    fromProps()

  /**
   * The tag that describes the ConfigProvider service.
   */
  lazy val tag: Tag[ConfigProvider] = Tag[ConfigProvider]
}
