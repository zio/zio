package zio.stream

import zio._
import zio.test._
import zio.metrics.MetricLabel
import zio.internal.stacktracer.SourceLocation

object StreamLazinessSpec extends ZIOBaseSpec {
  def lazy1[A](name: String)(f: (=> A) => Any)(implicit t: Trace, sl: SourceLocation): Spec[Any, Nothing] =
    lazy2[A, Any](name)((a, _) => f(a))

  def lazy2[A, B](name: String)(f: (=> A, => B) => Any)(implicit t: Trace, sl: SourceLocation): Spec[Any, Nothing] =
    lazy3[A, B, Any](name)((a, b, _) => f(a, b))

  def lazy3[A, B, C](
    name: String
  )(f: (=> A, => B, => C) => Any)(implicit t: Trace, sl: SourceLocation): Spec[Any, Nothing] =
    test(name) {
      final class ArgumentNotLazy(n: Int) extends RuntimeException(s"Argument $n is not lazy")
      val _ = f(
        throw new ArgumentNotLazy(1),
        throw new ArgumentNotLazy(2),
        throw new ArgumentNotLazy(3)
      )
      assertCompletes
    }

  def spec =
    suite("StreamLazinessSpec")(
      suite("ZSink")(
        lazy1("die")(ZSink.die(_)),
        lazy1("fail")(ZSink.fail(_)),
        lazy1("failCause")(ZSink.failCause(_)),
        lazy1("succeed")(ZSink.succeed(_))
      ),
      suite("ZStream companion object")(
        lazy1[ZIO[Any, Nothing, Any]]("acquireReleaseWith")(e => ZStream.acquireReleaseWith(e)(_ => ZIO.unit)),
        lazy1[ZIO[Any, Nothing, Any]]("acquireReleaseExitWith")(e =>
          ZStream.acquireReleaseExitWith(e)((_, _) => ZIO.unit)
        ),
        lazy1("blocking")(ZStream.blocking(_)),
        lazy1("concatAll")(ZStream.concatAll(_)),
        lazy1("debug")(ZStream.debug(_)),
        lazy1("die")(ZStream.die(_)),
        lazy1("dieMessage")(ZStream.dieMessage(_)),
        lazy1("execute")(ZStream.execute(_)),
        lazy1("fail")(ZStream.fail(_)),
        lazy1("failCause")(ZStream.failCause(_)),
        lazy1("finalizer")(ZStream.finalizer(_)),
        lazy1("fromChunk")(ZStream.fromChunk(_)),
        lazy1("fromChunkHub")(ZStream.fromChunkHub(_)),
        lazy1("fromChunkHubScoped")(ZStream.fromChunkHubScoped(_)),
        lazy1("fromChunkHubWithShutdown")(ZStream.fromChunkHubWithShutdown(_)),
        lazy1("fromChunkHubScopedWithShutdown")(ZStream.fromChunkHubScopedWithShutdown(_)),
        lazy1("fromChunkQueue")(ZStream.fromChunkQueue(_)),
        lazy1("fromChunkQueueWithShutdown")(ZStream.fromChunkQueueWithShutdown(_)),
        lazy2("fromHub")(ZStream.fromHub(_, _)),
        lazy2("fromHubScoped")(ZStream.fromHubScoped(_, _)),
        lazy2("fromHubWithShutdown")(ZStream.fromHubWithShutdown(_, _)),
        lazy2("fromHubScopedWithShutdown")(ZStream.fromHubScopedWithShutdown(_, _)),
        lazy2("fromInputStream")(ZStream.fromInputStream(_, _)),
        lazy2("fromInputStreamZIO")(ZStream.fromInputStreamZIO(_, _)),
        lazy2("fromInputStreamScoped")(ZStream.fromInputStreamScoped(_, _)),
        lazy2("fromIterable")(ZStream.fromIterable(_, _)),
        lazy2("fromIterableZIO")(ZStream.fromIterableZIO(_, _)),
        lazy2("fromIterator")(ZStream.fromIterator(_, _)),
        lazy2("fromIteratorScoped")(ZStream.fromIteratorScoped(_, _)),
        lazy2("fromIteratorSucceed")(ZStream.fromIteratorSucceed(_, _)),
        lazy1("fromIteratorZIO")(ZStream.fromIteratorZIO(_)),
        lazy1("fromJavaIterator - iterator")(ZStream.fromJavaIterator(_)),
        lazy1("fromJavaIteratorScoped - iterator")(ZStream.fromJavaIteratorScoped(_)),
        lazy1("fromJavaIteratorSucceed - iterator")(ZStream.fromJavaIteratorSucceed(_)),
        lazy1("fromJavaIteratorZIO")(ZStream.fromJavaIteratorZIO(_)),
        lazy2("fromQueue")(ZStream.fromQueue(_, _)),
        lazy2("fromQueueWithShutdown")(ZStream.fromQueueWithShutdown(_, _)),
        lazy1("fromSchedule")(ZStream.fromSchedule(_)),
        lazy1("fromTPriorityQueue")(ZStream.fromTPriorityQueue(_)),
        lazy1("fromTQueue")(ZStream.fromTQueue(_)),
        lazy1("fromZIO")(ZStream.fromZIO(_)),
        lazy1("fromZIOOption")(ZStream.fromZIOOption(_)),
        lazy1("iterate")(e => ZStream.iterate(e)(identity)),
        lazy1("log")(ZStream.log(_)),
        lazy2[String, String]("logAnnotate")((a, b) => ZStream.logAnnotate(a, b)),
        lazy1[LogAnnotation]("logAnnotate - annotation")(e => ZStream.logAnnotate(e)),
        lazy1[Set[LogAnnotation]]("logAnnotate - annotations set")(e => ZStream.logAnnotate(e)),
        lazy1("logDebug")(ZStream.logDebug(_)),
        lazy1("logError")(ZStream.logError(_)),
        lazy1("logErrorCause")(ZStream.logErrorCause(_)),
        lazy1("logFatal")(ZStream.logFatal(_)),
        lazy1("logInfo")(ZStream.logInfo(_)),
        lazy1("logSpan")(ZStream.logSpan(_)),
        lazy1("logTrace")(ZStream.logTrace(_)),
        lazy1("logWarning")(ZStream.logWarning(_)),
        lazy2[Int, Int]("mergeAll")((n, b) => ZStream.mergeAll(n, b)()),
        lazy1[Int]("mergeAllUnbounded")(e => ZStream.mergeAllUnbounded(e)()),
        lazy1[String]("paginate")(e => ZStream.paginate(e)(s => (s, None))),
        lazy1[String]("paginateChunk")(e => ZStream.paginateChunk(e)(s => (Chunk(s), None))),
        lazy1[String]("paginateChunkZIO")(e => ZStream.paginateChunkZIO(e)(s => ZIO.succeed((Chunk(s), None)))),
        lazy1[String]("paginateZIO")(e => ZStream.paginateZIO(e)(s => ZIO.succeed((s, None)))),
        lazy1[ZStream[Unit, Nothing, Int]]("provideLayer")(s => ZStream.provideLayer(ZLayer.empty)(s)),
        lazy3("range")(ZStream.range(_, _, _)),
        lazy1("repeat")(ZStream.repeat(_)),
        lazy2("repeatWithSchedule")(ZStream.repeatWithSchedule(_, _)),
        lazy1("repeatZIO")(ZStream.repeatZIO(_)),
        lazy1("repeatZIOChunk")(ZStream.repeatZIOChunk(_)),
        lazy1("repeatZIOChunkOption")(ZStream.repeatZIOChunkOption(_)),
        lazy1("repeatZIOOption")(ZStream.repeatZIOOption(_)),
        lazy2("repeatZIOWithSchedule")(ZStream.repeatZIOWithSchedule(_, _)),
        lazy1("scoped")(ZStream.scoped[Any](_)),
        lazy1("succeed")(ZStream.succeed(_)),
        lazy1("suspend")(ZStream.suspend(_)),
        lazy2[String, String]("tagged(String, String)")((a, b) => ZStream.tagged(a, b)),
        lazy1[MetricLabel]("tagged(MetricLabel)")(e => ZStream.tagged(e)),
        lazy1[Set[MetricLabel]]("tagged(Set[MetricLabel])")(e => ZStream.tagged(e)),
        lazy1("tick")(ZStream.tick(_)),
        lazy1[Int]("unfold")(e => ZStream.unfold(e)(s => Some((s, s)))),
        lazy1[Int]("unfoldChunk")(e => ZStream.unfoldChunk(e)(s => Some((Chunk(s), s)))),
        lazy1[Int]("unfoldChunkZIO")(e => ZStream.unfoldChunkZIO(e)(s => ZIO.succeed(Some((Chunk(s), s))))),
        lazy1[Int]("unfoldZIO")(e => ZStream.unfoldZIO(e)(s => ZIO.succeed(Some((s, s))))),
        lazy1("unwrap")(ZStream.unwrap(_)),
        lazy1("unwrapScoped")(ZStream.unwrapScoped[Any](_)),
        lazy2[Boolean, ZStream[Any, Nothing, Nothing]]("when")((b, s) => ZStream.when(b)(s)),
        lazy1[Int]("whenCase")(e => ZStream.whenCase(e) { case _ => ZStream.empty }),
        lazy1[ZIO[Any, Throwable, Int]]("whenCaseZIO")(e => ZStream.whenCaseZIO(e).apply { case _ => ZStream.empty })
      ),
      suite("ZStream instance methods")(
        lazy1[ZStreamAspect[Nothing, Any, Nothing, Any, Nothing, Any]]("@@")(a => ZStream.empty @@ a),
        lazy1[ZStream[Any, Nothing, Nothing]]("++")(s => ZStream.empty ++ s),
        lazy1[ZStream[Any, Int, Nothing]]("<>")(s => ZStream.fail(1) <> s),
        lazy1[ZStream[Any, Nothing, Nothing]]("*>")(s => ZStream.empty *> s),
        lazy1[ZStream[Any, Nothing, Nothing]]("<*")(s => ZStream.empty <* s),
        lazy1[ZStream[Any, Nothing, Nothing]]("<*>")(s => ZStream.empty <*> s),
        lazy1[ZStream[Any, Nothing, Nothing]]("&>")(s => ZStream.empty &> s),
        lazy1[ZStream[Any, Nothing, Nothing]]("<&")(s => ZStream.empty <& s),
        lazy1[ZStream[Any, Nothing, Nothing]]("<&>")(s => ZStream.empty <&> s),
        lazy1[ZPipeline[Any, Nothing, Nothing, Int]](">>>")(p => ZStream.empty >>> p),
        lazy1("aggregateAsync")(ZStream.empty.aggregateAsync(_)),
        lazy2("aggregateAsyncWithin")((sink, schedule) => ZStream.empty.aggregateAsyncWithin(sink, schedule)),
        lazy2("aggregateAsyncWithinEither")((sink, schedule) =>
          ZStream.empty.aggregateAsyncWithinEither(sink, schedule)
        ),
        lazy1("as")(ZStream.empty.as(_)),
        lazy2("broadcast")((n, lag) => ZStream.empty.broadcast(n, lag)),
        lazy1("broadcastDynamic")(ZStream.empty.broadcastDynamic(_)),
        lazy2("broadcastedQueues")((n, lag) => ZStream.empty.broadcastedQueues(n, lag)),
        lazy1("broadcastedQueuesDynamic")(ZStream.empty.broadcastedQueuesDynamic(_)),
        lazy1("buffer")(ZStream.empty.buffer(_)),
        lazy1("bufferChunks")(ZStream.empty.bufferChunks(_)),
        lazy1("bufferChunksDropping")(ZStream.empty.bufferChunksDropping(_)),
        lazy1("bufferChunksSliding")(ZStream.empty.bufferChunksSliding(_)),
        lazy1("bufferDropping")(ZStream.empty.bufferDropping(_)),
        lazy1("bufferSliding")(ZStream.empty.bufferSliding(_)),
        lazy2[ZStream[Any, Nothing, Int], Int]("combine")((that, s) =>
          ZStream.empty.combine(that)(s)(
            (_: Int, _: ZIO[Any, Option[Nothing], Nothing], _: ZIO[Any, Option[Nothing], Int]) =>
              ZIO.succeed(Exit.succeed((1, 1)))
          )
        ),
        lazy2[ZStream[Any, Nothing, Int], Int]("combineChunks")((that, s) =>
          ZStream.empty.combineChunks(that)(s)(
            (_: Int, _: ZIO[Any, Option[Nothing], Chunk[Nothing]], _: ZIO[Any, Option[Nothing], Chunk[Int]]) =>
              ZIO.succeed(Exit.succeed((Chunk(1), 1)))
          )
        ),
        lazy1("concat")(ZStream.empty.concat(_)),
        lazy1("cross")(ZStream.empty.cross(_)),
        lazy1("crossLeft")(ZStream.empty.crossLeft(_)),
        lazy1("crossRight")(ZStream.empty.crossRight(_)),
        lazy1[ZStream[Any, Nothing, Int]]("crossWith")(that => ZStream.empty.crossWith(that)((_, _) => ())),
        lazy1("debounce")(ZStream.empty.debounce(_)),
        lazy2("distributedWith")((n, lag) => ZStream.empty.distributedWith(n, lag, _ => ZIO.succeed(_ => true))),
        lazy1("distributedWithDynamic")(lag => ZStream.empty.distributedWithDynamic(lag, _ => ZIO.succeed(_ => true))),
        lazy1("drainFork")(ZStream.empty.drainFork(_)),
        lazy1("drop")(ZStream.empty.drop(_)),
        lazy1("dropRight")(ZStream.empty.dropRight(_)),
        lazy1("ensuring")(ZStream.empty.ensuring(_)),
        lazy2[Int, Int]("flatMapPar")((n, bufferSize) => ZStream.empty.flatMapPar(n, bufferSize)(_ => ZStream.empty)),
        lazy2[Int, Int]("flatMapParSwitch")((n, bufferSize) =>
          ZStream.empty.flatMapParSwitch(n, bufferSize)(_ => ZStream.empty)
        ),
        lazy2("flattenPar")((n, outputBuffer) =>
          (ZStream.empty: ZStream[Any, Nothing, ZStream[Any, Nothing, Int]]).flattenPar(n, outputBuffer)
        ),
        lazy1("grouped")(ZStream.empty.grouped(_)),
        lazy2("groupedWithin")((chunkSize, within) => ZStream.empty.groupedWithin(chunkSize, within)),
        lazy1("haltAfter")(ZStream.empty.haltAfter(_)),
        lazy1("interleave")(ZStream.empty.interleave(_)),
        lazy1("interruptAfter")(ZStream.empty.interruptAfter(_)),
        lazy1("intersperse")(ZStream.empty.intersperse(_)),
        lazy3("intersperse(start, middle, end)")((start, middle, end) => ZStream.empty.intersperse(start, middle, end)),
        lazy1[Int]("mapAccum")(s => ZStream(1).mapAccum(s)((st: Int, _: Int) => (st, st))),
        lazy1[Int]("mapZIOPar")(n => ZStream.empty.mapZIOPar(n)(_ => ZIO.unit)),
        lazy2[Int, Int]("mapZIOParUnordered")((n, bufferSize) =>
          ZStream.empty.mapZIOParUnordered(n, bufferSize)(_ => ZIO.unit)
        ),
        lazy1("mergeEither")(ZStream.empty.mergeEither(_)),
        lazy1("mergeHaltEither")(ZStream.empty.mergeHaltEither(_)),
        lazy1("mergeHaltLeft")(ZStream.empty.mergeHaltLeft(_)),
        lazy1("mergeHaltRight")(ZStream.empty.mergeHaltRight(_)),
        lazy3("mkString")((before, middle, after) =>
          (ZStream.empty: ZStream[Any, Nothing, String]).mkString(before, middle, after)
        ),
        lazy1("onExecutor")(ZStream.empty.onExecutor(_)),
        lazy1("orElse")(ZStream.fail(1).orElse(_)),
        lazy1("orElseFail")(ZStream.fail(1).orElseFail(_)),
        lazy1("orElseSucceed")(ZStream.fail(1).orElseSucceed(_)),
        lazy1("partition")(buffer => ZStream.empty.partition(_ => true, buffer)),
        lazy1("pipeThrough")(ZStream.empty.pipeThrough(_)),
        lazy1("pipeThroughChannel")(ZStream.empty.pipeThroughChannel(_)),
        lazy1("rechunk")(ZStream.empty.rechunk(_)),
        lazy1("repeat - instance")(ZStream.empty.repeat(_)),
        lazy1("repeatEither")(ZStream.empty.repeatEither(_)),
        lazy1("repeatElements")(ZStream.empty.repeatElements(_)),
        lazy1("run")(ZStream.empty.run(_)),
        lazy1("runScoped")(ZStream.empty.runScoped(_)),
        lazy1[Int]("runFold")(s => ZStream(1).runFold(s)((st: Int, _: Int) => st)),
        lazy1[Int]("runFoldScoped")(s => ZStream(1).runFoldScoped(s)((st: Int, _: Int) => st)),
        lazy1[Int]("runFoldScopedZIO")(s => ZStream(1).runFoldScopedZIO(s)((st: Int, _: Int) => ZIO.succeed(st))),
        lazy1[Int]("runFoldWhile")(s => ZStream(1).runFoldWhile(s)((_: Int) => true)((st: Int, _: Int) => st)),
        lazy1[Int]("runFoldWhileScoped")(s =>
          ZStream(1).runFoldWhileScoped(s)((_: Int) => true)((st: Int, _: Int) => st)
        ),
        lazy1[Int]("runFoldWhileZIO")(s =>
          ZStream(1).runFoldWhileZIO(s)((_: Int) => true)((st: Int, _: Int) => ZIO.succeed(st))
        ),
        lazy1[Int]("runFoldZIO")(s => ZStream(1).runFoldZIO(s)((st: Int, _: Int) => ZIO.succeed(st))),
        lazy1[Int]("scan")(s => ZStream(1).scan(s)((st: Int, _: Int) => st)),
        lazy1[Int]("scanZIO")(s => ZStream(1).scanZIO(s)((st: Int, _: Int) => ZIO.succeed(st))),
        lazy1("schedule")(ZStream.empty.schedule(_)),
        lazy1("sliding")(ZStream.empty.sliding(_)),
        lazy1("someOrElse")(default => (ZStream.empty: ZStream[Any, Nothing, Option[Int]]).someOrElse(default)),
        lazy1("someOrFail")(e => (ZStream.empty: ZStream[Any, Nothing, Option[Int]]).someOrFail(e)),
        lazy1("splitOnChunk")(ZStream.empty.splitOnChunk(_)),
        lazy1("take")(ZStream.empty.take(_)),
        lazy1("takeRight")(ZStream.empty.takeRight(_)),
        lazy3[Long, Duration, Long]("throttleEnforce")((units, duration, burst) =>
          ZStream.empty.throttleEnforce(units, duration, burst)(_ => 1L)
        ),
        lazy3[Long, Duration, Long]("throttleEnforceZIO")((units, duration, burst) =>
          ZStream.empty.throttleEnforceZIO(units, duration, burst)(_ => ZIO.succeed(1L))
        ),
        lazy3[Long, Duration, Long]("throttleShape")((units, duration, burst) =>
          ZStream.empty.throttleShape(units, duration, burst)(_ => 1L)
        ),
        lazy3[Long, Duration, Long]("throttleShapeZIO")((units, duration, burst) =>
          ZStream.empty.throttleShapeZIO(units, duration, burst)(_ => ZIO.succeed(1L))
        ),
        lazy1("timeout")(ZStream.empty.timeout(_)),
        lazy1[Any]("timeoutFail")(e => ZStream.empty.timeoutFail(e)(1.second)),
        lazy2[Cause[Any], Duration]("timeoutFailCause")((cause, duration) =>
          ZStream.empty.timeoutFailCause(cause)(duration)
        ),
        lazy1("via")(ZStream.empty.via(_)),
        lazy1("when")(ZStream.empty.when(_)),
        lazy1("whenZIO")(ZStream.empty.whenZIO(_))
      )
    )
}
