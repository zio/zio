package zio.stream

import zio._

import zio.internal.{Hub => _, _}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.internal._

sealed trait ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone] { self =>

  final def <*>[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit
    zippable: Zippable[OutDone, OutDone2],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, zippable.Out] =
    zip(that)

  final def *>[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2] =
    zipRight(that)

  final def <*[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone] =
    zipLeft(that)

  final def >>>[Env1 <: Env, OutErr2, OutElem2, OutDone2](
    that: => ZChannel[Env1, OutErr, OutElem, OutDone, OutErr2, OutElem2, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2] =
    pipeTo(that)

  final def as[OutDone2](done: => OutDone2)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2] =
    map(_ => done)

  final def catchAll[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone1 >: OutDone
  ](
    onErr: OutErr => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1] =
    catchAllCause(_.failureOrCause.fold(onErr, ZChannel.refailCause(_)))

  final def catchAllCause[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone1 >: OutDone
  ](
    onErr: Cause[OutErr] => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1] =
    foldCauseChannel(onErr, ZChannel.succeedNow)

  /**
   * Returns a new channel, which is the same as this one, except its outputs
   * are filtered and transformed by the specified partial function.
   */
  final def collect[OutElem2](
    f: PartialFunction[OutElem, OutElem2]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone] = {
    val pf = f.lift
    lazy val collector: ZChannel[Env, OutErr, OutElem, OutDone, OutErr, OutElem2, OutDone] =
      ZChannel.readWithCause(
        pf(_: OutElem) match {
          case None       => collector
          case Some(out2) => ZChannel.write(out2) *> collector
        },
        (e: Cause[OutErr]) => ZChannel.refailCause(e),
        (z: OutDone) => ZChannel.succeedNow(z)
      )

    self pipeTo collector
  }

  /**
   * Returns a new channel, which is the same as this one, except that all the
   * outputs are collected and bundled into a tuple together with the terminal
   * value of this channel.
   *
   * As the channel returned from this channel collects all of this channel's
   * output into an in- memory chunk, it is not safe to call this method on
   * channels that output a large or unbounded number of values.
   */
  final def collectElements(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, Nothing, (Chunk[OutElem], OutDone)] =
    ZChannel.suspend {
      val builder = ChunkBuilder.make[OutElem]()

      lazy val reader: ZChannel[Env, OutErr, OutElem, OutDone, OutErr, Nothing, OutDone] =
        ZChannel.readWithCause(
          (out: OutElem) => ZChannel.succeed(builder += out) *> reader,
          (e: Cause[OutErr]) => ZChannel.refailCause(e),
          (z: OutDone) => ZChannel.succeedNow(z)
        )

      (self pipeTo reader).flatMap(z => ZChannel.succeed((builder.result(), z)))
    }

  final def concatMap[Env1 <: Env, InErr1 <: InErr, InElem1 <: InElem, InDone1 <: InDone, OutErr1 >: OutErr, OutElem2](
    onElem: OutElem => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any] =
    concatMapWith(onElem)((_, _) => (), (_, _) => ())

