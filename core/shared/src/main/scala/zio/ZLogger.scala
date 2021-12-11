package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace
import izumi.reflect.macrortti.LightTypeTag

trait ZLogger[-Message, +Output] { self =>
  def apply(
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message: () => Message,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans: List[LogSpan],
    location: ZTraceElement,
    annotations: LogAnnotations
  ): Output

  /**
   * Combines this logger with the specified logger to produce a new logger that
   * logs to both this logger and that logger.
   */
  def ++[M <: Message, O](
    that: ZLogger[M, O]
  )(implicit zippable: Zippable[Output, O]): ZLogger[M, zippable.Out] =
    new ZLogger[M, zippable.Out] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => M,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        location: ZTraceElement,
        annotations: LogAnnotations
      ): zippable.Out =
        zippable.zip(
          self(trace, fiberId, logLevel, message, context, spans, location, annotations),
          that(trace, fiberId, logLevel, message, context, spans, location, annotations)
        )
    }

  def +>[M <: Message, O](that: ZLogger[M, O]): ZLogger[M, O] = (self ++ that).map(_._2)

  def <+[M <: Message](that: ZLogger[M, Any]): ZLogger[M, Output] = (self ++ that).map(_._1)

  final def contramap[Message1](f: Message1 => Message): ZLogger[Message1, Output] =
    new ZLogger[Message1, Output] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message1,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        location: ZTraceElement,
        annotations: LogAnnotations
      ): Output = self(trace, fiberId, logLevel, () => f(message()), context, spans, location, annotations)
    }

  /**
   * Returns a version of this logger that only logs messages when the log level
   * satisfies the specified predicate.
   */
  final def filterLogLevel(f: LogLevel => Boolean): ZLogger[Message, Option[Output]] =
    new ZLogger[Message, Option[Output]] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        location: ZTraceElement,
        annotations: LogAnnotations
      ): Option[Output] =
        if (f(logLevel)) {
          Some(self(trace, fiberId, logLevel, message, context, spans, location, annotations))
        } else None
    }

  final def map[B](f: Output => B): ZLogger[Message, B] =
    new ZLogger[Message, B] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => Message,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        location: ZTraceElement,
        annotations: LogAnnotations
      ): B = f(self(trace, fiberId, logLevel, message, context, spans, location, annotations))
    }

  final def test(input: => Message): Output =
    apply(
      ZTraceElement.empty,
      FiberId.None,
      LogLevel.Info,
      () => input,
      Map(),
      Nil,
      ZTraceElement.empty,
      LogAnnotations.empty
    )

  final def toSet[Message1 <: Message](implicit tag: Tag[Message1]): ZLogger.Set[Message1, Output] =
    ZLogger.Set(self: ZLogger[Message1, Output])
}
object ZLogger {
  private[zio] val stringTag: LightTypeTag = Tag[String].tag
  private[zio] val causeTag: LightTypeTag  = Tag[Cause[Any]].tag

  import Predef.{Set => ScalaSet, _}

  /**
   * Represents a set of loggers, which operate on different input types, but
   * which all produce the same output type.
   */
  sealed abstract case class Set[+A, +B] private (map: Map[LightTypeTag, ZLogger[_, B]]) { self =>
    private var cache: Map[LightTypeTag, ScalaSet[ZLogger[_, Any]]] = Map()

    final def +[C, B1 >: B](that: ZLogger[C, B1])(implicit tag: Tag[C]): Set[A with C, B1] =
      new Set(map + (tag.tag -> that)) {}

    final def ++[A2, B1 >: B](that: Set[A2, B1]): Set[A with A2, B1] = new Set(self.map ++ that.map) {}

    final def add[C, B1 >: B](that: ZLogger[C, B1])(implicit tag: Tag[C]): Set[A with C, B1] = self.+[C, B1](that)

    final def addAll[A2, B1 >: B](that: Set[A2, B1]): Set[A with A2, B1] = self.++[A2, B1](that)

    final def filterLogLevel(f: LogLevel => Boolean): Set[A, Option[B]] =
      new Set[A, Option[B]](map.map { case (k, v) => k -> v.asInstanceOf[ZLogger[Any, B]].filterLogLevel(f) }) {}

    final def getAllDynamic(tag: LightTypeTag): ScalaSet[_ <: ZLogger[_, B]] = {
      val set = cache.get(tag)

      set match {
        case None =>
          val set = map.collect {
            case (t, logger) if tag <:< t => logger
          }.toSet

          cache = cache + (tag -> set.asInstanceOf[ScalaSet[ZLogger[_, Any]]])

          set

        case Some(set) => set.asInstanceOf[ScalaSet[ZLogger[_, B]]]
      }
    }

    final def getAll[C](implicit tag: Tag[C]): ScalaSet[_ <: ZLogger[C, B]] =
      getAllDynamic(tag.tag).asInstanceOf[ScalaSet[ZLogger[C, B]]]

    final def map[C](f: B => C): Set[A, C] =
      new Set[A, C](map.map { case (k, v) => k -> v.asInstanceOf[ZLogger[Any, B]].map(f) }) {}

    final def toLoggerWith[C, B1 >: B](b: B1)(f: (B1, B1) => B1)(implicit tag: Tag[C]): ZLogger[C, B1] =
      getAll[C].fold[ZLogger[C, B1]](ZLogger.succeed(b)) { case (acc, logger) =>
        (acc ++ logger).map(f.tupled)
      }
  }
  object Set {
    val default: Set[String & Cause[Any], String] =
      empty[String].add[String, String](defaultString).add[Cause[Any], String](defaultCause)

