/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

  final def orElse(that: ConfigProvider): ConfigProvider =
    new ConfigProvider {
      def load[A](config: Config[A])(implicit trace: Trace): IO[Config.Error, A] =
        self.load(config).orElse(that.load(config))
    }
}
object ConfigProvider {
  private val localDateTime  = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  private val localDate      = DateTimeFormatter.ISO_LOCAL_DATE
  private val localTime      = DateTimeFormatter.ISO_LOCAL_TIME
  private val offsetDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME
  private val offsetDate     = DateTimeFormatter.ISO_OFFSET_DATE
  private val offsetTime     = DateTimeFormatter.ISO_OFFSET_TIME

  /**
   * A simplified config provider that knows only how to deal with flat 
   * (key/value) properties. Because these providers are common, there 
   * is special support for implementing them.
   */
  trait Flat {
    def load[A](path: Chunk[String], config: Config.Atom[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]]

    def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Chunk[String]]
  }
  object Flat {
    object util {
      def parseAtom[A](
        text: String,
        path: Chunk[String],
        name: String,
        atom: Config.Atom[A]
      ): IO[Config.Error, Chunk[A]] = {
        val name   = path.lastOption.getOrElse("<unnamed>")
        val isText = atom == Config.Text || atom == Config.Secret

        if (isText) ZIO.fromEither(atom.parse(text)).map(Chunk(_))
        else
          ZIO
            .foreach(Chunk.fromArray(text.split(",")))(s => ZIO.fromEither(atom.parse(s.trim)))
            .mapError(_.prefixed(path))
      }
    }
  }

  /**
   * A config provider layer that loads configuration from interactive console
   * prompts, using the default Console service.
   */
  val console: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(consoleProvider)

  val consoleProvider: ConfigProvider =
    fromFlat(new Flat {
      def load[A](path: Chunk[String], atom: Config.Atom[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]] = {
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = atom.description
        val sourceError = (e: Throwable) =>
          Config.Error.SourceUnavailable(
            path :+ name,
            "There was a problem reading configuration from the console",
            Cause.fail(e)
          )
        val isText = atom == Config.Text || atom == Config.Secret

        for {
          _       <- Console.printLine(s"Please enter ${description} for property ${name}:").mapError(sourceError)
          line    <- Console.readLine.mapError(sourceError)
          results <- Flat.util.parseAtom(line, path, name, atom)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Chunk[String]] =
        (for {
          _    <- Console.printLine(s"Enter the keys you want for the table ${path}, separated by commas:")
          keys <- Console.readLine.map(_.split(",").map(_.trim))
        } yield Chunk.fromIterable(keys)).mapError(e =>
          Config.Error
            .SourceUnavailable(path, "There was a problem reading configuration from the console", Cause.fail(e))
        )
    })

  val defaultProvider: ConfigProvider =
    envProvider.orElse(propsProvider)

  /**
   * A config provider that loads configuration from environment
   * variables, using the default System service.
   */
  val envProvider: ConfigProvider =
    fromFlat(new Flat {
      val sourceUnavailable = (path: Chunk[String]) =>
        (e: Throwable) =>
          Config.Error.SourceUnavailable(path, "There was a problem reading environment variables", Cause.fail(e))

      def makePathString(path: Chunk[String]): String = path.mkString("_").toUpperCase

      def load[A](path: Chunk[String], atom: Config.Atom[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(path)
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = atom.description

        for {
          valueOpt <- zio.System.env(pathString).mapError(sourceUnavailable(path))
          value <-
            ZIO
              .fromOption(valueOpt)
              .mapError(_ => Config.Error.MissingData(path, s"Expected ${pathString} to be set in the environment"))
          results <- Flat.util.parseAtom(value, path, name, atom)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Chunk[String]] =
        zio.System.envs.map { envs =>
          val pathString = makePathString(path)
          val keyStrings = Chunk.fromIterable(envs.keys).map(_.toUpperCase)

          keyStrings.filter(_.startsWith(pathString))
        }.mapError(sourceUnavailable(path))
    })

  /**
   * A config provider layer that loads configuration from environment
   * variables, using the default System service.
   */
  val env: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(envProvider)

  /**
    * Constructs a new ConfigProvider from a key/value (flat) provider, where 
    * nesting is embedded into the string keys.
    */
  def fromFlat(flat: Flat): ConfigProvider =
    new ConfigProvider {
      import Config._

      def extend[A, B](leftDef: A, rightDef: B)(left: Chunk[A], right: Chunk[B]): (Chunk[A], Chunk[B]) =
        if (left.length < right.length) {
          (left ++ Chunk.fill(right.length - left.length)(leftDef), right)
        } else if (right.length < left.length) {
          (left, right ++ Chunk.fill(left.length - right.length)(rightDef))
        } else (left, right)

      def loop[A](prefix: Chunk[String], config: Config[A], isEmptyOk: Boolean)(implicit
        trace: Trace
      ): IO[Config.Error, Chunk[A]] =
        config match {
          case Fallback(first, second) =>
            loop(prefix, first, isEmptyOk).catchAll(e1 =>
              loop(prefix, second, isEmptyOk).catchAll(e2 => ZIO.fail(e1 || e2))
            )

          case Described(config, _) => loop(prefix, config, isEmptyOk)

          case Lazy(thunk) => loop(prefix, thunk(), isEmptyOk)

          case MapOrFail(original, f) =>
            loop(prefix, original, isEmptyOk).flatMap(as => ZIO.foreach(as)(a => ZIO.fromEither(f(a))))

          case Sequence(config) =>
            loop(prefix, config, true).map(Chunk(_))

          case Nested(name, config) =>
            loop(prefix ++ Chunk(name), config, isEmptyOk)

          case Table(valueConfig) =>
            for {
              keys   <- flat.enumerateChildren(prefix)
              values <- ZIO.foreach(keys)(key => loop(prefix ++ Chunk(key), valueConfig, isEmptyOk))
            } yield values.map(values => keys.zip(values).toMap)

          case zipped: Zipped[leftType, rightType, c] =>
            import zipped.{left, right, zippable}
            for {
              l <- loop(prefix, left, isEmptyOk).either
              r <- loop(prefix, right, isEmptyOk).either
              result <- (l, r) match {
                          case (Left(e1), Left(e2)) => ZIO.fail(e1 && e2)
                          case (Left(e1), Right(_)) => ZIO.fail(e1)
                          case (Right(_), Left(e2)) => ZIO.fail(e2)
                          case (Right(l), Right(r)) =>
                            lazy val lfail: Config.Error =
                              Config.Error.MissingData(prefix, "An element in a sequence was missing")
                            lazy val rfail: Config.Error =
                              Config.Error.MissingData(prefix, "An element in a sequence was missing")
                            val (ls, rs) = extend[IO[Config.Error, leftType], IO[Config.Error, rightType]](
                              ZIO.fail(lfail),
                              ZIO.fail(rfail)
                            )(l.map(ZIO.succeed(_)), r.map(ZIO.succeed(_)))

                            ZIO.foreach(ls.zip(rs)) { case (l, r) => l.zipWith(r)(zippable.zip(_, _)) }
                        }
            } yield result

          case atom: Atom[A] =>
            for {
              vs <- flat.load(prefix, atom)
              result <- if (vs.isEmpty && !isEmptyOk) ZIO.fail(atom.missingError(prefix.lastOption.getOrElse("<n/a>")))
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
    }

  /**
    * Constructs a ConfigProvider using a map and the specified delimiter 
    * string, which determines how to split the keys in the map into 
    * path segments.
    */
  def fromMap(map: Map[String, String], pathDelim: String = "."): ConfigProvider =
    fromFlat(new Flat {
      def makePathString(path: Chunk[String]): String = path.mkString(pathDelim).toLowerCase

      def load[A](path: Chunk[String], atom: Config.Atom[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(path)
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = atom.description
        val valueOpt    = map.get(pathString)

        for {
          value <- ZIO
                     .fromOption(valueOpt)
                     .mapError(_ => Config.Error.MissingData(path, s"Expected ${pathString} to be set in properties"))
          results <- Flat.util.parseAtom(value, path, name, atom)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Chunk[String]] =
        ZIO.succeed {
          val pathString = makePathString(path)
          val keyStrings = Chunk.fromIterable(map.keys).map(_.toUpperCase)

          keyStrings.filter(_.startsWith(pathString))
        }
    })

  /**
   * A config provider layer that loads configuration from system properties,
   * using the default System service.
   */
  val props: ZLayer[Any, Nothing, ConfigProvider] =
    ZLayer.succeed(propsProvider)

  /**
    * A configuration provider that loads configuration from system properties,
    * using the default System service.
    */
  val propsProvider: ConfigProvider =
    fromFlat(new Flat {
      val sourceUnavailable = (path: Chunk[String]) =>
        (e: Throwable) => Config.Error.SourceUnavailable(path, "There was a problem reading properties", Cause.fail(e))

      def makePathString(path: Chunk[String]): String = path.mkString(".").toLowerCase

      def load[A](path: Chunk[String], atom: Config.Atom[A])(implicit trace: Trace): IO[Config.Error, Chunk[A]] = {
        val pathString  = makePathString(path)
        val name        = path.lastOption.getOrElse("<unnamed>")
        val description = atom.description

        for {
          valueOpt <- zio.System.property(pathString).mapError(sourceUnavailable(path))
          value <- ZIO
                     .fromOption(valueOpt)
                     .mapError(_ => Config.Error.MissingData(path, s"Expected ${pathString} to be set in properties"))
          results <- Flat.util.parseAtom(value, path, name, atom)
        } yield results
      }

      def enumerateChildren(path: Chunk[String])(implicit trace: Trace): IO[Config.Error, Chunk[String]] =
        zio.System.properties.map { envs =>
          val pathString = makePathString(path)
          val keyStrings = Chunk.fromIterable(envs.keys).map(_.toUpperCase)

          keyStrings.filter(_.startsWith(pathString))
        }.mapError(sourceUnavailable(path))
    })

  /**
    * The tag that describes the ConfigProvider service.
    */
  val tag: Tag[ConfigProvider] = Tag[ConfigProvider]
}