  final def concatMapWith[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem2,
    OutDone2,
    OutDone3
  ](
    onElem: OutElem => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, OutDone2]
  )(
    combineInner: (OutDone2, OutDone2) => OutDone2,
    combineOuter: (OutDone2, OutDone) => OutDone3
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, OutDone3] =
    ZChannel.ConcatAll(trace, self, onElem, combineInner, combineOuter)

  /**
   * Returns a new channel, which is the concatenation of all the channels that
   * are written out by this channel. This method may only be called on channels
   * that output other channels.
   */
  final def concatOut[Env1 <: Env, InErr1 <: InErr, InElem1 <: InElem, InDone1 <: InDone, OutErr1 >: OutErr, OutElem2](
    implicit
    ev: OutElem <:< ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any] =
    ZChannel.concatAll(self.mapOut(ev))

  /**
   * Returns a new channel which is the same as this one but applies the given
   * function to the input channel's done value.
   */
  final def contramap[InDone0](
    f: InDone0 => InDone
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone0, OutErr, OutElem, OutDone] = {
    lazy val reader: ZChannel[Any, InErr, InElem, InDone0, InErr, InElem, InDone] =
      ZChannel.readWithCause(
        (in: InElem) => ZChannel.write(in) *> reader,
        (err: Cause[InErr]) => ZChannel.refailCause(err),
        (done0: InDone0) => ZChannel.succeedNow(f(done0))
      )

    reader >>> self
  }

  /**
   * Returns a new channel which is the same as this one but applies the given
   * function to the input channel's error value.
   */
  final def contramapError[InErr0](
    f: InErr0 => InErr
  )(implicit trace: Trace): ZChannel[Env, InErr0, InElem, InDone, OutErr, OutElem, OutDone] = {
    lazy val reader: ZChannel[Any, InErr0, InElem, InDone, InErr, InElem, InDone] =
      ZChannel.readWithCause(
        (in: InElem) => ZChannel.write(in) *> reader,
        (err0: Cause[InErr0]) => ZChannel.refailCause(err0.map(f)),
        (done: InDone) => ZChannel.succeedNow(done)
      )

    reader >>> self
  }

  /**
   * Returns a new channel which is the same as this one but applies the given
   * ZIO function to the input channel's error value.
   */
  final def contramapErrorZIO[InErr0, Env1 <: Env](
    f: InErr0 => ZIO[Env1, InErr, InDone]
  )(implicit trace: Trace): ZChannel[Env1, InErr0, InElem, InDone, OutErr, OutElem, OutDone] = {
    lazy val reader: ZChannel[Env1, InErr0, InElem, InDone, InErr, InElem, InDone] =
      ZChannel.readWith(
        (in: InElem) => ZChannel.write(in) *> reader,
        (err0: InErr0) => ZChannel.fromZIO(f(err0)),
        (done: InDone) => ZChannel.succeedNow(done)
      )

    reader >>> self
  }

  /**
   * Returns a new channel which is the same as this one but applies the given
   * function to the input channel's output elements
   */
  final def contramapIn[InElem0](
    f: InElem0 => InElem
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem0, InDone, OutErr, OutElem, OutDone] = {
    lazy val reader: ZChannel[Any, InErr, InElem0, InDone, InErr, InElem, InDone] =
      ZChannel.readWithCause(
        (in: InElem0) => ZChannel.write(f(in)) *> reader,
        (err: Cause[InErr]) => ZChannel.refailCause(err),
        (done: InDone) => ZChannel.succeedNow(done)
      )

    reader >>> self
  }

  /**
   * Returns a new channel which is the same as this one but applies the given
   * ZIO function to the input channel's output elements
   */
  final def contramapInZIO[InElem0, Env1 <: Env](
    f: InElem0 => ZIO[Env1, InErr, InElem]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem0, InDone, OutErr, OutElem, OutDone] = {
    lazy val reader: ZChannel[Env1, InErr, InElem0, InDone, InErr, InElem, InDone] =
      ZChannel.readWithCause(
        (in: InElem0) => ZChannel.fromZIO(f(in)).flatMap(ZChannel.write(_)) *> reader,
        (err: Cause[InErr]) => ZChannel.refailCause(err),
        (done: InDone) => ZChannel.succeedNow(done)
      )

    reader >>> self
  }

  /**
   * Returns a new channel which is the same as this one but applies the given
   * ZIO function to the input channel's done value.
   */
  final def contramapZIO[InDone0, Env1 <: Env](
    f: InDone0 => ZIO[Env1, InErr, InDone]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone0, OutErr, OutElem, OutDone] = {
    lazy val reader: ZChannel[Env1, InErr, InElem, InDone0, InErr, InElem, InDone] =
      ZChannel.readWithCause(
        (in: InElem) => ZChannel.write(in) *> reader,
        (err: Cause[InErr]) => ZChannel.refailCause(err),
        (done0: InDone0) => ZChannel.fromZIO(f(done0))
      )

    reader >>> self
  }

  /**
   * Returns a new channel which reads all the elements from upstream's output
   * channel and ignores them, then terminates with the upstream result value.
   */
  final def drain(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, Nothing, OutDone] = {
    lazy val drainer: ZChannel[Any, OutErr, OutElem, OutDone, OutErr, Nothing, OutDone] =
      ZChannel.readWithCause(
        elem => drainer,
        err => ZChannel.refailCause(err),
        done => ZChannel.succeedNow(done)
      )

    self >>> drainer
  }

  final def emitCollect(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, (Chunk[OutElem], OutDone), Unit] =
    collectElements.flatMap(ZChannel.write)

  final def ensuring[Env1 <: Env](
    finalizer: => ZIO[Env1, Nothing, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ensuringWith(_ => finalizer)

  final def ensuringWith[Env1 <: Env](
    finalizer: Exit[OutErr, OutDone] => ZIO[Env1, Nothing, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Ensuring(trace, self, finalizer)

  final def flatMap[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    onDone: OutDone => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2] =
    foldChannel(ZChannel.fail(_), onDone)

  final def flatten[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](implicit
    ev: OutDone <:< ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2] =
    self.flatMap(ev)

  final def foldCauseChannel[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone2
  ](
    onErr: Cause[OutErr] => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2],
    onDone: OutDone => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2] =
    ZChannel.Fold(trace, self, onErr, onDone)

  final def foldChannel[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone2
  ](
    onErr: OutErr => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2],
    onDone: OutDone => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2] =
    foldCauseChannel(_.failureOrCause.fold(onErr, ZChannel.refailCause(_)), onDone)

  /**
   * Returns a new channel, which is the same as this one, except it will be
   * interrupted when the specified effect completes. If the effect completes
   * successfully before the underlying channel is done, then the returned
   * channel will yield the success value of the effect as its terminal value.
   * On the other hand, if the underlying channel finishes first, then the
   * returned channel will yield the success value of the underlying channel as
   * its terminal value.
   */
  final def interruptWhen[Env1 <: Env, OutErr1 >: OutErr, OutDone1 >: OutDone](
    io: ZIO[Env1, OutErr1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem, OutDone1] =
    self.mergeWith(ZChannel.fromZIO(io))(
      selfDone => ZChannel.MergeDecision.done(ZIO.done(selfDone)),
      ioDone => ZChannel.MergeDecision.done(ZIO.done(ioDone))
    )

  /**
   * Returns a new channel, which is the same as this one, except it will be
   * interrupted when the specified promise is completed. If the promise is
   * completed before the underlying channel is done, then the returned channel
   * will yield the value of the promise. Otherwise, if the underlying channel
   * finishes first, then the returned channel will yield the value of the
   * underlying channel.
   */
  final def interruptWhen[OutErr1 >: OutErr, OutDone1 >: OutDone](
    promise: Promise[OutErr1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr1, OutElem, OutDone1] =
    interruptWhen(promise.await)

  final def map[OutDone2](
    onDone: OutDone => OutDone2
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2] =
    flatMap(done => ZChannel.succeedNow(onDone(done)))

  final def mapError[OutErr2](
    onErr: OutErr => OutErr2
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone] =
    mapErrorCause(_.map(onErr))

  final def mapErrorCause[OutErr2](
    onErr: Cause[OutErr] => Cause[OutErr2]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone] =
    catchAllCause(cause => ZChannel.refailCause(onErr(cause)))

  final def mapOut[OutElem2](
    f: OutElem => OutElem2
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone] = {
    lazy val reader: ZChannel[Env, OutErr, OutElem, OutDone, OutErr, OutElem2, OutDone] =
      ZChannel.readWithCause(
        out => ZChannel.write(f(out)) *> reader,
        (e: Cause[OutErr]) => ZChannel.refailCause(e),
        (z: OutDone) => ZChannel.succeedNow(z)
      )

    self >>> reader
  }

  final def mapOutZIO[Env1 <: Env, OutErr1 >: OutErr, OutElem2](
    f: OutElem => ZIO[Env1, OutErr1, OutElem2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone] = {
    lazy val reader: ZChannel[Env1, OutErr, OutElem, OutDone, OutErr1, OutElem2, OutDone] =
      ZChannel.readWithCause(
        (out: OutElem) => ZChannel.fromZIO(f(out)).flatMap(ZChannel.write(_)) *> reader,
        (e: Cause[OutErr1]) => ZChannel.refailCause(e),
        (z: OutDone) => ZChannel.succeedNow(z)
      )

    self >>> reader
  }

  /**
   * Creates a channel that is like this channel but the given ZIO function gets
   * applied to each emitted output element, taking `n` elements at once and
   * mapping them in parallel
   */
  final def mapOutZIOPar[Env1 <: Env, OutErr1 >: OutErr, OutElem2](n: Int)(
    f: OutElem => ZIO[Env1, OutErr1, OutElem2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone] =
    ZChannel.MapOutZIOPar(trace, self, n, f)

  final def mapZIO[Env1 <: Env, OutErr1 >: OutErr, OutDone2](
    onDone: OutDone => ZIO[Env1, OutErr1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem, OutDone2] =
    flatMap(done => ZChannel.fromZIO(onDone(done)))

  /**
   * Returns a new channel which creates a new channel for each emitted element
   * and merges some of them together. Different merge strategies control what
   * happens if there are more than the given maximum number of channels gets
   * created. See [[ZChannel.mergeAll]].
   * @param n
   *   The maximum number of channels to merge
   * @param bufferSize
   *   Number of elements that can be buffered from upstream for the merging
   * @param mergeStrategy
   *   Merge strategy, either back pressure or sliding.
   * @param f
   *   The function that creates a new channel from each emitted element
   */
  final def mergeMap[Env1 <: Env, InErr1 <: InErr, InElem1 <: InElem, InDone1 <: InDone, OutErr1 >: OutErr, OutElem2](
    n: => Int,
    bufferSize: => Int = 16,
    mergeStrategy: => ZChannel.MergeStrategy = ZChannel.MergeStrategy.BackPressure
  )(
    f: OutElem => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any] =
    ZChannel.mergeAll(self.mapOut(f), n, bufferSize, mergeStrategy)

  /**
   * Returns a new channel which merges a number of channels emitted by this
   * channel using the back pressuring merge strategy. See [[ZChannel.mergeAll]]
   */
  final def mergeOut[Env1 <: Env, InErr1 <: InErr, InElem1 <: InElem, InDone1 <: InDone, OutErr1 >: OutErr, OutElem2](
    n: => Int
  )(implicit
    ev: OutElem <:< ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any] =
    ZChannel.mergeAll(self.mapOut(ev), n)

  /**
   * Returns a new channel which merges a number of channels emitted by this
   * channel using the back pressuring merge strategy and uses a given function
   * to merge each completed subchannel's result value. See
   * [[ZChannel.mergeAll]]
   */
  final def mergeOutWith[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem2,
    OutDone1 >: OutDone
  ](n: => Int)(f: (OutDone1, OutDone1) => OutDone1)(implicit
    ev: OutElem <:< ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, OutDone1],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, OutDone1] =
    ZChannel.mergeAllWith(self.mapOut(ev), n)(f)

  final def mergeWith[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutErr3,
    OutElem1 >: OutElem,
    OutDone2,
    OutDone3
  ](
    that: ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2]
  )(
    leftDone: Exit[OutErr, OutDone] => ZChannel.MergeDecision[Env1, OutErr2, OutDone2, OutErr3, OutDone3],
    rightDone: Exit[OutErr2, OutDone2] => ZChannel.MergeDecision[Env1, OutErr, OutDone, OutErr3, OutDone3]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr3, OutElem1, OutDone3] =
    ZChannel.MergeWith(trace, self, that, leftDone, rightDone)

  /** Returns a channel that never completes */
  @deprecated("use ZChannel.never", "3.0.0")
  final def never(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Nothing] =
    ZChannel.fromZIO(ZIO.never)

  final def onExecutor(executor: => Executor)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.onExecutor(executor)(self)

  /**
   * Translates channel failure into death of the fiber, making all failures
   * unchecked and not a part of the type of the channel.
   */
  final def orDie(implicit
    ev: OutErr <:< Throwable,
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, Nothing, OutElem, OutDone] =
    orDieWith(ev)

  /**
   * Keeps none of the errors, and terminates the fiber with them, using the
   * specified function to convert the `OutErr` into a `Throwable`.
   */
  final def orDieWith(f: OutErr => Throwable)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, Nothing, OutElem, OutDone] =
    self.catchAll(e => throw f(e))

  /**
   * Returns a new channel that will perform the operations of this one, until
   * failure, and then it will switch over to the operations of the specified
   * fallback channel.
   */
  final def orElse[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone1 >: OutDone
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1] =
    self.catchAll(_ => that)

  final def pipeTo[Env1 <: Env, OutErr2, OutElem2, OutDone2](
    that: => ZChannel[Env1, OutErr, OutElem, OutDone, OutErr2, OutElem2, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2] =
    ZChannel.PipeTo(trace, self, that)

  /**
   * Returns a new channel that pipes the output of this channel into the
   * specified channel and preserves this channel's failures without providing
   * them to the other channel for observation.
   */
  final def pipeToOrFail[Env1 <: Env, OutErr1 >: OutErr, OutElem2, OutDone2](
    that: => ZChannel[Env1, Nothing, OutElem, OutDone, OutErr1, OutElem2, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone2] =
    ZChannel.suspend {

      class ChannelFailure(val err: Cause[OutErr1]) extends Throwable
      var channelFailure: ChannelFailure = null

      lazy val reader: ZChannel[Env, OutErr, OutElem, OutDone, Nothing, OutElem, OutDone] =
        ZChannel.readWithCause(
          elem => ZChannel.write(elem) *> reader,
          err => {
            channelFailure = new ChannelFailure(err)
            ZChannel.refailCause(Cause.die(channelFailure))
          },
          done => ZChannel.succeedNow(done)
        )

      lazy val writer: ZChannel[Env1, OutErr1, OutElem2, OutDone2, OutErr1, OutElem2, OutDone2] =
        ZChannel.readWithCause(
          elem => ZChannel.write(elem) *> writer,
          {
            case Cause.Die(value: ChannelFailure, _) if value == channelFailure => {
              ZChannel.refailCause(channelFailure.err)
            }
            case cause => ZChannel.refailCause(cause)
          },
          done => ZChannel.succeedNow(done)
        )

      self >>> reader >>> that >>> writer
    }

  final def provideEnvironment(
    environment: => ZEnvironment[Env]
  )(implicit trace: Trace): ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Provide(trace, environment, self)

  /**
   * Provides a layer to the channel, which translates it to another level.
   */
  final def provideLayer[OutErr1 >: OutErr, Env0](
    layer: => ZLayer[Env0, OutErr1, Env]
  )(implicit trace: Trace): ZChannel[Env0, InErr, InElem, InDone, OutErr1, OutElem, OutDone] =
    ZChannel.unwrapScoped[Env0](layer.build.map(env => self.provideEnvironment(env)))

  /**
   * Transforms the environment being provided to the channel with the specified
   * function.
   */
  final def provideSomeEnvironment[Env0](
    f: ZEnvironment[Env0] => ZEnvironment[Env]
  )(implicit trace: Trace): ZChannel[Env0, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.environmentWithChannel[Env0] { env =>
      self.provideEnvironment(f(env))
    }

  /**
   * Splits the environment into two parts, providing one part using the
   * specified layer and leaving the remainder `Env0`.
   */
  final def provideSomeLayer[Env0]
    : ZChannel.ProvideSomeLayer[Env0, Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    new ZChannel.ProvideSomeLayer[Env0, Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](self)

  final def run(implicit ev1: Any <:< InElem, ev2: OutElem <:< Nothing, trace: Trace): ZIO[Env, OutErr, OutDone] =
    ZIO.scoped[Env](runScoped)

  final def fork(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ChannelFiberRuntime[InErr, InElem, InDone, OutErr, OutElem, OutDone]] =
    forkUnstartedZIO.flatMap { fiber =>
      ZIO.succeed {
        fiber.start()
        fiber
      }
    }

  final def forkScoped(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ChannelFiberRuntime[InErr, InElem, InDone, OutErr, OutElem, OutDone]] =
    forkScopedUnstarted.flatMap { fiber =>
      ZIO.succeed {
        fiber.start()
        fiber
      }
    }

  private[stream] final def forkUnstartedZIO(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ChannelFiberRuntime[InErr, InElem, InDone, OutErr, OutElem, OutDone]] =
    ZIO.transplant { grafter =>
      ZIO.environmentWithZIO { environment =>
        ZIO.runtime[Any].flatMap { runtime =>
          ZIO.getFiberRefs.flatMap { fiberRefs =>
            ZIO.runtimeFlags.flatMap { runtimeFlags =>
              ZIO.acquireRelease {
                ZIO.succeed {
                  val fiber = new ChannelFiberRuntime(
                    self.asInstanceOf[ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone]],
                    None,
                    environment,
                    fiberRefs,
                    runtimeFlags,
                    runtime,
                    FiberId.make(trace)(Unsafe.unsafe),
                    grafter
                  )
                  val supervisor = fiber.getSupervisor()(Unsafe.unsafe)
                  supervisor.onStart(
                    environment,
                    ZIO.unit,
                    None,
                    fiber
                  )(Unsafe.unsafe)
                  fiber.addObserver(exit => supervisor.onEnd(exit, fiber)(Unsafe.unsafe))
                  fiber
                }
              } { fiber =>
                fiber.interrupt
              }
            }
          }
        }
      }
    }

  private final def forkScopedUnstarted(implicit
    trace: Trace
  ): ZIO[Env with Scope, Nothing, ChannelFiberRuntime[InErr, InElem, InDone, OutErr, OutElem, OutDone]] =
    ZIO.transplant { grafter =>
      ZIO.scopeWith { scope =>
        scope.fork.flatMap { child =>
          ZIO.environmentWithZIO { environment =>
            ZIO.runtime[Any].flatMap { runtime =>
              ZIO.getFiberRefs.flatMap { fiberRefs =>
                ZIO.runtimeFlags.flatMap { runtimeFlags =>
                  ZIO.acquireRelease {
                    ZIO.succeed {
                      val fiber = new ChannelFiberRuntime(
                        self.asInstanceOf[ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone]],
                        Some(child),
                        environment,
                        fiberRefs,
                        runtimeFlags,
                        runtime,
                        FiberId.make(trace)(Unsafe.unsafe),
                        grafter
                      )
                      val supervisor = fiber.getSupervisor()(Unsafe.unsafe)
                      supervisor.onStart(
                        environment,
                        ZIO.unit,
                        None,
                        fiber
                      )(Unsafe.unsafe)
                      fiber.addObserver(exit => supervisor.onEnd(exit, fiber)(Unsafe.unsafe))
                      fiber
                    }
                  } { fiber =>
                    fiber.interrupt
                  }
                }
              }
            }
          }
        }
      }
    }

  /** Creates a channel which repeatedly runs this channel */
  final def repeated(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Nothing] = {
    lazy val loop: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Nothing] = self *> loop
    loop
  }

  final def runScoped(implicit
    ev1: Any <:< InElem,
    ev2: OutElem <:< Nothing,
    trace: Trace
  ): ZIO[Env with Scope, OutErr, OutDone] =
    for {
      fiber <- self.forkScoped
      exit  <- fiber.await
      done  <- ZIO.done(exit)
    } yield done

  final def runCollect(implicit ev1: Any <:< InElem, trace: Trace): ZIO[Env, OutErr, (Chunk[OutElem], OutDone)] =
    collectElements.run

  final def runDrain(implicit ev1: Any <:< InElem, trace: Trace): ZIO[Env, OutErr, OutDone] =
    drain.run

  /** Converts this channel to a [[ZPipeline]] */
  final def toPipeline[In, Out](implicit
    In: Chunk[In] <:< InElem,
    Out: OutElem <:< Chunk[Out],
    InDone: Any <:< InDone,
    trace: Trace
  ): ZPipeline[Env, OutErr, In, Out] =
    ZPipeline.fromChannel[Env, OutErr, In, Out](
      self.asInstanceOf[ZChannel[Env, Nothing, Chunk[In], Any, OutErr, Chunk[Out], Any]]
    )

  final def toPull(implicit trace: Trace): ZIO[Env with Scope, Nothing, ZIO[Env, OutErr, Either[OutDone, OutElem]]] =
    self.forkScopedUnstarted.map(_.readZIO)

  /** Converts this channel to a [[ZSink]] */
  final def toSink[In, Out](implicit
    In: Chunk[In] <:< InElem,
    Out: OutElem <:< Chunk[Out],
    InDone: Any <:< InDone,
    trace: Trace
  ): ZSink[Env, OutErr, In, Out, OutDone] =
    ZSink.fromChannel[Env, OutErr, In, Out, OutDone](
      self.asInstanceOf[ZChannel[Env, Nothing, Chunk[In], Any, OutErr, Chunk[Out], OutDone]]
    )

  /** Converts this channel to a [[ZStream]] */
  final def toStream[Out](implicit
    Out: OutElem <:< Chunk[Out],
    InErr: Any <:< InErr,
    InElem: Any <:< InElem,
    InDone: Any <:< InDone,
    trace: Trace
  ): ZStream[Env, OutErr, Out] =
    ZStream.fromChannel[Env, OutErr, Out](
      self.asInstanceOf[ZChannel[Env, Any, Any, Any, OutErr, Chunk[Out], Any]]
    )

  final def uninterruptible(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.uninterruptibleMask(_ => self)

  final def unit(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Unit] =
    as(())

  /**
   * Updates a service in the environment of this channel.
   */
  final def updateService[Service]
    : ZChannel.UpdateService[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, Service] =
    new ZChannel.UpdateService[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, Service](self)

  /**
   * Updates a service at the specified key in the environment of this channel.
   */
  final def updateServiceAt[Service]
    : ZChannel.UpdateServiceAt[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, Service] =
    new ZChannel.UpdateServiceAt[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, Service](self)

  final def zip[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit
    zippable: Zippable[OutDone, OutDone2],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, zippable.Out] =
    zipWith(that)(zippable.zip(_, _))

  final def zipRight[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2] =
    flatMap(_ => that)

  final def zipLeft[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone] =
    flatMap(done => that.map(_ => done))

  /**
   * Creates a new channel which runs in parallel this and the other channel and
   * when both succeeds finishes with a tuple of both channel's done value
   */
  final def zipPar[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit
    zippable: Zippable[OutDone, OutDone2],
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, zippable.Out] =
    self.mergeWith(that)(
      exit1 =>
        ZChannel.MergeDecision.Await[Env1, OutErr1, OutDone2, OutErr1, zippable.Out](exit2 =>
          ZIO.done(exit1.zip(exit2))
        ),
      exit2 =>
        ZChannel.MergeDecision.Await[Env1, OutErr1, OutDone, OutErr1, zippable.Out](exit1 => ZIO.done(exit1.zip(exit2)))
    )

  /**
   * Creates a new channel which runs in parallel this and the other channel and
   * when both succeeds finishes with the first one's done value
   */
  final def zipParLeft[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone] =
    (self zipPar that).map(_._1)

  /**
   * Creates a new channel which runs in parallel this and the other channel and
   * when both succeeds finishes with the second one's done value
   */
  def zipParRight[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    that: => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2] =
    (self zipPar that).map(_._2)

  final def zipWith[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2,
    OutDone3
  ](
    that: ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(f: (OutDone, OutDone2) => OutDone3)(implicit
    trace: Trace
  ): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone3] =
    flatMap(done => that.map(done2 => f(done, done2)))

  final def zipWithIndex(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, (OutElem, Long), OutDone] = {

    def loop(index: Long): ZChannel[Env, OutErr, OutElem, OutDone, OutErr, (OutElem, Long), OutDone] =
      ZChannel.readWithCause(
        elem => ZChannel.write((elem, index)) *> loop(index + 1),
        cause => ZChannel.refailCause(cause),
        done => ZChannel.succeed(done)
      )

    self >>> loop(0)

  }

  private[stream] def trace: Trace =
    Trace.empty
}

object ZChannel {

  def acquireReleaseExitWith[Env, InErr, InElem, InDone, OutErr, Acquired, OutElem2, OutDone](
    acquire: => ZIO[Env, OutErr, Acquired]
  )(release: (Acquired, Exit[OutErr, OutDone]) => URIO[Env, Any])(
    use: Acquired => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone] =
    fromZIO(Ref.make[Exit[OutErr, OutDone] => URIO[Env, Any]](_ => ZIO.unit)).flatMap { ref =>
      fromZIO(acquire.tap(a => ref.set(release(a, _))).uninterruptible)
        .flatMap(use)
        .ensuringWith(ex => ref.get.flatMap(_.apply(ex)))
    }

  def acquireReleaseOutWith[Env, OutErr, OutElem](acquire: => ZIO[Env, OutErr, OutElem])(
    release: OutElem => ZIO[Env, Nothing, Any]
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, OutErr, OutElem, Unit] =
    acquireReleaseOutExitWith(acquire)((elem, _) => release(elem))

  def acquireReleaseOutExitWith[Env, OutErr, OutElem](acquire: => ZIO[Env, OutErr, OutElem])(
    release: (OutElem, Exit[Any, Any]) => ZIO[Env, Nothing, Any]
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, OutErr, OutElem, Unit] =
    ZChannel.uninterruptibleMask { restore =>
      ZChannel.fromZIO(acquire).flatMap { elem =>
        restore(ZChannel.write(elem)).ensuringWith(exit => release(elem, exit))
      }
    }

  def acquireReleaseWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, OutDone2](
    acquire: => ZIO[Env, OutErr, OutDone]
  )(
    release: OutDone => ZIO[Env, Nothing, Any]
  )(
    use: OutDone => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2] =
    ZChannel.fromZIO(acquire).flatMap { done =>
      use(done).ensuring(release(done))
    }

  def asyncOne[Env, Err, Done](
    registerCallback: (ZChannel[Env, Any, Any, Any, Err, Nothing, Done] => Unit) => Unit
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, Err, Nothing, Done] =
    ZChannel.Async(trace, { callback => registerCallback(callback); null })

  def async[Err, Elem, Out](
    f: ChannelFiberRuntime[Err, Elem, Out, Nothing, Nothing, Nothing] => Unit
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Err, Elem, Out] =
    ZChannel.unwrapScoped {
      ZChannel.identity[Err, Elem, Out].forkScoped.map { fiber =>
        f(fiber.asInstanceOf[ChannelFiberRuntime[Err, Elem, Out, Nothing, Nothing, Nothing]])
        ZChannel.fromRuntimeFiber(fiber, 0L)
      }
    }

  /**
   * Creates a channel backed by a buffer. When the buffer is empty, the channel
   * will simply passthrough its input as output. However, when the buffer is
   * non-empty, the value inside the buffer will be passed along as output.
   */
  def buffer[InErr, InElem, InDone](
    empty: => InElem,
    isEmpty: InElem => Boolean,
    ref: => Ref[InElem]
  )(implicit trace: Trace): ZChannel[Any, InErr, InElem, InDone, InErr, InElem, InDone] =
    ZChannel.suspend {
      def buffer(
        empty: InElem,
        isEmpty: InElem => Boolean,
        ref: Ref[InElem]
      ): ZChannel[Any, InErr, InElem, InDone, InErr, InElem, InDone] =
        ZChannel.unwrap {
          ref.modify { v =>
            if (isEmpty(v))
              (
                ZChannel.readWithCause(
                  (in: InElem) => ZChannel.write(in) *> buffer(empty, isEmpty, ref),
                  (err: Cause[InErr]) => ZChannel.refailCause(err),
                  (done: InDone) => ZChannel.succeedNow(done)
                ),
                v
              )
            else
              (ZChannel.write(v) *> buffer(empty, isEmpty, ref), empty)
          }
        }

      buffer(empty, isEmpty, ref)
    }

  def bufferChunk[InErr, InElem, InDone](
    ref: => Ref[Chunk[InElem]]
  )(implicit trace: Trace): ZChannel[Any, InErr, Chunk[InElem], InDone, InErr, Chunk[InElem], InDone] =
    buffer[InErr, Chunk[InElem], InDone](Chunk.empty, _.isEmpty, ref)

  def concatAll[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channels: => ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Any],
      Any
    ]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Any] =
    concatAllWith(channels)((_, _) => (), (_, _) => ())

  def concatAllWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, OutDone2, OutDone3](
    channels: => ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
      OutDone2
    ]
  )(
    f: (OutDone, OutDone) => OutDone,
    g: (OutDone, OutDone2) => OutDone3
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone3] =
    channels.concatMapWith(channel => channel)(f, g)

  /**
   * Accesses the whole environment of the channel.
   */
  def environment[Env](implicit
    trace: Trace
  ): ZChannel[Env, Any, Any, Any, Nothing, Nothing, ZEnvironment[Env]] =
    ZChannel.fromZIO(ZIO.environment)

  /**
   * Accesses the environment of the channel.
   */
  def environmentWith[Env]: ZChannel.EnvironmentWithPartiallyApplied[Env] =
    new ZChannel.EnvironmentWithPartiallyApplied[Env]

  /**
   * Accesses the environment of the channel in the context of a channel.
   */
  def environmentWithChannel[Env]: ZChannel.EnvironmentWithChannelPartiallyApplied[Env] =
    new ZChannel.EnvironmentWithChannelPartiallyApplied[Env]

  /**
   * Accesses the environment of the channel in the context of an effect.
   */
  def environmentWithZIO[Env]: ZChannel.EnvironmentWithZIOPartiallyApplied[Env] =
    new ZChannel.EnvironmentWithZIOPartiallyApplied[Env]

  def fail[OutErr](err: => OutErr)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, OutErr, Nothing, Nothing] =
    failCause(Cause.fail(err))

  def failCause[OutErr](cause: => Cause[OutErr])(implicit
    trace0: Trace
  ): ZChannel[Any, Any, Any, Any, OutErr, Nothing, Nothing] =
    ZChannel.stackTrace(trace0).flatMap { trace =>
      ZChannel.logSpans.flatMap { spans =>
        ZChannel.logAnnotations.flatMap { annotations =>
          ZChannel.Fail(cause.traced(trace).spanned(spans).annotated(annotations))
        }
      }
    }

  def fromEither[E, A](either: => Either[E, A])(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, E, Nothing, A] =
    ZChannel.suspend(either.fold(ZChannel.fail(_), ZChannel.succeed(_)))

  def fromHub[Err, Done, Elem](
    hub: => Hub[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
    ZChannel.unwrapScoped(hub.subscribe.map(fromQueue(_)))

  def fromHubScoped[Err, Done, Elem](
    hub: => Hub[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZIO[Scope, Nothing, ZChannel[Any, Any, Any, Any, Err, Elem, Done]] =
    hub.subscribe.map(fromQueue(_))

  def fromOption[A](option: => Option[A])(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, None.type, Nothing, A] =
    ZChannel.suspend(
      option.fold[ZChannel[Any, Any, Any, Any, None.type, Nothing, A]](ZChannel.fail(None))(ZChannel.succeed(_))
    )

  def fromQueue[Err, Done, Elem](
    queue: => Dequeue[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
    ZChannel.suspend {
      def fromQueue(queue: Dequeue[Either[Exit[Err, Done], Elem]]): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
        ZChannel.fromZIO(queue.take).flatMap {
          case Right(elem) => write(elem) *> fromQueue(queue)
          case Left(exit) =>
            exit.foldExit(
              failCause(_),
              succeedNow(_)
            )
        }

      fromQueue(queue)
    }

  def fromZIO[Env, Err, Done](zio: => ZIO[Env, Err, Done])(implicit
    trace: Trace
  ): ZChannel[Env, Any, Any, Any, Err, Nothing, Done] =
    ZChannel.suspend(ZChannel.FromZIO(trace, zio))

  def identity[Err, Elem, Done](implicit trace: Trace): ZChannel[Any, Err, Elem, Done, Err, Elem, Done] =
    readWithCause(
      (in: Elem) => write(in) *> identity[Err, Elem, Done],
      (err: Cause[Err]) => failCause(err),
      (done: Done) => succeedNow(done)
    )

  def interruptAs(fiberId: => FiberId)(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Nothing] =
    failCause(Cause.interrupt(fiberId))

  def logAnnotations(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Map[String, String]] =
    getFiberRef(FiberRef.currentLogAnnotations)

  def logSpans(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, List[LogSpan]] =
    getFiberRef(FiberRef.currentLogSpan)

  def mergeAll[Env, InErr, InElem, InDone, OutErr, OutElem](
    channels: => ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Any],
      Any
    ],
    n: => Int,
    bufferSize: => Int = 16,
    mergeStrategy: => MergeStrategy = MergeStrategy.BackPressure
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Any] =
    mergeAllWith(channels, n, bufferSize, mergeStrategy)((_, _) => ())

  def mergeAllUnbounded[Env, InErr, InElem, InDone, OutErr, OutElem](
    channels: => ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Any],
      Any
    ]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Any] =
    mergeAll(channels, Int.MaxValue)

  def mergeAllUnboundedWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channels: => ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
      OutDone
    ]
  )(f: (OutDone, OutDone) => OutDone)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    mergeAllWith(channels, Int.MaxValue)(f)

  def mergeAllWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channels: ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
      OutDone
    ],
    n: => Int,
    bufferSize: => Int = 16,
    mergeStrategy: => MergeStrategy = MergeStrategy.BackPressure
  )(
    f: (OutDone, OutDone) => OutDone
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.MergeAllWith(trace, channels, n, bufferSize, mergeStrategy, f)

  def setFiberRef[Value](fiberRef: FiberRef[Value])(
    value: Value
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    modifyFiberRef(fiberRef)(_ => ((), value))

  def getFiberRef[Value](fiberRef: FiberRef[Value])(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Value] =
    modifyFiberRef(fiberRef)(value => (value, value))

  def modifyFiberRef[Value, Summary](
    fiberRef: FiberRef[Value]
  )(f: Value => (Summary, Value))(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Summary] =
    ZChannel.withFiberRuntime[Any, Any, Any, Any, Nothing, Nothing, Summary] { fiberRuntime =>
      val (summary, value) = f(fiberRuntime.getFiberRef(fiberRef)(Unsafe.unsafe))
      fiberRuntime.setFiberRef(fiberRef, value)(Unsafe.unsafe)
      ZChannel.succeedNow(summary)
    }

  def updateRuntimeFlags(
    patch: RuntimeFlags.Patch
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    if (patch == RuntimeFlags.Patch.empty) ZChannel.unit
    else ZChannel.UpdateRuntimeFlags(trace, patch)

  def runtimeFlags(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, RuntimeFlags] =
    ZChannel.withFiberRuntime[Any, Any, Any, Any, Nothing, Nothing, RuntimeFlags] { fiberRuntime =>
      ZChannel.succeedNow(fiberRuntime.getRuntimeFlags(Unsafe.unsafe))
    }

  /** Returns a channel that never completes */
  final def never(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Nothing] =
    ZChannel.fromZIO(ZIO.never)

  def provideLayer[Env0, Env, Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone](layer: ZLayer[Env0, OutErr, Env])(
    channel: => ZChannel[Env with Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit
    ev: EnvironmentTag[Env],
    tag: EnvironmentTag[Env1],
    trace: Trace
  ): ZChannel[Env0 with Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.suspend(channel.provideSomeLayer[Env0 with Env1](ZLayer.environment[Env1] ++ layer))

  def read[InElem](implicit trace: Trace): ZChannel[Any, Any, InElem, Any, None.type, Nothing, InElem] =
    readOrFail(None)

  def readOrFail[InErr, InElem](
    err: => InErr
  )(implicit trace: Trace): ZChannel[Any, Any, InElem, Any, InErr, Nothing, InElem] =
    readWith(
      elem => ZChannel.succeedNow(elem),
      _ => ZChannel.fail(err),
      _ => ZChannel.fail(err)
    )

  def readWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    in: InElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    error: InErr => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    done: InDone => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Read(trace, in, _.failureOrCause.fold(error, ZChannel.refailCause(_)), done)

  def readWithCause[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    in: InElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    halt: Cause[InErr] => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    done: InDone => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Read(trace, in, halt, done)

  def scoped[R]: ScopedPartiallyApplied[R] =
    new ScopedPartiallyApplied[R]

  def shift(executor: => Executor)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    ZChannel.withFiberRuntime[Any, Any, Any, Any, Nothing, Nothing, Unit] { fiberRuntime =>
      val newExecutor = executor
      fiberRuntime.getFiberRef(FiberRef.overrideExecutor)(Unsafe.unsafe) match {
        case None =>
          fiberRuntime.setFiberRef(FiberRef.overrideExecutor, Some(newExecutor))(Unsafe.unsafe)
          fiberRuntime.getRunningExecutor()(Unsafe.unsafe) match {
            case Some(runningExecutor) if runningExecutor == newExecutor => ZChannel.unit
            case _                                                       => ZChannel.yieldNow
          }
        case Some(overrideExecutor) =>
          if (overrideExecutor == newExecutor) ZChannel.unit
          else {
            fiberRuntime.setFiberRef(FiberRef.overrideExecutor, Some(newExecutor))(Unsafe.unsafe)
            ZChannel.yieldNow
          }
      }
    }

  def stackTrace(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, StackTrace] =
    ZChannel.GenerateStackTrace(trace)

  def unshift(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    ZChannel.setFiberRef(FiberRef.overrideExecutor)(None)

  def onExecutor[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](executor: => Executor)(
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.withFiberRuntime[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] { fiberRuntime =>
      val oldExecutor = fiberRuntime.getCurrentExecutor()(Unsafe.unsafe)
      val newExecutor = executor
      val isLocked    = fiberRuntime.getFiberRef(FiberRef.overrideExecutor)(Unsafe.unsafe).isDefined

      if (isLocked && oldExecutor == newExecutor)
        channel
      else if (isLocked)
        ZChannel.acquireReleaseWith[Env, InErr, InElem, InDone, OutErr, OutElem, Unit, OutDone](ZIO.shift(newExecutor))(
          _ => ZIO.shift(oldExecutor)
        )(_ => channel)
      else
        ZChannel.acquireReleaseWith[Env, InErr, InElem, InDone, OutErr, OutElem, Unit, OutDone](ZIO.shift(newExecutor))(
          _ => ZIO.unshift
        )(_ => channel)
    }

  def refailCause[E](cause: Cause[E]): ZChannel[Any, Any, Any, Any, E, Nothing, Nothing] =
    Fail(cause)

  /**
   * Accesses the specified service in the environment of the channel.
   */
  def service[Service: Tag](implicit
    trace: Trace
  ): ZChannel[Service, Any, Any, Any, Nothing, Nothing, Service] =
    ZChannel.fromZIO(ZIO.service)

  /**
   * Accesses the service corresponding to the specified key in the environment.
   */
  def serviceAt[Service]: ZChannel.ServiceAtPartiallyApplied[Service] =
    new ZChannel.ServiceAtPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the channel.
   */
  def serviceWith[Service]: ZChannel.ServiceWithPartiallyApplied[Service] =
    new ZChannel.ServiceWithPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the channel in the
   * context of a channel.
   */
  def serviceWithChannel[Service]: ZChannel.ServiceWithChannelPartiallyApplied[Service] =
    new ZChannel.ServiceWithChannelPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the channel in the
   * context of an effect.
   */
  def serviceWithZIO[Service]: ZChannel.ServiceWithZIOPartiallyApplied[Service] =
    new ZChannel.ServiceWithZIOPartiallyApplied[Service]

  def succeed[OutDone](done: => OutDone)(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, Nothing, Nothing, OutDone] =
    ZChannel.Succeed(trace, () => done)

  def succeedNow[OutDone](done: OutDone)(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, Nothing, Nothing, OutDone] =
    ZChannel.SucceedNow(done)

  def succeedWith[R, Z](f: ZEnvironment[R] => Z)(implicit
    trace: Trace
  ): ZChannel[R, Any, Any, Any, Nothing, Nothing, Z] =
    ZChannel.fromZIO(ZIO.environmentWith[R](f))

  def suspend[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Suspend(() => channel)

  def toHub[Err, Done, Elem](
    hub: => Hub[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZChannel[Any, Err, Elem, Done, Nothing, Nothing, Any] =
    toQueue(hub)

  def toQueue[Err, Done, Elem](
    queue: => Enqueue[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZChannel[Any, Err, Elem, Done, Nothing, Nothing, Any] =
    ZChannel.suspend {
      def toQueue(
        queue: Enqueue[Either[Exit[Err, Done], Elem]]
      ): ZChannel[Any, Err, Elem, Done, Nothing, Nothing, Any] =
        ZChannel.readWithCause(
          (in: Elem) => ZChannel.fromZIO(queue.offer(Right(in))) *> toQueue(queue),
          (cause: Cause[Err]) => ZChannel.fromZIO(queue.offer(Left(Exit.failCause(cause)))),
          (done: Done) => ZChannel.fromZIO(queue.offer(Left(Exit.succeed(done))))
        )

      toQueue(queue)
    }

  def uninterruptibleMask[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    f: ZChannel.InterruptibilityRestorer => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.UpdateRuntimeFlagsWithin.Dynamic(
      trace,
      RuntimeFlags.disable(RuntimeFlag.Interruption),
      runtimeFlags =>
        if (RuntimeFlags.interruption(runtimeFlags)) f(ZChannel.InterruptibilityRestorer.MakeInterruptible)
        else f(ZChannel.InterruptibilityRestorer.MakeUninterruptible)
    )

  val unit: ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    succeedNow(())(Trace.empty)

  def unwrap[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: => ZIO[Env, OutErr, ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.fromZIO(channel).flatten

  def unwrapScoped[Env]: UnwrapScopedPartiallyApplied[Env] =
    new UnwrapScopedPartiallyApplied[Env]

  def withFiberRuntime[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    onState: zio.stream.internal.ChannelFiberRuntime[
      InErr,
      InElem,
      InDone,
      OutErr,
      OutElem,
      OutDone
    ] => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Stateful(trace, onState)

  def withUpstream[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    f: ZChannel[Any, Any, Any, Any, InErr, InElem, InDone] => ZChannel[Env, Any, Any, Any, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.WithUpstream(trace, f)

  def write[OutElem](elem: OutElem)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, OutElem, Unit] =
    ZChannel.Emit(trace, elem)

  def writeAll[OutElem](elems: OutElem*)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, OutElem, Unit] =
    writeChunk(Chunk.fromIterable(elems))

  def writeChunk[OutElem](
    chunk: Chunk[OutElem]
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, OutElem, Unit] =
    chunk.foldLeft[ZChannel[Any, Any, Any, Any, Nothing, OutElem, Unit]](ZChannel.unit)((channel, elem) =>
      channel *> write(elem)
    )

  def yieldNow(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    ZChannel.YieldNow(trace)

  final class EnvironmentWithPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[OutDone](f: ZEnvironment[Env] => OutDone)(implicit
      trace: Trace
    ): ZChannel[Env, Any, Any, Any, Nothing, Nothing, OutDone] =
      ZChannel.environment.map(f)
  }

  final class EnvironmentWithChannelPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[Env1 <: Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      f: ZEnvironment[Env] => ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.environment.flatMap(f)
  }

  final class EnvironmentWithZIOPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[Env1 <: Env, OutErr, OutDone](f: ZEnvironment[Env] => ZIO[Env1, OutErr, OutDone])(implicit
      trace: Trace
    ): ZChannel[Env1, Any, Any, Any, OutErr, Nothing, OutDone] =
      ZChannel.environment.mapZIO(f)
  }

  final class ProvideSomeLayer[Env0, -Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {
    def apply[OutErr1 >: OutErr, Env1](
      layer: => ZLayer[Env0, OutErr1, Env1]
    )(implicit
      ev: Env0 with Env1 <:< Env,
      tagged: EnvironmentTag[Env1],
      trace: Trace
    ): ZChannel[Env0, InErr, InElem, InDone, OutErr1, OutElem, OutDone] =
      self
        .asInstanceOf[ZChannel[Env0 with Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
        .provideLayer(ZLayer.environment[Env0] ++ layer)
  }

  final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](
      zio: => ZIO[Scope with R, E, A]
    )(implicit trace: Trace): ZChannel[R, Any, Any, Any, E, A, Any] =
      ZChannel.unwrap {
        Scope.make.flatMap { scope =>
          scope.extend[R](zio).map(a => ZChannel.write(a).ensuringWith(scope.close(_)))
        }
      }
  }

  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Key](
      key: => Key
    )(implicit
      tag: EnvironmentTag[Map[Key, Service]],
      trace: Trace
    ): ZChannel[Map[Key, Service], Any, Any, Any, Nothing, Nothing, Option[Service]] =
      ZChannel.environmentWith(_.getAt(key))
  }

  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[OutDone](f: Service => OutDone)(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Service, Any, Any, Any, Nothing, Nothing, OutDone] =
      ZChannel.service[Service].map(f)
  }

  final class ServiceWithChannelPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Env <: Service, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      f: Service => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    )(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.service[Service].flatMap(f)
  }

  final class ServiceWithZIOPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Env <: Service, OutErr, OutDone](f: Service => ZIO[Env, OutErr, OutDone])(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Env, Any, Any, Any, OutErr, Nothing, OutDone] =
      ZChannel.service[Service].mapZIO(f)
  }

  final class UnwrapScopedPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {
    def apply[InErr, InElem, InDone, OutErr, OutElem, OutDone](
      channel: => ZIO[Scope with Env, OutErr, ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
    )(implicit
      trace: Trace
    ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.fromZIO(Scope.make).flatMap { scope =>
        ZChannel.unwrap(scope.extend[Env](channel)).ensuringWith(scope.close(_))
      }
  }

  final class UpdateService[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone, Service](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {
    def apply[Env1 <: Env with Service](
      f: Service => Service
    )(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      self.provideSomeEnvironment(_.update(f))
  }

  final class UpdateServiceAt[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone, Service](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {
    def apply[Env1 <: Env with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit
      tag: Tag[Map[Key, Service]],
      trace: Trace
    ): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

  sealed abstract class MergeDecision[-R, -E0, -Z0, +E, +Z]
  object MergeDecision {
    case class Done[R, E, Z](zio: ZIO[R, E, Z])                        extends MergeDecision[R, Any, Any, E, Z]
    case class Await[R, E0, Z0, E, Z](f: Exit[E0, Z0] => ZIO[R, E, Z]) extends MergeDecision[R, E0, Z0, E, Z]

    def done[R, E, Z](zio: ZIO[R, E, Z]): MergeDecision[R, Any, Any, E, Z]                      = Done(zio)
    def await[R, E0, Z0, E, Z](f: Exit[E0, Z0] => ZIO[R, E, Z]): MergeDecision[R, E0, Z0, E, Z] = Await(f)
    def awaitConst[R, E, Z](zio: ZIO[R, E, Z]): MergeDecision[R, Any, Any, E, Z]                = Await(_ => zio)
  }

  sealed trait MergeStrategy
  object MergeStrategy {
    case object BackPressure  extends MergeStrategy
    case object BufferSliding extends MergeStrategy
  }

  sealed trait InterruptibilityRestorer {
    def apply[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      channel: => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    def isParentRegionInterruptible: Boolean
    final def isParentRegionUninterruptible: Boolean = !isParentRegionInterruptible
    final def parentInterruptStatus: InterruptStatus = InterruptStatus.fromBoolean(isParentRegionInterruptible)
  }

  object InterruptibilityRestorer {
    case object MakeInterruptible extends InterruptibilityRestorer {
      def apply[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
        channel: => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
      )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
        ZChannel.UpdateRuntimeFlagsWithin.Interruptible(trace, channel)
      def isParentRegionInterruptible: Boolean = true
    }
    case object MakeUninterruptible extends InterruptibilityRestorer {
      def apply[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
        channel: => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
      )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
        ZChannel.UpdateRuntimeFlagsWithin.Uninterruptible(trace, channel)
      def isParentRegionInterruptible: Boolean = false
    }
  }

  private[stream] final case class UpdateRuntimeFlags(override val trace: Trace, update: RuntimeFlags.Patch)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit]

  private[stream] sealed trait UpdateRuntimeFlagsWithin[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]
      extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] {
    def update: RuntimeFlags.Patch
    def scope(runtimeFlags: RuntimeFlags): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  }

  private[stream] object UpdateRuntimeFlagsWithin {

    final case class Interruptible[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      override val trace: Trace,
      channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    ) extends UpdateRuntimeFlagsWithin[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] {
      def update: RuntimeFlags.Patch                                                                        = RuntimeFlags.enable(RuntimeFlag.Interruption)
      def scope(runtimeFlags: RuntimeFlags): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] = channel
    }

    final case class Uninterruptible[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      override val trace: Trace,
      channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    ) extends UpdateRuntimeFlagsWithin[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] {
      def update: RuntimeFlags.Patch                                                                        = RuntimeFlags.disable(RuntimeFlag.Interruption)
      def scope(runtimeFlags: RuntimeFlags): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] = channel
    }

    final case class Dynamic[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      override val trace: Trace,
      patch: RuntimeFlags.Patch,
      f: RuntimeFlags => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    ) extends UpdateRuntimeFlagsWithin[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] {
      def update: RuntimeFlags.Patch = patch
      def scope(runtimeFlags: RuntimeFlags): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] = f(
        runtimeFlags
      )
    }
  }

  private[stream] def fromRuntimeFiber[Err, Elem, Done](
    fiber: ChannelFiberRuntime[Nothing, Nothing, Nothing, Err, Elem, Done],
    epoch: Long
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
    fiber
      .read(epoch)
      .foldCauseChannel(
        cause => ZChannel.refailCause(cause),
        {
          case Left(done)  => ZChannel.succeedNow(done)
          case Right(elem) => ZChannel.write(elem) *> fromRuntimeFiber(fiber, epoch)
        }
      )

  private[stream] def mergeAllFibers[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    parent: ChannelFiberRuntime[
      Any,
      Any,
      Any,
      OutErr,
      ChannelFiberRuntime[Any, Any, Any, OutErr, OutElem, OutDone],
      OutDone
    ],
    n: Int,
    bufferSize: Int,
    mergeStrategy: MergeStrategy,
    f: (OutDone, OutDone) => OutDone
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.async { downstream =>
      final case class State(
        exit: Exit[OutErr, OutDone],
        parent: Option[ChannelFiberRuntime[
          Any,
          Any,
          Any,
          OutErr,
          ChannelFiberRuntime[Any, Any, Any, OutErr, OutElem, OutDone],
          OutDone
        ]],
        nextChild: Option[ChannelFiberRuntime[Any, Any, Any, OutErr, OutElem, OutDone]],
        runningChildren: List[ChannelFiberRuntime[Any, Any, Any, OutErr, OutElem, OutDone]],
        interruptingChildren: List[ChannelFiberRuntime[Any, Any, Any, OutErr, OutElem, OutDone]]
      )

      val state = new java.util.concurrent.atomic.AtomicReference(State(null, Some(parent), None, Nil, Nil))

      def getAndUpdate(f: State => State): State = {
        var loop           = true
        var current: State = null
        while (loop) {
          current = state.get
          val next = f(current)
          loop = !state.compareAndSet(current, next)
        }
        current
      }

      def combine(left: Exit[OutErr, OutDone], right: Exit[OutErr, OutDone]): Exit[OutErr, OutDone] =
        if (left eq null) right
        else left.zipWith(right)(f, _ && _)

      def readParent: Unit =
        parent.unsafeRead(
          err => {
            val currentState = getAndUpdate { case State(exit, _, _, runningChildren, interruptingChildren) =>
              State(
                combine(exit, Exit.failCause(err)),
                None,
                None,
                List.empty,
                runningChildren ::: interruptingChildren
              )
            }
            currentState match {
              case State(exit, _, _, runningChildren, interruptingChildren) =>
                if (runningChildren.isEmpty && interruptingChildren.isEmpty) {
                  combine(exit, Exit.failCause(err)).foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                } else {
                  runningChildren.foreach(_.unsafeInterruptAsFork(FiberId.None))
                }
            }
          },
          child => {
            val currentState =
              getAndUpdate { case State(exit, parent, nextChild, runningChildren, interruptedChildren) =>
                if ((exit eq null) || exit.isSuccess) {
                  if (runningChildren.length + interruptedChildren.length < n) {
                    State(exit, parent, None, child :: runningChildren, interruptedChildren)
                  } else {
                    mergeStrategy match {
                      case MergeStrategy.BackPressure =>
                        State(exit, parent, Some(child), runningChildren, interruptedChildren)
                      case MergeStrategy.BufferSliding =>
                        if (runningChildren.length == n) {
                          State(
                            exit,
                            parent,
                            Some(child),
                            runningChildren.dropRight(1),
                            runningChildren.takeRight(1) ::: interruptedChildren
                          )
                        } else {
                          State(exit, parent, Some(child), runningChildren, interruptedChildren)
                        }
                    }
                  }
                } else {
                  State(exit, parent, None, List.empty, interruptedChildren)
                }
              }
            currentState match {
              case State(exit, parent, nextChild, runningChildren, interruptedChildren) =>
                if ((exit eq null) || exit.isSuccess) {
                  if (runningChildren.length + interruptedChildren.length < n) {
                    readChild(child)
                    readParent
                  } else {
                    mergeStrategy match {
                      case MergeStrategy.BackPressure =>
                        ()
                      case MergeStrategy.BufferSliding =>
                        if (runningChildren.length == n) {
                          runningChildren.takeRight(1).foreach(_.unsafeInterruptAsFork(FiberId.None))
                        }
                    }
                  }
                } else {
                  if (runningChildren.isEmpty && interruptedChildren.isEmpty) {
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  }
                }
            }
          },
          done => {
            val currentState = getAndUpdate { case State(exit, _, nextChild, runningChildren, interruptingChildren) =>
              State(combine(exit, Exit.succeed(done)), None, nextChild, runningChildren, interruptingChildren)
            }
            currentState match {
              case State(exit, _, nextChild, runningChildren, interruptingChildren) =>
                if (nextChild.isEmpty && runningChildren.isEmpty && interruptingChildren.isEmpty) {
                  combine(exit, Exit.succeed(done)).foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                }
            }
          },
          0L
        )

      def readChild(child: ChannelFiberRuntime[Any, Any, Any, OutErr, OutElem, OutDone]): Unit =
        child.unsafeRead(
          err => {
            val currentState =
              getAndUpdate { case State(exit, parent, nextChild, runningChildren, interruptingChildren) =>
                if (err.isInterruptedOnly) {
                  if (runningChildren.length + interruptingChildren.length == n) {
                    State(
                      exit,
                      parent,
                      None,
                      nextChild.toList ::: runningChildren.filterNot(_ eq child),
                      interruptingChildren.filterNot(_ eq child)
                    )
                  } else {
                    State(
                      exit,
                      parent,
                      nextChild,
                      runningChildren.filterNot(_ eq child),
                      interruptingChildren.filterNot(_ eq child)
                    )
                  }
                } else {
                  State(
                    combine(exit, Exit.failCause(err)),
                    None,
                    None,
                    List.empty,
                    (runningChildren ::: interruptingChildren).filterNot(_ eq child)
                  )
                }
              }
            currentState match {
              case State(exit, parent, nextChild, runningChildren, interruptingChildren) =>
                if (err.isInterruptedOnly) {
                  if (
                    nextChild.isEmpty && parent.isEmpty && runningChildren
                      .filterNot(_ eq child)
                      .isEmpty && interruptingChildren
                      .filterNot(_ eq child)
                      .isEmpty
                  ) {
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  } else {
                    if (runningChildren.length + interruptingChildren.length == n) {
                      nextChild match {
                        case Some(child) =>
                          readChild(child)
                          readParent
                        case None =>
                          ()
                      }
                    }
                  }
                } else {
                  if (
                    parent.isEmpty && runningChildren.filterNot(_ eq child).isEmpty && interruptingChildren
                      .filterNot(_ eq child)
                      .isEmpty
                  ) {
                    combine(exit, Exit.failCause(err)).foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  } else {
                    parent.foreach(_.unsafeInterruptAsFork(FiberId.None))
                    runningChildren.filterNot(_ eq child).foreach(_.unsafeInterruptAsFork(FiberId.None))
                  }
                }
            }
          },
          elem => {
            downstream.unsafeWriteElem(elem)
            readChild(child)
          },
          done => {
            val currentState =
              getAndUpdate { case State(exit, parent, nextChild, runningChildren, interruptingChildren) =>
                if (runningChildren.length + interruptingChildren.length == n) {
                  State(
                    combine(exit, Exit.succeed(done)),
                    parent,
                    None,
                    nextChild.toList ::: runningChildren.filterNot(_ eq child),
                    interruptingChildren.filterNot(_ eq child)
                  )
                } else {
                  State(
                    combine(exit, Exit.succeed(done)),
                    parent,
                    nextChild,
                    runningChildren.filterNot(_ eq child),
                    interruptingChildren.filterNot(_ eq child)
                  )
                }
              }
            currentState match {
              case State(exit, parent, nextChild, runningChildren, interruptingChildren) =>
                if (
                  nextChild.isEmpty && parent.isEmpty && runningChildren
                    .filterNot(_ eq child)
                    .isEmpty && interruptingChildren
                    .filterNot(_ eq child)
                    .isEmpty
                ) {
                  combine(exit, Exit.succeed(done)).foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                } else {
                  if (runningChildren.length + interruptingChildren.length == n) {
                    nextChild match {
                      case Some(child) =>
                        readChild(child)
                        readParent
                      case None =>
                        ()
                    }
                  }
                }
            }
          },
          0L
        )

      readParent
    }

  private[stream] def mergeAllFibersOrdered[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    fibers: List[ChannelFiberRuntime[Any, Any, Any, OutErr, (OutElem, Long), OutDone]]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.async { downstream =>
      final case class MergeState(
        exit: Exit[OutErr, OutDone],
        runningFibers: List[ChannelFiberRuntime[Any, Any, Any, OutErr, (OutElem, Long), OutDone]],
        interruptingFibers: List[ChannelFiberRuntime[Any, Any, Any, OutErr, (OutElem, Long), OutDone]],
        completed: List[(OutElem, Long)],
        writing: List[(OutElem, Long)],
        index: Long
      )

      val state = new java.util.concurrent.atomic.AtomicReference(MergeState(null, fibers, Nil, Nil, Nil, 0L))

      def getAndUpdate(f: MergeState => MergeState): MergeState = {
        var loop                = true
        var current: MergeState = null
        while (loop) {
          current = state.get
          val next = f(current)
          loop = !state.compareAndSet(current, next)
        }
        current
      }

      val writing =
        new java.util.concurrent.atomic.AtomicBoolean(false)

      def combine(left: Exit[OutErr, OutDone], right: Exit[OutErr, OutDone]): Exit[OutErr, OutDone] =
        if (left eq null) right
        else left.zipWith(right)((_, r) => r, (l, r) => if (r.isInterruptedOnly) l else l && r)

      def update(state: MergeState): (List[OutElem], MergeState) = {

        def loop(
          completed: List[(OutElem, Long)],
          index: Long,
          toWrite: List[OutElem]
        ): (List[OutElem], List[(OutElem, Long)], Long) =
          completed match {
            case (elem, i) :: tail if i == index =>
              loop(tail, index + 1, elem :: toWrite)
            case _ =>
              (toWrite.reverse, completed, index)
          }

        val (toWrite, newCompleted, newIndex) = loop(state.completed.sortBy(_._2), state.index, Nil)
        (toWrite, state.copy(completed = newCompleted))
      }

      def writeLoop: Unit = {
        val currentState = getAndUpdate { state =>
          update(state)._2
        }
        val (toWrite, _) = update(currentState)
        toWrite.foreach(downstream.unsafeWriteElem)
        val updatedState = getAndUpdate { state =>
          state.copy(index = state.index + toWrite.length)
        }
      }

      def tryWrite: Unit =
        if (writing.compareAndSet(false, true)) {
          writeLoop
          writing.set(false)
          val updatedState = state.get
          val (toWrite, _) = update(updatedState)
          if (toWrite.nonEmpty) {
            tryWrite
          }
        }

      def read(fiber: ChannelFiberRuntime[Any, Any, Any, OutErr, (OutElem, Long), OutDone]): Unit =
        fiber.unsafeRead(
          err => {
            val currentState = getAndUpdate {
              case MergeState(exit, runningFibers, interruptingFibers, completed, writing, index) =>
                MergeState(
                  combine(exit, Exit.failCause(err)),
                  Nil,
                  (runningFibers ::: interruptingFibers).filterNot(_ eq fiber),
                  completed,
                  writing,
                  index
                )
            }
            currentState match {
              case MergeState(exit, runningFibers, interruptingFibers, completed, writing, index) =>
                if (runningFibers.filterNot(_ eq fiber).isEmpty && interruptingFibers.filterNot(_ eq fiber).isEmpty) {
                  combine(exit, Exit.failCause(err)).foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                } else {
                  runningFibers.foreach(_.unsafeInterruptAsFork(FiberId.None))
                }
            }
          },
          { case (elem, index0) =>
            val currentState =
              getAndUpdate { case MergeState(exit, runningFibers, interruptingFibers, completed, writing, index) =>
                MergeState(exit, runningFibers, interruptingFibers, (elem -> index0) :: completed, writing, index)
              }
            tryWrite
            read(fiber)
          },
          done => {
            val currentState = getAndUpdate {
              case MergeState(exit, runningFibers, interruptingFibers, completed, writing, index) =>
                MergeState(
                  combine(exit, Exit.succeed(done)),
                  runningFibers.filterNot(_ eq fiber),
                  interruptingFibers.filterNot(_ eq fiber),
                  completed,
                  writing,
                  index
                )
            }
            currentState match {
              case MergeState(exit, runningFibers, interruptingFibers, completed, writing, index) =>
                if (runningFibers.filterNot(_ eq fiber).isEmpty && interruptingFibers.filterNot(_ eq fiber).isEmpty) {
                  combine(exit, Exit.succeed(done)).foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                }
            }
          },
          0L
        )

      fibers.foreach(read)
    }

  private[stream] def mergeFibers[Env, Err, Err2, Err3, Elem, Done, Done2, Done3](
    left: ChannelFiberRuntime[Nothing, Nothing, Nothing, Err, Elem, Done],
    right: ChannelFiberRuntime[Nothing, Nothing, Nothing, Err2, Elem, Done2]
  )(
    leftDone: Exit[Err, Done] => ZChannel.MergeDecision[Env, Err2, Done2, Err3, Done3],
    rightDone: Exit[Err2, Done2] => ZChannel.MergeDecision[Env, Err, Done, Err3, Done3]
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, Err3, Elem, Done3] =
    ZChannel.async[Err3, Elem, Done3] { downstream =>
      val completed = new java.util.concurrent.atomic.AtomicInteger(0)
      @volatile var mergeDecision
        : Either[MergeDecision[Env, Err2, Done2, Err3, Done3], MergeDecision[Env, Err, Done, Err3, Done3]] = null
      val exit                                                                                             = zio.internal.OneShot.make[Exit[Err3, Done3]]

      sealed trait MergeState

      case object Running                                                                             extends MergeState
      final case class LeftDone(mergeDecision: ZChannel.MergeDecision[Env, Err2, Done2, Err3, Done3]) extends MergeState
      final case class RightDone(mergeDecision: ZChannel.MergeDecision[Env, Err, Done, Err3, Done3])  extends MergeState

      val state = new java.util.concurrent.atomic.AtomicReference[MergeState](Running)

      def runZIO(zio: ZIO[Env, Err3, Done3]): Exit[Err3, Done3] =
        Runtime.default.unsafe.run(zio.asInstanceOf[ZIO[Any, Err3, Done3]])(Trace.empty, Unsafe.unsafe)

      def readLeft: Unit =
        left.unsafeRead(
          err => {
            val mergeDecision = leftDone(Exit.failCause(err))
            val currentState  = state.getAndSet(LeftDone(mergeDecision))
            currentState match {
              case Running =>
                mergeDecision match {
                  case MergeDecision.Done(_) => right.unsafeInterruptAsFork(FiberId.None)
                  case _                     =>
                }
              case RightDone(mergeDecision) =>
                mergeDecision match {
                  case MergeDecision.Await(f) =>
                    val zio  = f(Exit.failCause(err))
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  case MergeDecision.Done(zio) =>
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                }
              case _ =>
            }
          },
          elem => {
            downstream.unsafeWriteElem(elem, readLeft)
          },
          done => {
            val mergeDecision = leftDone(Exit.succeed(done))
            val currentState  = state.getAndSet(LeftDone(mergeDecision))
            currentState match {
              case Running =>
                mergeDecision match {
                  case MergeDecision.Done(_) => right.unsafeInterruptAsFork(FiberId.None)
                  case _                     =>
                }
              case RightDone(mergeDecision) =>
                mergeDecision match {
                  case MergeDecision.Await(f) =>
                    val zio  = f(Exit.succeed(done))
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  case MergeDecision.Done(zio) =>
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                }
              case _ =>
            }
          },
          0L
        )

      def readRight: Unit =
        right.unsafeRead(
          err => {
            val mergeDecision = rightDone(Exit.failCause(err))
            val currentState  = state.getAndSet(RightDone(mergeDecision))
            currentState match {
              case Running =>
                mergeDecision match {
                  case MergeDecision.Done(_) => left.unsafeInterruptAsFork(FiberId.None)
                  case _                     =>
                }
              case LeftDone(mergeDecision) =>
                mergeDecision match {
                  case MergeDecision.Await(f) =>
                    val zio  = f(Exit.failCause(err))
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  case MergeDecision.Done(zio) =>
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                }
              case _ =>
            }
          },
          elem => {
            downstream.unsafeWriteElem(elem, readRight)
          },
          done => {
            val mergeDecision = rightDone(Exit.succeed(done))
            val currentState  = state.getAndSet(RightDone(mergeDecision))
            currentState match {
              case Running =>
                mergeDecision match {
                  case MergeDecision.Done(_) =>
                    left.unsafeInterruptAsFork(FiberId.None)
                  case _ =>
                }
              case LeftDone(mergeDecision) =>
                mergeDecision match {
                  case MergeDecision.Await(f) =>
                    val zio  = f(Exit.succeed(done))
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                  case MergeDecision.Done(zio) =>
                    val exit = runZIO(zio)
                    exit.foldExit(downstream.unsafeWriteErr, downstream.unsafeWriteDone)
                }
              case _ =>
            }
          },
          0L
        )
      readLeft
      readRight
    }

  private[stream] final case class Async[Env, Err, Done](
    override val trace: Trace,
    registerCallback: (
      ZChannel[Env, Any, Any, Any, Err, Nothing, Done] => Unit
    ) => ZChannel[Env, Any, Any, Any, Err, Nothing, Done]
  ) extends ZChannel[Env, Any, Any, Any, Err, Nothing, Done]

  private[stream] final case class ConcatAll[
    Env,
    InErr,
    InElem,
    InDone,
    OutErr,
    OutElem,
    OutElem2,
    OutDone,
    OutDone2,
    OutDone3
  ](
    override val trace: Trace,
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    onElem: OutElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone2],
    combineInner: (OutDone2, OutDone2) => OutDone2,
    combinerOuter: (OutDone2, OutDone) => OutDone3
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone3]

  private[stream] final case class Emit[OutElem](override val trace: Trace, elem: OutElem)
      extends ZChannel[Any, Any, Any, Any, Nothing, OutElem, Unit]

  private[stream] final case class Ensuring[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    override val trace: Trace,
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    finalizer: Exit[OutErr, OutDone] => ZIO[Env, Nothing, Any]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class Fail[OutErr](err: Cause[OutErr])
      extends ZChannel[Any, Any, Any, Any, OutErr, Nothing, Nothing]

  private[stream] final case class Fold[Env, InErr, InElem, InDone, OutErr, OutErr2, OutElem, OutDone, OutDone2](
    override val trace: Trace,
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    onErr: Cause[OutErr] => ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2],
    onDone: OutDone => ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2]

  private[stream] final case class FromZIO[Env, Err, Done](override val trace: Trace, zio: ZIO[Env, Err, Done])
      extends ZChannel[Env, Any, Any, Any, Err, Nothing, Done]

  private[stream] final case class GenerateStackTrace(override val trace: Trace)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, StackTrace]

  private[stream] final case class MapOutZIOPar[Env, InErr, InElem, InDone, OutErr, OutElem, OutElem2, OutDone](
    override val trace: Trace,
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    n: Int,
    f: OutElem => ZIO[Env, OutErr, OutElem2]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone]

  private[stream] final case class MergeAllWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    override val trace: Trace,
    channels: ZChannel[
      Env,
      InErr,
      InElem,
      InDone,
      OutErr,
      ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
      OutDone
    ],
    n: Int,
    bufferSize: Int,
    mergeStrategy: MergeStrategy,
    f: (OutDone, OutDone) => OutDone
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class MergeWith[
    Env,
    InErr,
    InElem,
    InDone,
    OutErr,
    OutErr2,
    OutErr3,
    OutElem,
    OutDone,
    OutDone2,
    OutDone3
  ](
    override val trace: Trace,
    left: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    right: ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2],
    leftDone: Exit[OutErr, OutDone] => ZChannel.MergeDecision[Env, OutErr2, OutDone2, OutErr3, OutDone3],
    rightDone: Exit[OutErr2, OutDone2] => ZChannel.MergeDecision[Env, OutErr, OutDone, OutErr3, OutDone3]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr3, OutElem, OutDone3]

  private[stream] final case class PipeTo[
    Env,
    InErr,
    InElem,
    InDone,
    OutErr,
    OutErr2,
    OutElem,
    OutElem2,
    OutDone,
    OutDone2
  ](
    override val trace: Trace,
    left: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    right: ZChannel[Env, OutErr, OutElem, OutDone, OutErr2, OutElem2, OutDone2]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2]

  private[stream] final case class Provide[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    override val trace: Trace,
    environment: ZEnvironment[Env],
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class Read[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    override val trace: Trace,
    onElem: InElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    onErr: Cause[InErr] => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    onDone: InDone => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class Stateful[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone, State](
    override val trace: Trace,
    onState: zio.stream.internal.ChannelFiberRuntime[
      InErr,
      InElem,
      InDone,
      OutErr,
      OutElem,
      OutDone
    ] => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class Succeed[OutDone](override val trace: Trace, done: () => OutDone)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, OutDone]

  private[stream] final case class SucceedNow[OutDone](done: OutDone)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, OutDone]

  private[stream] final case class Suspend[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: () => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class WithUpstream[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    override val trace: Trace,
    f: ZChannel[Any, Any, Any, Any, InErr, InElem, InDone] => ZChannel[Env, Any, Any, Any, OutErr, OutElem, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[stream] final case class YieldNow(override val trace: Trace)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit]
}