    def empty[A]: Set[Any, A] = new Set(Map()) {}

    def apply[A, B](logger: ZLogger[A, B])(implicit tag: Tag[A]): Set[A, B] = empty[B].add[A, B](logger)

    def apply[A, B, Z](logger1: ZLogger[A, Z], logger2: ZLogger[B, Z])(implicit
      tag1: Tag[A],
      tag2: Tag[B]
    ): Set[A & B, Z] =
      empty[Z].add[A, Z](logger1).add[B, Z](logger2)
  }

  val defaultString: ZLogger[String, String] = (
    trace: ZTraceElement,
    fiberId: FiberId,
    logLevel: LogLevel,
    message0: () => String,
    context: Map[FiberRef.Runtime[_], AnyRef],
    spans0: List[LogSpan],
    location: ZTraceElement,
    annotations: LogAnnotations
  ) => {
    val sb = new StringBuilder()

    val _ = context

    val now = java.time.Instant.now()

    val nowMillis = java.lang.System.currentTimeMillis()

    sb.append("timestamp=")
      .append(now.toString())
      .append(" level=")
      .append(logLevel.label)
      .append(" thread=#")
      .append(fiberId.threadName)
      .append(" message=\"")
      .append(message0())
      .append("\"")

    if (spans0.nonEmpty) {
      sb.append(" ")

      val it    = spans0.iterator
      var first = true

      while (it.hasNext) {
        if (first) {
          first = false
        } else {
          sb.append(" ")
        }

        it.next().unsafeRender(sb, nowMillis)
      }
    }

    trace match {
      case ZTraceElement(location, file, line) =>
        sb.append(" location=")

        appendQuoted(location, sb)

        sb.append(" file=")

        appendQuoted(file, sb)

        sb.append(" line=")
          .append(line)

      case _ =>
    }

    if (annotations.nonEmpty) {
      sb.append(" ")

      annotations.unsafeRender(sb)
    }

    sb.toString()
  }

  val defaultCause: ZLogger[Cause[Any], String] = defaultString.contramap(_.prettyPrint)

  /**
   * A logger that does nothing in response to logging events.
   */
  val none: ZLogger[Any, Unit] = new ZLogger[Any, Unit] {
    def apply(
      trace: ZTraceElement,
      fiberId: FiberId,
      logLevel: LogLevel,
      message: () => Any,
      context: Map[FiberRef.Runtime[_], AnyRef],
      spans: List[LogSpan],
      location: ZTraceElement,
      annotations: LogAnnotations
    ): Unit =
      ()
  }

  def simple[A, B](log: A => B): ZLogger[A, B] =
    new ZLogger[A, B] {
      def apply(
        trace: ZTraceElement,
        fiberId: FiberId,
        logLevel: LogLevel,
        message: () => A,
        context: Map[FiberRef.Runtime[_], AnyRef],
        spans: List[LogSpan],
        location: ZTraceElement,
        annotations: LogAnnotations
      ): B =
        log(message())
    }

  def succeed[A](a: => A): ZLogger[Any, A] = simple(_ => a)

  private def appendQuoted(label: String, sb: StringBuilder): StringBuilder = {
    if (label.indexOf(" ") < 0) sb.append(label)
    else sb.append("\"").append(label).append("\"")
    sb
  }
}
