package zio.stream

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZChannel.unitFn2
import zio.stream.internal.ChannelExecutor.ChannelState
import zio.stream.internal.{AsyncInputConsumer, AsyncInputProducer, ChannelExecutor, SingleProducerAsyncInput}

import java.util.concurrent.atomic.AtomicReference

/**
 * A `ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]` is a nexus
 * of I/O operations, which supports both reading and writing. A channel may
 * read values of type `InElem` or process upstream failures of type `InErr`,
 * while it may write values of type `OutElem`. When the channel finishes, it
 * yields a value of type `OutDone`. A channel may fail with a value of type
 * `OutErr`.
 *
 * Channels are the foundation of ZIO Streams: both streams and sinks are built
 * on channels. Most users shouldn't have to use channels directly, as streams
 * and sinks are much more convenient and cover all common use cases. However,
 * when adding new stream and sink operators, or doing something highly
 * specialized, it may be useful to use channels directly.
 *
 * Channels compose in a variety of ways:
 *
 *   - Piping. One channel can be piped to another channel, assuming the input
 *     type of the second is the same as the output type of the first.
 *   - Sequencing. The terminal value of one channel can be used to create
 *     another channel, and both the first channel and the function that makes
 *     the second channel can be composed into a channel.
 *   - Concating. The output of one channel can be used to create other
 *     channels, which are all concatenated together. The first channel and the
 *     function that makes the other channels can be composed into a channel.
 *
 * This example pipes three elements into a channel that reads each element and
 * writes it doubled:
 *
 * {{{
 * import zio._
 *
 * lazy val doubler: ZChannel[Any, String, Int, Any, String, Int, Any] =
 *   ZChannel.readWith(
 *     (n: Int) => ZChannel.write(n * 2) *> doubler,
 *     (err: String) => ZChannel.fail(err),
 *     (done: Any) => ZChannel.succeed(done)
 *   )
 *
 * val result: IO[String, (Chunk[Int], Any)] =
 *   (ZChannel.writeAll(1, 2, 3) >>> doubler).runCollect
 * // (Chunk(2,4,6),())
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stream/zchannel/ ZChannel]]
 */
sealed trait ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone] { self =>

  /**
   * Returns a new channel that is the sequential composition of this channel
   * and the specified channel. The returned channel terminates with both
   * terminal values combined by `Zippable`: `Unit` values are dropped and
   * nested tuples are flattened, so `1` and `()` give `1`, and `(1, "a")` and
   * `true` give `(1, "a", true)`.
   *
   * This is a symbol operator for [[ZChannel#zip]].
   */
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
    self.flatMap(z => that.map(z2 => zippable.zip(z, z2)))

  /**
   * Returns a new channel that is the sequential composition of this channel
   * and the specified channel. The returned channel terminates with the
   * terminal value of the other channel.
   *
   * This is a symbol operator for [[ZChannel#zipRight]].
   */
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
    self.flatMap(_ => that)

  /**
   * Returns a new channel that is the sequential composition of this channel
   * and the specified channel. The returned channel terminates with the
   * terminal value of this channel.
   *
   * This is a symbol operator for [[ZChannel#zipLeft]].
   */
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
    self.flatMap(z => that.as(z))

  /**
   * Returns a new channel that pipes the output of this channel into the
   * specified channel. The returned channel has the input type of this channel,
   * and the output type of the specified channel, terminating with the value of
   * the specified channel.
   *
   * This is a symbolic operator for [[ZChannel#pipeTo]].
   */
  final def >>>[Env1 <: Env, OutErr2, OutElem2, OutDone2](
    that: => ZChannel[Env1, OutErr, OutElem, OutDone, OutErr2, OutElem2, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2] =
    self pipeTo that

  /**
   * Returns a new channel that is the same as this one, except the terminal
   * value of the channel is the specified constant value.
   *
   * This method produces the same result as mapping this channel to the
   * specified constant value.
   */
  final def as[OutDone2](z2: => OutDone2)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2] =
    self.map(_ => z2)

  /**
   * Returns a new channel that is the same as this one, except if this channel
   * errors for any typed error, then the returned channel will switch over to
   * using the fallback channel returned by the specified error handler.
   *
   * A cause without a typed error (a defect or an interruption) is propagated
   * unchanged.
   */
  final def catchAll[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone1 >: OutDone
  ](
    f: OutErr => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1] =
    catchAllCause((cause: Cause[OutErr]) => cause.failureOrCause.fold(f(_), ZChannel.refailCause))

  /**
   * Returns a new channel that is the same as this one, except if this channel
   * fails with a cause, then the returned channel switches over to the fallback
   * channel returned by the specified error handler.
   *
   * The handler receives typed errors, defects and interruption causes that the
   * channel fails with (for example through [[ZChannel.interruptAs]]). It does
   * not run when the fiber running the channel is interrupted from outside.
   */
  final def catchAllCause[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr2,
    OutElem1 >: OutElem,
    OutDone1 >: OutDone
  ](
    f: Cause[OutErr] => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone1] =
    ZChannel.Fold(self, new ZChannel.Fold.K(ZChannel.Fold.successIdentity[OutDone1], f))

  /**
   * Returns a new channel whose outputs are fed to the specified factory
   * function, which creates new channels in response. These new channels are
   * sequentially concatenated together, and all their outputs appear as outputs
   * of the newly returned channel.
   */
  final def concatMap[Env1 <: Env, InErr1 <: InErr, InElem1 <: InElem, InDone1 <: InDone, OutErr1 >: OutErr, OutElem2](
    f: OutElem => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, Any] =
    concatMapWith(f)(unitFn2, unitFn2)

  /**
   * Returns a new channel whose outputs are fed to the specified factory
   * function, which creates new channels in response. These new channels are
   * sequentially concatenated together, and all their outputs appear as outputs
   * of the newly returned channel.
   *
   * `g` combines the terminal values of the created channels, in order. `h`
   * then combines that result with the terminal value of this channel to give
   * the terminal value of the returned channel.
   */
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
    f: OutElem => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, OutDone2]
  )(
    g: (OutDone2, OutDone2) => OutDone2,
    h: (OutDone2, OutDone) => OutDone3
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem2, OutDone3] =
    ZChannel.ConcatAll(
      g,
      h,
      () => self,
      f
    )

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
   * Returns a new channel which is the same as this one but handles a typed
   * error from upstream with the given ZIO function.
   *
   * When upstream fails with a typed error, `f` runs: if it succeeds, this
   * channel sees upstream finish with that value; if it fails, this channel
   * sees that failure as the upstream error. An upstream cause without a typed
   * error (a defect or an interruption) reaches this channel unchanged.
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

  /**
   * Returns a new channel which discards every element that this channel
   * writes, then terminates with the terminal value of this channel.
   */
  final def drain(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, Nothing, OutDone] = {
    lazy val drainer: ZChannel[Env, OutErr, OutElem, OutDone, OutErr, Nothing, OutDone] =
      ZChannel.readWithCause(
        (_: OutElem) => drainer,
        (e: Cause[OutErr]) => ZChannel.refailCause(e),
        (z: OutDone) => ZChannel.succeedNow(z)
      )

    self.pipeTo(drainer)
  }

  /**
   * Returns a new channel which connects the given [[AsyncInputProducer]] as
   * this channel's input
   */
  private[zio] final def embedInput[InErr2, InElem2, InDone2](input: AsyncInputProducer[InErr2, InElem2, InDone2])(
    implicit
    noInputErrors: Any <:< InErr,
    noInputElements: Any <:< InElem,
    noInputDone: Any <:< InDone,
    trace: Trace
  ): ZChannel[Env, InErr2, InElem2, InDone2, OutErr, OutElem, OutDone] =
    ZChannel.Bridge(input, self.asInstanceOf[ZChannel[Env, Any, Any, Any, OutErr, OutElem, OutDone]])

  /**
   * Returns a new channel, which is the same as this one, except it will be
   * interrupted when the specified effect completes. If the effect completes
   * successfully before the underlying channel is done, then the returned
   * channel will yield the success value of the effect as its terminal value.
   * On the other hand, if the underlying channel finishes first, then the
   * returned channel will yield the success value of the underlying channel as
   * its terminal value.
   *
   * If the effect fails first, the returned channel fails with its error. If
   * the underlying channel finishes or fails first, the effect is interrupted.
   */
  final def interruptWhen[Env1 <: Env, OutErr1 >: OutErr, OutDone1 >: OutDone](
    io: ZIO[Env1, OutErr1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem, OutDone1] =
    self.mergeWith(ZChannel.fromZIO(io))(
      ZChannel.MergeDecision.done,
      ZChannel.MergeDecision.done
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

  /**
   * Returns a new channel that collects the output and terminal value of this
   * channel, which it then writes as output of the returned channel.
   */
  final def emitCollect(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, (Chunk[OutElem], OutDone), Unit] =
    collectElements.flatMap(t => ZChannel.write(t))

  /**
   * Returns a new channel with an attached finalizer. The finalizer is
   * guaranteed to be executed so long as the channel begins execution (and
   * regardless of whether or not it completes).
   */
  final def ensuringWith[Env1 <: Env](
    finalizer: Exit[OutErr, OutDone] => URIO[Env1, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Ensuring(self, finalizer)

  /**
   * Returns a new channel with an attached finalizer. The finalizer is
   * guaranteed to be executed so long as the channel begins execution (and
   * regardless of whether or not it completes).
   */
  final def ensuring[Env1 <: Env](
    finalizer: => URIO[Env1, Any]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ensuringWith(_ => finalizer)

  /**
   * Returns a new channel, which sequentially combines this channel, together
   * with the provided factory function, which creates a second channel based on
   * the terminal value of this channel. The result is a channel that will first
   * perform the functions of this channel, before performing the functions of
   * the created channel (including yielding its terminal value).
   */
  final def flatMap[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1 >: OutErr,
    OutElem1 >: OutElem,
    OutDone2
  ](
    f: OutDone => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone2] =
    ZChannel.Fold(self, new ZChannel.Fold.K(f, ZChannel.Fold.failCauseIdentity[OutErr1]))

  /**
   * Returns a new channel, which flattens the terminal value of this channel.
   * This function may only be called if the terminal value of this channel is
   * another channel of compatible types.
   */
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
    ZChannel.Fold(
      self,
      ZChannel.Fold.foldKIdentity[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone, OutDone2]
    )

  /**
   * Folds over the result of this channel: `onSucc` receives the terminal value
   * and `onErr` receives a typed error. A failure without a typed error (a
   * defect or an interruption) is propagated unchanged.
   */
  final def foldChannel[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1,
    OutElem1 >: OutElem,
    OutDone1
  ](
    onErr: OutErr => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone1],
    onSucc: OutDone => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone1] =
    foldCauseChannel(
      _.failureOrCause match {
        case Left(err)    => onErr(err)
        case Right(cause) => ZChannel.refailCause(cause)
      },
      onSucc
    )

  /**
   * Folds over the result of this channel including any cause of termination:
   * `onSucc` receives the terminal value and `onErr` receives the full cause of
   * a failure.
   */
  final def foldCauseChannel[
    Env1 <: Env,
    InErr1 <: InErr,
    InElem1 <: InElem,
    InDone1 <: InDone,
    OutErr1,
    OutElem1 >: OutElem,
    OutDone1
  ](
    onErr: Cause[OutErr] => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone1],
    onSucc: OutDone => ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone1]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone1] =
    ZChannel.Fold(self, new ZChannel.Fold.K(onSucc, onErr))

  /**
   * Returns a new channel that will perform the operations of this one, until
   * it fails with a typed error, and then it will switch over to the operations
   * of the specified fallback channel. A failure without a typed error (a
   * defect or an interruption) is propagated unchanged.
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

  /**
   * Returns a new channel, which is the same as this one, except the terminal
   * value of the returned channel is created by applying the specified function
   * to the terminal value of this channel.
   */
  final def map[OutDone2](f: OutDone => OutDone2)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2] =
    self.flatMap(z => ZChannel.succeed(f(z)))

  /**
   * Returns a new channel, which is the same as this one, except the failure
   * value of the returned channel is created by applying the specified function
   * to the failure value of this channel.
   */
  final def mapError[OutErr2](f: OutErr => OutErr2)(implicit
    trace: Trace
  ): ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone] =
    mapErrorCause(_.map(f))

  /**
   * Returns a new channel, which is the same as this one, except the failure
   * cause of the returned channel is created by applying the specified function
   * to the full [[Cause]] of this channel's failure.
   */
  final def mapErrorCause[OutErr2](
    f: Cause[OutErr] => Cause[OutErr2]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone] = {
    lazy val mapErrorCause: ZChannel[Env, OutErr, OutElem, OutDone, OutErr2, OutElem, OutDone] =
      ZChannel.readWithCause(
        chunk => ZChannel.write(chunk) *> mapErrorCause,
        cause => ZChannel.refailCause(f(cause)),
        done => ZChannel.succeedNow(done)
      )

    self >>> mapErrorCause
  }

  /**
   * Returns a new channel, which is the same as this one, except the failure
   * value of the returned channel is the result of the specified effect, run on
   * the typed error of this channel. A failure without a typed error (a defect
   * or an interruption) is propagated unchanged.
   */
  final def mapErrorZIO[Env1 <: Env, OutErr2](
    f: OutErr => URIO[Env1, OutErr2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr2, OutElem, OutDone] = {
    lazy val mapErrorZIO: ZChannel[Env1, OutErr, OutElem, OutDone, OutErr2, OutElem, OutDone] =
      ZChannel.readWith(
        chunk => ZChannel.write(chunk) *> mapErrorZIO,
        error => ZChannel.fromZIO(f(error).flip),
        done => ZChannel.succeedNow(done)
      )

    self >>> mapErrorZIO
  }

  /**
   * Returns a new channel, which is the same as this one, except the terminal
   * value of the returned channel is created by applying the specified
   * effectful function to the terminal value of this channel.
   */
  final def mapZIO[Env1 <: Env, OutErr1 >: OutErr, OutDone2](
    f: OutDone => ZIO[Env1, OutErr1, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem, OutDone2] =
    self.flatMap(z => ZChannel.fromZIO(f(z)))

  /**
   * Creates a channel that is like this channel but the given function gets
   * applied to each emitted output element
   */
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

  /**
   * Creates a channel that is like this channel but the given ZIO function gets
   * applied to each emitted output element
   */
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
   * applied to each emitted output element, running `f` on up to `n` elements
   * at the same time. Results are written in the order of the input elements.
   */
  final def mapOutZIOPar[Env1 <: Env, OutErr1 >: OutErr, OutElem2](n: Int)(
    f: OutElem => ZIO[Env1, OutErr1, OutElem2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone] =
    mapOutZIOPar[Env1, OutErr1, OutElem2](n, bufferSize = 16)(f)

  /**
   * Creates a channel that is like this channel but the given ZIO function gets
   * applied to each emitted output element, running `f` on up to `n` elements
   * at the same time. Results are written in the order of the input elements.
   * When `f` fails, the running effects are interrupted and the returned
   * channel fails with that error.
   *
   * @param n
   *   The maximum number of elements to map in parallel
   * @param bufferSize
   *   Number of elements that can be buffered downstream
   */
  final def mapOutZIOPar[Env1 <: Env, OutErr1 >: OutErr, OutElem2](n: Int, bufferSize: Int = 16)(
    f: OutElem => ZIO[Env1, OutErr1, OutElem2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone] =
    ZChannel.unwrapScopedWith { scope =>
      ZIO.fiberIdWith { fiberId =>
        val input       = SingleProducerAsyncInput.unsafe.make[InErr, InElem, InDone](fiberId)(Unsafe)
        val queueReader = ZChannel.fromInput(input)
        val n0          = n.toLong
        val bufferSize0 = bufferSize
        val outgoing    = Queue.unsafe.bounded[Fiber[Either[Unit, OutDone], OutElem2]](bufferSize0, fiberId)(Unsafe)
        val errorSignal = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)
        val permits     = Semaphore.unsafe.make(n0)(Unsafe)
        val failureRef  = Ref.unsafe.make[Cause[OutErr1]](Cause.empty)(Unsafe)

        val setFinalizer: UIO[Unit] =
          ZIO.uninterruptible {
            scope.addFinalizer(outgoing.shutdown)
          }

        for {
          _          <- setFinalizer
          pull       <- (queueReader >>> self).toPullInAlt(scope)
          childScope <- scope.fork
          processElems = pull.flatMap { outElem =>
                           val latch = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)

                           permits
                             .withPermit(
                               latch.succeedUnit *>
                                 f(outElem).catchAllCause { cause =>
                                   val continue =
                                     errorSignal.succeedUnit *>
                                       ZChannel.failLeftUnit

                                   if (cause.isInterruptedOnly) continue
                                   else failureRef.update(_ && cause) *> continue
                                 }
                             )
                             .interruptible
                             .forkIn(childScope)
                             .flatMap(fiber => latch.await *> outgoing.offer(fiber))
                         }.forever.interruptible
                           .onError(_.failureOrCause match {
                             case Left(x: Left[OutErr, ?]) =>
                               failureRef.update(_ && Cause.fail(x.value)) *>
                                 outgoing.offer(Fiber.done(ZChannel.failLeftUnit))
                             case Left(x: Right[?, OutDone]) =>
                               permits.withPermits(n0)(ZIO.unit).interruptible *>
                                 outgoing.offer(Fiber.fail(x.asInstanceOf[Either[Unit, OutDone]]))
                             case Right(cause) =>
                               val continue = outgoing.offer(Fiber.done(ZChannel.failLeftUnit))

                               if (cause.isInterruptedOnly) continue
                               else failureRef.update(_ && cause) *> continue
                           })
                           .ignore

          _ <- (processElems raceFirst ZChannel.awaitErrorSignal(childScope, fiberId)(errorSignal)).forkIn(scope)
        } yield {
          lazy val writer: ZChannel[Env1, Any, Any, Any, OutErr1, OutElem2, OutDone] =
            ZChannel.unwrap[Env1, Any, Any, Any, OutErr1, OutElem2, OutDone] {
              outgoing.take.flatMap(_.await).flatMap {
                case s: Exit.Success[OutElem2] => Exit.succeed(ZChannel.write(s.value) *> writer)
                case f: Exit.Failure[Either[Unit, OutDone]] =>
                  val failure0 = failureRef.unsafe.get(Unsafe)
                  val out = f.cause.failureOrCause match {
                    case Left(_: Left[Unit, OutDone]) => ZChannel.refailCause(failure0)
                    case Left(x: Right[Unit, OutDone]) =>
                      if (failure0 eq Cause.empty) ZChannel.succeedNow(x.value)
                      else ZChannel.refailCause(failure0)
                    case Right(c) if c.isInterruptedOnly => ZChannel.refailCause(failure0)
                    case Right(cause)                    => ZChannel.refailCause(cause)
                  }
                  outgoing.shutdown.as(out)
              }
            }

          writer.embedInput(input).ensuring(outgoing.shutdown)
        }
      }
    }

  /**
   * Creates a channel that is like this channel but the given ZIO function gets
   * applied to each emitted output element, running `f` on up to `n` elements
   * at the same time. Results are written in the order in which the effects
   * complete, so the order of the input elements is not preserved.
   *
   * @param n
   *   The maximum number of elements to map in parallel
   * @param bufferSize
   *   Number of elements that can be buffered downstream
   */
  final def mapOutZIOParUnordered[Env1 <: Env, OutErr1 >: OutErr, OutElem2](n: Int, bufferSize: Int = 16)(
    f: OutElem => ZIO[Env1, OutErr1, OutElem2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone] =
    ZChannel.unwrapScopedWith { scope =>
      for {
        input       <- SingleProducerAsyncInput.make[InErr, InElem, InDone]
        queueReader  = ZChannel.fromInput(input)
        outgoing    <- Queue.bounded[Exit[Either[Unit, OutDone], OutElem2]](bufferSize)
        _           <- scope.addFinalizer(outgoing.shutdown)
        errorSignal <- Promise.make[Nothing, Unit]
        permits     <- Semaphore.make(n.toLong)
        failure      = Ref.unsafe.make[Cause[OutErr1]](Cause.empty)(Unsafe)
        pull        <- (queueReader >>> self).toPullInAlt(scope)
        childScope  <- scope.fork
        fiberId     <- ZIO.fiberId
        _ <-
          pull.flatMap { outElem =>
            val latch = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)
            for {
              _ <- permits
                     .withPermit(
                       latch.succeedUnit *> f(outElem)
                         .foldCauseZIO(
                           cause => {
                             val continue =
                               errorSignal.succeedUnit *>
                                 outgoing.offer(ZChannel.failLeftUnit)

                             if (cause.isInterruptedOnly) continue
                             else failure.update(_ && cause) *> continue
                           },
                           elem => outgoing.offer(Exit.succeed(elem))
                         )
                     )
                     .interruptible
                     .forkIn(childScope)
              _ <- latch.await
            } yield ()
          }.forever.interruptible
            .onError(_.failureOrCause match {
              case Left(x: Left[OutErr, OutDone]) =>
                failure.update(_ && Cause.fail(x.value)) *>
                  outgoing.offer(ZChannel.failLeftUnit)
              case Left(x: Right[OutErr, OutDone]) =>
                permits.withPermits(n.toLong)(ZIO.unit).interruptible *>
                  outgoing.offer(Exit.fail(x.asInstanceOf[Either[Unit, OutDone]]))
              case Right(cause) =>
                val continue = outgoing.offer(ZChannel.failLeftUnit)

                if (cause.isInterruptedOnly) continue
                else failure.update(_ && cause) *> continue
            })
            .ignore
            .raceFirst(ZChannel.awaitErrorSignal(childScope, fiberId)(errorSignal))
            .forkIn(scope)
      } yield {
        lazy val writer: ZChannel[Env1, Any, Any, Any, OutErr1, OutElem2, OutDone] =
          ZChannel.unwrap[Env1, Any, Any, Any, OutErr1, OutElem2, OutDone] {
            outgoing.take.flatMap {
              case s: Exit.Success[OutElem2] => Exit.succeed(ZChannel.write(s.value) *> writer)
              case f: Exit.Failure[Either[Unit, OutDone]] =>
                val failure0 = failure.unsafe.get(Unsafe)
                val out = f.cause.failureOrCause match {
                  case Left(_: Left[Unit, OutDone]) => ZChannel.refailCause(failure0)
                  case Left(x: Right[Unit, OutDone]) =>
                    if (failure0 eq Cause.empty) ZChannel.succeedNow(x.value)
                    else ZChannel.refailCause(failure0)
                  case Right(c) if c.isInterruptedOnly => ZChannel.refailCause(failure0)
                  case Right(cause)                    => ZChannel.refailCause(cause)
                }
                outgoing.shutdown.as(out)
            }
          }

        writer.embedInput(input).ensuring(outgoing.shutdown)
      }
    }

  /**
   * Returns a new channel which creates a new channel for each emitted element
   * and runs up to `n` of them at the same time, merging their outputs. With
   * `BackPressure`, a new channel waits while `n` are running. With
   * `BufferSliding`, the new channel starts without waiting, and an earlier
   * channel that is still running may be interrupted. See
   * [[ZChannel.mergeAll]].
   *
   * @param n
   *   The maximum number of channels to merge
   * @param bufferSize
   *   Capacity of the queue that holds the elements written by the merged
   *   channels until downstream reads them
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

  /**
   * Returns a new channel, which is the merge of this channel and the specified
   * channel, where the behavior of the returned channel on left or right early
   * termination is decided by the specified `leftDone` and `rightDone` merge
   * decisions.
   *
   * Both channels run at the same time and their output elements are
   * interleaved. They read from the same upstream input, and each upstream
   * element goes to one of them.
   */
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
  ](that: ZChannel[Env1, InErr1, InElem1, InDone1, OutErr2, OutElem1, OutDone2])(
    leftDone: Exit[OutErr, OutDone] => ZChannel.MergeDecision[Env1, OutErr2, OutDone2, OutErr3, OutDone3],
    rightDone: Exit[OutErr2, OutDone2] => ZChannel.MergeDecision[Env1, OutErr, OutDone, OutErr3, OutDone3]
  )(implicit trace: Trace): ZChannel[Env1, InErr1, InElem1, InDone1, OutErr3, OutElem1, OutDone3] = {
    type MergeState = ZChannel.MergeState[Env1, OutErr, OutErr2, OutErr3, OutElem1, OutDone, OutDone2, OutDone3]
    import ZChannel.MergeState._

    def m(scope: Scope) =
      for {
        input      <- SingleProducerAsyncInput.make[InErr1, InElem1, InDone1]
        queueReader = ZChannel.fromInput(input)
        pullL      <- (queueReader >>> self).toPullIn(scope)
        pullR      <- (queueReader >>> that).toPullIn(scope)
      } yield {
        def handleSide[Err, Done, Err2, Done2](
          exit: Exit[Err, Either[Done, OutElem1]],
          fiber: Fiber.Runtime[Err2, Either[Done2, OutElem1]],
          pull: ZIO[Env1, Err, Either[Done, OutElem1]]
        )(
          done: Exit[Err, Done] => ZChannel.MergeDecision[Env1, Err2, Done2, OutErr3, OutDone3],
          both: (
            Fiber.Runtime[Err, Either[Done, OutElem1]],
            Fiber.Runtime[Err2, Either[Done2, OutElem1]]
          ) => MergeState,
          single: (Exit[Err2, Done2] => ZIO[Env1, OutErr3, OutDone3]) => MergeState
        ): ZIO[Env1, Nothing, ZChannel[Env1, Any, Any, Any, OutErr3, OutElem1, OutDone3]] = {
          def onDecision(
            decision: ZChannel.MergeDecision[Env1, Err2, Done2, OutErr3, OutDone3]
          ): ZIO[Any, Nothing, ZChannel[Env1, Any, Any, Any, OutErr3, OutElem1, OutDone3]] =
            decision match {
              case ZChannel.MergeDecision.Done(zio) =>
                ZIO.succeed(ZChannel.fromZIO(fiber.interrupt *> zio))
              case ZChannel.MergeDecision.Await(f) =>
                fiber.await.map {
                  case Exit.Success(Right(elem)) =>
                    ZChannel.write(elem) *> go(single(f))
                  case Exit.Success(Left(z)) =>
                    ZChannel.fromZIO(f(Exit.succeed(z)))
                  case Exit.Failure(cause) =>
                    ZChannel.fromZIO(f(Exit.failCause(cause)))
                }
            }

          exit match {
            case Exit.Success(Right(elem)) =>
              ZIO.succeed {
                ZChannel.write(elem) *> ZChannel.fromZIO(pull.forkIn(scope)).flatMap { leftFiber =>
                  go(both(leftFiber, fiber))
                }
              }

            case Exit.Success(Left(z)) =>
              onDecision(done(Exit.succeed(z)))

            case Exit.Failure(failure) =>
              onDecision(done(Exit.failCause(failure)))
          }
        }

        def go(state: MergeState): ZChannel[Env1, Any, Any, Any, OutErr3, OutElem1, OutDone3] =
          state match {
            case BothRunning(leftFiber, rightFiber, preferLeft) =>
              val lj: ZIO[Env1, OutErr, Either[OutDone, OutElem1]]   = leftFiber.join.interruptible
              val rj: ZIO[Env1, OutErr2, Either[OutDone2, OutElem1]] = rightFiber.join.interruptible

              ZChannel.unwrap {
                (leftFiber.unsafe.poll(Unsafe.unsafe), rightFiber.unsafe.poll(Unsafe.unsafe)) match {
                  case (Some(leftEx), opt) if preferLeft || opt.isEmpty =>
                    handleSide(leftEx, rightFiber, pullL)(
                      leftDone,
                      BothRunning(_, _, false),
                      LeftDone(_)
                    )
                  case (_, Some(rightEx)) =>
                    handleSide(rightEx, leftFiber, pullR)(
                      rightDone,
                      (l, r) => BothRunning(r, l, true),
                      RightDone(_)
                    )
                  case _ =>
                    lj.raceWith(rj)(
                      (leftEx, rf) =>
                        rf.interrupt *>
                          handleSide(leftEx, rightFiber, pullL)(
                            leftDone,
                            BothRunning(_, _, false),
                            LeftDone(_)
                          ),
                      (rightEx, lf) =>
                        lf.interrupt *>
                          handleSide(rightEx, leftFiber, pullR)(
                            rightDone,
                            (l, r) => BothRunning(r, l, true),
                            RightDone(_)
                          )
                    )
                }
              }

            case LeftDone(f) =>
              ZChannel.unwrap {
                pullR.exit.map {
                  case Exit.Success(Right(elem)) => ZChannel.write(elem) *> go(LeftDone(f))
                  case Exit.Success(Left(z)) =>
                    ZChannel.fromZIO(f(Exit.succeed(z)))
                  case Exit.Failure(cause) =>
                    ZChannel.fromZIO(f(Exit.failCause(cause)))
                }
              }

            case RightDone(f) =>
              ZChannel.unwrap {
                pullL.exit.map {
                  case Exit.Success(Right(elem)) => ZChannel.write(elem) *> go(RightDone(f))
                  case Exit.Success(Left(z)) =>
                    ZChannel.fromZIO(f(Exit.succeed(z)))
                  case Exit.Failure(cause) =>
                    ZChannel.fromZIO(f(Exit.failCause(cause)))
                }
              }
          }

        ZChannel.fromZIO {
          ZIO.transplant[Env1, Nothing, MergeState] { graft =>
            val fL = graft.applyOnExit(pullL).forkIn(scope)
            val fR = graft.applyOnExit(pullR).forkIn(scope)
            fL.zipWith(fR)(BothRunning(_, _, true))
          }
        }
          .flatMap(go)
          .embedInput(input)
      }

    ZChannel.unwrapScopedWith(m)
  }

  /** Returns a channel that never completes */
  @deprecated("use ZChannel.never", "3.0.0")
  final def never(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Nothing] =
    ZChannel.fromZIO(ZIO.never)

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
   * Returns a new channel that pipes the output of this channel into the
   * specified channel. The returned channel has the input type of this channel,
   * and the output type of the specified channel, terminating with the value of
   * the specified channel.
   *
   * Same as the symbolic operator [[>>>]]
   */
  final def pipeTo[Env1 <: Env, OutErr2, OutElem2, OutDone2](
    that: => ZChannel[Env1, OutErr, OutElem, OutDone, OutErr2, OutElem2, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2] =
    if (self eq ZChannel.identityAny)
      ZChannel.suspend(that.asInstanceOf[ZChannel[Env1, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2]])
    else ZChannel.PipeTo(() => self, () => that)

  /**
   * Returns a new channel that pipes the output of this channel into the
   * specified channel and preserves this channel's failures without providing
   * them to the other channel as typed errors.
   *
   * The other channel sees a failure of this channel as a defect. When the
   * other channel propagates that cause, the returned channel fails with the
   * original cause of this channel.
   */
  final def pipeToOrFail[Env1 <: Env, OutErr1 >: OutErr, OutElem2, OutDone2](
    that: => ZChannel[Env1, Nothing, OutElem, OutDone, OutErr1, OutElem2, OutDone2]
  )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone2] =
    ZChannel.suspend {
      val that0 = that
      if (self eq ZChannel.identityAny) {
        that0.asInstanceOf[ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone2]]
      } else if (that0 eq ZChannel.identityAny) {
        self.asInstanceOf[ZChannel[Env1, InErr, InElem, InDone, OutErr1, OutElem2, OutDone2]]
      } else {
        self >>> that0.failOnInputError[OutErr1]
      }
    }

  /**
   * Returns a new channel that accepts input failures and fails with them,
   * without providing them to this channel for observation. This is useful for
   * running a channel that cannot handle input failures, such as the channel of
   * a [[ZSink]], as part of a channel that receives them.
   */
  private[stream] final def failOnInputError[OutErr1 >: OutErr](implicit
    trace: Trace
  ): ZChannel[Env, OutErr1, InElem, InDone, OutErr1, OutElem, OutDone] =
    ZChannel.suspend {
      class ChannelFailure(val err: Cause[OutErr1]) extends Throwable(null, null, true, false) {
        override def getMessage: String = err.unified.headOption.fold("<unknown>")(_.message)

        override def getStackTrace(): Array[StackTraceElement] =
          err.unified.headOption.fold[Chunk[StackTraceElement]](Chunk.empty)(_.trace).toArray

        override def getCause(): Throwable =
          err.find { case Cause.Die(throwable, _) => throwable }
            .orElse(err.find { case Cause.Fail(value: Throwable, _) => value })
            .orNull

        def fillSuppressed()(implicit unsafe: Unsafe): Unit =
          if (getSuppressed().length == 0) {
            err.unified.iterator.drop(1).foreach(unified => addSuppressed(unified.toThrowable))
          }

        override def toString =
          err.prettyPrint
      }
      var channelFailure: ChannelFailure = null

      lazy val reader: ZChannel[Env, OutErr1, InElem, InDone, Nothing, InElem, InDone] =
        ZChannel.readWithCause(
          elem => ZChannel.write(elem) *> reader,
          err => {
            channelFailure = new ChannelFailure(err)
            ZChannel.refailCause(Cause.die(channelFailure))
          },
          done => ZChannel.succeedNow(done)
        )

      lazy val writer: ZChannel[Env, OutErr1, OutElem, OutDone, OutErr1, OutElem, OutDone] =
        ZChannel.readWithCause(
          elem => ZChannel.write(elem) *> writer,
          cause =>
            ZChannel.refailCause(
              if (channelFailure eq null)
                cause
              else
                cause.fold[Cause[OutErr1]](
                  Cause.empty,
                  (e, st) => Cause.fail(e, st),
                  (t, st) =>
                    t match {
                      case t: ChannelFailure if t == channelFailure =>
                        t.err
                      case t => Cause.die(t, st)
                    },
                  (fid, st) => Cause.interrupt(fid, st)
                )(_ ++ _, _ && _, (cause, _) => cause)
            ),
          done => ZChannel.succeedNow(done)
        )

      reader >>> self >>> writer
    }

  /**
   * Provides the channel with its required environment, which eliminates its
   * dependency on `Env`.
   */
  final def provideEnvironment(env: => ZEnvironment[Env])(implicit
    trace: Trace
  ): ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.Provide(() => env, self)

  /**
   * Provides a layer to the channel. The layer is built when the channel starts
   * and released when the channel finishes.
   */
  final def provideLayer[OutErr1 >: OutErr, Env0](
    layer: => ZLayer[Env0, OutErr1, Env]
  )(implicit trace: Trace): ZChannel[Env0, InErr, InElem, InDone, OutErr1, OutElem, OutDone] =
    ZChannel.unwrapScopedWith(scope => layer.build(scope).map(env => self.provideEnvironment(env)))

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

  /** Creates a channel which repeatedly runs this channel */
  final def repeated(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Nothing] = {
    lazy val loop: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Nothing] = self *> loop
    loop
  }

  /**
   * Run the channel until it finishes with a done value or fails with an error.
   * The channel must not read any input or write any output.
   */
  final def run(implicit ev1: Any <:< InElem, ev2: OutElem <:< Nothing, trace: Trace): ZIO[Env, OutErr, OutDone] =
    ZIO.scopedWith(scope => self.runIn(scope))

  /**
   * Run the channel until it finishes with a done value or fails with an error
   * and collects its emitted output elements.
   *
   * The channel must not read any input.
   */
  final def runCollect(implicit ev1: Any <:< InElem, trace: Trace): ZIO[Env, OutErr, (Chunk[OutElem], OutDone)] =
    collectElements.run

  /**
   * Run the channel until it finishes with a done value or fails with an error
   * and ignores its emitted output elements.
   *
   * The channel must not read any input.
   */
  final def runDrain(implicit ev1: Any <:< InElem, trace: Trace): ZIO[Env, OutErr, OutDone] =
    self.drain.run

  /**
   * Runs the channel like `run`, but adds closing the channel to `scope` as a
   * finalizer: the finalizers that are still open when the channel finishes run
   * when `scope` closes. The channel must not read any input or write any
   * output.
   */
  final def runIn(
    scope: => Scope
  )(implicit ev1: Any <:< InElem, ev2: OutElem <:< Nothing, trace: Trace): ZIO[Env, OutErr, OutDone] = {
    def run(channelPromise: Promise[OutErr, OutDone], scopePromise: Promise[Nothing, Unit], scope: Scope) = ZIO
      .acquireReleaseExitWith(
        ZIO.succeed(
          new ChannelExecutor[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
            () => self,
            null,
            executeCloseLastSubstream = ZIO.identityFn[URIO[Env, Any]]
          )
        )
      ) { (exec, exit: Exit[OutErr, OutDone]) =>
        val finalize = exec.close(exit)
        if (finalize ne null) finalize.tapErrorCause(cause => scope.addFinalizer(Exit.failCause(cause)))
        else ZIO.unit
      } { exec =>
        ZIO.suspendSucceed {
          def interpret(channelState: ChannelExecutor.ChannelState[Env, OutErr]): ZIO[Env, OutErr, OutDone] =
            channelState match {
              case ChannelState.Effect(zio) =>
                zio *> interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])
              case ChannelState.Emit =>
                // Can't really happen because Out <:< Nothing. So just skip ahead.
                interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])
              case ChannelState.Done =>
                exec.getDone
              case r @ ChannelState.Read(upstream, onEffect, onEmit, onDone) =>
                ChannelExecutor.readUpstream[Env, OutErr, OutErr, OutDone](
                  r.asInstanceOf[ChannelState.Read[Env, OutErr]],
                  () => interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]]),
                  Exit.failCause
                )
            }

          interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])
            .intoPromise(channelPromise) *> channelPromise.await <* scopePromise.await
        }
      }

    ZIO.uninterruptibleMask { restore =>
      for {
        parent         <- ZIO.succeed(scope)
        child          <- parent.fork
        channelPromise <- Promise.make[OutErr, OutDone]
        scopePromise   <- Promise.make[Nothing, Unit]
        fiber          <- restore(run(channelPromise, scopePromise, child)).forkDaemon
        _ <- parent.addFinalizer {
               channelPromise.isDone.flatMap { isDone =>
                 if (isDone) scopePromise.succeedUnit *> fiber.await *> fiber.inheritAll
                 else scopePromise.succeedUnit *> fiber.interrupt *> fiber.inheritAll
               }
             }
        done <- restore(channelPromise.await)
      } yield done
    }
  }

  /**
   * Run the channel until it finishes with a done value or fails with an error.
   * The channel must not read any input or write any output.
   *
   * Closing the channel is added to the current scope as a finalizer: the
   * finalizers that are still open when the channel finishes run when that
   * scope closes. Finalizers that already ran before the channel finished, such
   * as those of the left side of `*>`, are not delayed. The finalizers of the
   * upstream side of `>>>` can still be open at that point, even when upstream
   * finished first.
   */
  final def runScoped(implicit
    ev1: Any <:< InElem,
    ev2: OutElem <:< Nothing,
    trace: Trace
  ): ZIO[Env with Scope, OutErr, OutDone] =
    ZIO.scopeWith(scope => self.runIn(scope))

  /**
   * Creates a new channel which is like this channel but returns with the unit
   * value when succeeds
   */
  final def unit(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, Unit] =
    self.flatMap(ZChannel.unitChannelFn)

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

  /**
   * Returns in a scope a ZIO effect that can be used to repeatedly pull
   * elements from the channel. The pull effect fails with the channel's failure
   * in case the channel fails, or returns either the channel's done value or an
   * emitted element.
   */
  final def toPull(implicit trace: Trace): ZIO[Env with Scope, Nothing, ZIO[Env, OutErr, Either[OutDone, OutElem]]] =
    ZIO.scopeWith(self.toPullIn(_))

  /**
   * Returns an effect that can be used to repeatedly pull elements from the
   * channel, like `toPull`, adding the channel's finalizers to `scope`. The
   * pull effect fails with the channel's failure, or succeeds with `Right` of
   * an emitted element or `Left` of the done value.
   */
  final def toPullIn(
    scope: => Scope
  )(implicit trace: Trace): ZIO[Env, Nothing, ZIO[Env, OutErr, Either[OutDone, OutElem]]] =
    ZIO.suspendSucceed {
      val scope0 = scope

      val exec = new ChannelExecutor[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
        initialChannel = () => self,
        providedEnv = null,
        executeCloseLastSubstream = ZIO.identityFn
      )

      val setFinalizer: ZIO[Env, Nothing, Unit] =
        ZIO
          .environmentWithZIO[Env] { environment =>
            scope0.addFinalizerExit { exit =>
              val finalizer = exec.close(exit)
              if (finalizer ne null) finalizer.provideEnvironment(environment)
              else Exit.unit
            }
          }
          .uninterruptible

      def interpret(
        channelState: ChannelExecutor.ChannelState[Env, OutErr]
      ): ZIO[Env, OutErr, Either[OutDone, OutElem]] =
        channelState match {
          case ChannelState.Done =>
            exec.getDone match {
              case Exit.Success(done)  => ZIO.succeed(Left(done))
              case Exit.Failure(cause) => Exit.failCause(cause)
            }
          case ChannelState.Emit =>
            ZIO.succeed(Right(exec.getEmit))
          case ChannelState.Effect(zio) =>
            zio *> interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])
          case r: ChannelState.Read[Env, OutErr] =>
            val continue = () => interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])

            ChannelExecutor.readUpstream[Env, OutErr, OutErr, Either[OutDone, OutElem]](
              r = r,
              onSuccess = continue,
              onFailure = Exit.failCause
            )
        }

      val pull: ZIO[Env, OutErr, Either[OutDone, OutElem]] =
        ZIO.suspendSucceed(interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]]))

      setFinalizer.as(pull)
    }

  /**
   * Returns an effect that can be used to repeatedly pull elements from the
   * channel, adding the channel's finalizers to `scope`. The pull effect
   * succeeds with an emitted element. Once the channel has finished, it fails
   * with `Left` of the channel's typed error or `Right` of its done value.
   */
  final def toPullInAlt(
    scope: => Scope
  )(implicit trace: Trace): ZIO[Env, Nothing, ZIO[Env, Either[OutErr, OutDone], OutElem]] =
    ZIO.suspendSucceed {
      val scope0 = scope

      val exec = new ChannelExecutor[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
        initialChannel = () => self,
        providedEnv = null,
        executeCloseLastSubstream = ZIO.identityFn[URIO[Env, Any]]
      )

      val setFinalizer: ZIO[Env, Nothing, Unit] =
        ZIO
          .environmentWithZIO[Env] { environment =>
            scope0.addFinalizerExit { exit =>
              val finalizer = exec.close(exit)
              if (finalizer ne null) finalizer.provideEnvironment(environment)
              else Exit.unit
            }
          }
          .uninterruptible

      def interpret(
        channelState: ChannelExecutor.ChannelState[Env, OutErr]
      ): ZIO[Env, Either[OutErr, OutDone], OutElem] =
        channelState match {
          case ChannelState.Done =>
            exec.getDone match {
              case s: Exit.Success[OutDone] => Exit.fail(Right(s.value))
              case f: Exit.Failure[OutErr]  => Exit.failCause(f.cause.map(Left(_)))
            }
          case ChannelState.Emit =>
            Exit.succeed(exec.getEmit)
          case ChannelState.Effect(zio) =>
            zio.foldZIO(
              failure = e => Exit.fail(Left(e)),
              success = _ => interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])
            )
          case r: ChannelState.Read[Env, OutErr] =>
            val continue = () => interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]])

            ChannelExecutor.readUpstream[Env, OutErr, Either[OutErr, OutDone], OutElem](
              r = r,
              onSuccess = continue,
              onFailure = Exit.failCause(_).mapError(Left(_))
            )
        }

      val pull: ZIO[Env, Either[OutErr, OutDone], OutElem] =
        ZIO.suspendSucceed(interpret(exec.run().asInstanceOf[ChannelState[Env, OutErr]]))

      setFinalizer.as(pull)
    }

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

  /**
   * Creates a new channel which first runs this channel and if it succeeds runs
   * the other channel and finally finishes with the zipped done value of both
   * channels
   */
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
    self.flatMap(l => that.map(r => zippable.zip(l, r)))

  /**
   * Creates a new channel which first runs this channel and if it succeeds runs
   * the other channel and finally finishes with the done value of the first
   * channel
   */
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
    (self zip that).map(_._1)

  /**
   * Creates a new channel which runs in parallel this and the other channel and
   * when both succeed finishes with both done values combined by `Zippable`
   * (`Unit` values are dropped and nested tuples are flattened).
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
      exit1 => ZChannel.MergeDecision.Await[Env1, OutErr1, OutDone2, OutErr1, zippable.Out](exit2 => exit1.zip(exit2)),
      exit2 => ZChannel.MergeDecision.Await[Env1, OutErr1, OutDone, OutErr1, zippable.Out](exit1 => exit1.zip(exit2))
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

  /**
   * Creates a new channel which first runs this channel and if it succeeds runs
   * the other channel and finally finishes with the done value of the second
   * channel
   */
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
    (self zip that).map(_._2)
}

/**
 * Constructors for [[ZChannel]] values, and the types that configure how
 * channels are merged ([[ZChannel.MergeDecision]] and
 * [[ZChannel.MergeStrategy]]).
 */
object ZChannel {
  private val failLeftUnit = Exit.fail(Left(()))
  private val failUnit     = Exit.failCause(Cause.unit)

  private[zio] final case class PipeTo[
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
    left: () => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    right: () => ZChannel[Env, OutErr, OutElem, OutDone, OutErr2, OutElem2, OutDone2]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2]

  private[zio] final case class Read[Env, InErr, InElem, InDone, OutErr, OutErr2, OutElem, OutElem2, OutDone, OutDone2](
    more: InElem => ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2],
    done: Fold.K[Env, InErr, InElem, InDone, OutErr, OutErr2, OutElem2, OutDone, OutDone2]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem2, OutDone2]
  private[zio] final case class SucceedNow[OutDone](terminal: OutDone)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, OutDone]
  private[zio] final case class Fail[OutErr](error: () => Cause[OutErr])
      extends ZChannel[Any, Any, Any, Any, OutErr, Nothing, Nothing]
  private[zio] final case class FromZIO[Env, OutErr, OutDone](zio: () => ZIO[Env, OutErr, OutDone])
      extends ZChannel[Env, Any, Any, Any, OutErr, Nothing, OutDone]
  private[zio] final case class Emit[OutElem](out: OutElem) extends ZChannel[Any, Any, Any, Any, Nothing, OutElem, Unit]
  private[zio] final case class Succeed[OutDone](effect: () => OutDone)
      extends ZChannel[Any, Any, Any, Any, Nothing, Nothing, OutDone]
  private[zio] final case class Suspend[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    effect: () => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  private[zio] final case class Ensuring[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    finalizer: Exit[OutErr, OutDone] => ZIO[Env, Nothing, Any]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  private[zio] final case class ConcatAll[
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
    combineInners: (OutDone, OutDone) => OutDone,
    combineAll: (OutDone, OutDone2) => OutDone3,
    value: () => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone2],
    k: OutElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone3]
  private[zio] final case class Fold[Env, InErr, InElem, InDone, OutErr, OutErr2, OutElem, OutDone, OutDone2](
    value: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    k: Fold.K[Env, InErr, InElem, InDone, OutErr, OutErr2, OutElem, OutDone, OutDone2]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2]

  /**
   * Continuations used internally by the channel interpreter.
   */
  object Fold {

    /**
     * Continuation used internally by the channel interpreter.
     */
    sealed abstract class Continuation[-Env, -InErr, -InElem, -InDone, OutErr, +OutErr2, +OutElem, OutDone, +OutDone2]
    private[zio] final case class K[-Env, -InErr, -InElem, -InDone, OutErr, +OutErr2, +OutElem, OutDone, +OutDone2](
      onSuccess: OutDone => ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2],
      onHalt: Cause[OutErr] => ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2]
    ) extends Continuation[Env, InErr, InElem, InDone, OutErr, OutErr2, OutElem, OutDone, OutDone2] {
      def onExit(exit: Exit[OutErr, OutDone]): ZChannel[Env, InErr, InElem, InDone, OutErr2, OutElem, OutDone2] =
        exit match {
          case Exit.Success(value) => onSuccess(value)
          case Exit.Failure(cause) => onHalt(cause)
        }
    }
    private[zio] final case class Finalizer[Env, OutErr, OutDone](finalizer: Exit[OutErr, OutDone] => URIO[Env, Any])
        extends Continuation[Env, Any, Any, Any, OutErr, Nothing, Nothing, OutDone, Nothing]

    private[this] val SuccessIdentity: Any => ZChannel[Any, Any, Any, Any, Nothing, Nothing, Any] =
      ZChannel.succeedNow(_)(Trace.empty)

    private[stream] def successIdentity[Z](implicit
      trace: Trace
    ): Z => ZChannel[Any, Any, Any, Any, Nothing, Nothing, Z] =
      SuccessIdentity.asInstanceOf[Z => ZChannel[Any, Any, Any, Any, Nothing, Nothing, Z]]

    private[this] val FailCauseIdentity: Cause[Any] => ZChannel[Any, Any, Any, Any, Any, Nothing, Nothing] =
      ZChannel.refailCause

    private[stream] def failCauseIdentity[E](implicit
      trace: Trace
    ): Cause[E] => ZChannel[Any, Any, Any, Any, E, Nothing, Nothing] =
      FailCauseIdentity.asInstanceOf[Cause[E] => ZChannel[Any, Any, Any, Any, E, Nothing, Nothing]]

    private[this] val FoldKIdentity =
      new Fold.K[Any, Any, Any, Any, Any, Any, Any, Any, Any](
        v => v.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]],
        FailCauseIdentity
      )

    private[stream] def foldKIdentity[Env1, InErr1, InElem1, InDone1, OutErr1, OutElem1, OutDone, OutDone2]
      : Fold.K[Env1, InErr1, InElem1, InDone1, OutErr1, OutErr1, OutElem1, OutDone, OutDone2] =
      FoldKIdentity.asInstanceOf[Fold.K[Env1, InErr1, InElem1, InDone1, OutErr1, OutErr1, OutElem1, OutDone, OutDone2]]
  }

  private[zio] final case class Bridge[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone](
    input: AsyncInputProducer[InErr, InElem, InDone],
    channel: ZChannel[Env, Any, Any, Any, OutErr, OutElem, OutDone]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[zio] final case class DeferedUpstream[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone](
    mkChannel: ZChannel[Any, Any, Any, Any, InErr, InElem, InDone] => ZChannel[
      Env,
      Any,
      Any,
      Any,
      OutErr,
      OutElem,
      OutDone
    ]
  ) extends ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  private[zio] final case class BracketOut[R, E, Z](
    acquire: () => ZIO[R, E, Z],
    finalizer: (Z, Exit[Any, Any]) => URIO[R, Any]
  ) extends ZChannel[R, Any, Any, Any, E, Z, Unit]

  private[zio] final case class Provide[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    environment: () => ZEnvironment[Env],
    inner: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends ZChannel[Any, InErr, InElem, InDone, OutErr, OutElem, OutDone]

  /**
   * Creates a channel that runs `acquire` uninterruptibly, writes the acquired
   * value as its only output element and runs `release` on it when the channel
   * is closed. When the element is consumed by `concatMap` or by a downstream
   * channel (`>>>`), the channel is closed after that consumer has finished.
   *
   * If `acquire` fails, the channel fails with that error and `release` does
   * not run.
   */
  def acquireReleaseOutWith[Env, OutErr, Acquired](acquire: => ZIO[Env, OutErr, Acquired])(
    release: Acquired => URIO[Env, Any]
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, OutErr, Acquired, Unit] =
    acquireReleaseOutExitWith(acquire)((z, _) => release(z))

  /**
   * Creates a channel that runs `acquire` uninterruptibly, writes the acquired
   * value as its only output element and runs `release` on it when the channel
   * is closed, like `acquireReleaseOutWith`. `release` also receives the `Exit`
   * the channel is closed with: for example, when a `concatMap` consumer of the
   * element fails, `release` receives that failure.
   */
  def acquireReleaseOutExitWith[Env, OutErr, Acquired](acquire: => ZIO[Env, OutErr, Acquired])(
    release: (Acquired, Exit[Any, Any]) => URIO[Env, Any]
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, OutErr, Acquired, Unit] =
    BracketOut(() => acquire, release)

  /**
   * Creates a channel that runs `acquire` uninterruptibly, continues as the
   * channel that `use` creates from the acquired value, and runs `release` when
   * that channel finishes, fails or is interrupted. If `acquire` fails, the
   * channel fails with that error and `release` does not run.
   */
  def acquireReleaseWith[Env, InErr, InElem, InDone, OutErr, Acquired, OutElem2, OutDone](
    acquire: => ZIO[Env, OutErr, Acquired]
  )(release: Acquired => URIO[Env, Any])(
    use: Acquired => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone] =
    acquireReleaseExitWith[Env, InErr, InElem, InDone, OutErr, Acquired, OutElem2, OutDone](acquire)((a, _) =>
      release(a)
    )(use)

  /**
   * Creates a channel that runs `acquire` uninterruptibly, continues as the
   * channel that `use` creates from the acquired value, and runs `release` with
   * the `Exit` of that channel when it finishes, fails or is interrupted. If
   * `acquire` fails, the channel fails with that error and `release` does not
   * run.
   */
  def acquireReleaseExitWith[Env, InErr, InElem, InDone, OutErr, Acquired, OutElem2, OutDone](
    acquire: => ZIO[Env, OutErr, Acquired]
  )(release: (Acquired, Exit[OutErr, OutDone]) => URIO[Env, Any])(
    use: Acquired => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem2, OutDone] =
    fromZIO(Ref.make[Exit[OutErr, OutDone] => URIO[Env, Any]](ZIO.unitZIOFn)).flatMap { ref =>
      fromZIO(acquire.tap(a => ref.set(release(a, _))).uninterruptible)
        .flatMap(use)
        .ensuringWith(ex => ref.get.flatMap(_.apply(ex)))
    }

  private def awaitErrorSignal(
    scope: Scope.Closeable,
    fiberId: FiberId
  )(
    signal: Promise[Nothing, Unit]
  )(implicit trace: Trace): UIO[Unit] =
    signal.await.interruptible.onExit {
      case _: Exit.Success[?] => scope.close(Exit.interrupt(fiberId))
      case _                  => Exit.unit
    }

  /**
   * Creates a channel backed by a buffer. When the buffer is empty, the channel
   * passes its input through as output. When the buffer is non-empty, the
   * channel first writes the value inside the buffer and resets the buffer to
   * `empty`.
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

  /**
   * Creates a channel backed by a buffer of chunks, like `buffer` with the
   * empty chunk as the empty value: when the buffer holds a non-empty chunk,
   * the channel first writes that chunk and empties the buffer, then passes its
   * input through.
   */
  def bufferChunk[InErr, InElem, InDone](
    ref: => Ref[Chunk[InElem]]
  )(implicit trace: Trace): ZChannel[Any, InErr, Chunk[InElem], InDone, InErr, Chunk[InElem], InDone] =
    buffer[InErr, Chunk[InElem], InDone](Chunk.empty, _.isEmpty, ref)

  /**
   * Creates a channel that runs each channel written by `channels` in turn, to
   * completion, and writes all their output elements. The terminal values are
   * discarded.
   */
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
    concatAllWith(channels)(unitFn2, unitFn2)

  /**
   * Creates a channel that runs each channel written by `channels` in turn, to
   * completion, and writes all their output elements.
   *
   * `f` combines the terminal values of the inner channels, in order, and `g`
   * combines that result with the terminal value of `channels` to give the
   * terminal value of the returned channel.
   */
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
    ConcatAll(
      f,
      g,
      () => channels,
      (channel: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]) => channel
    )

  /**
   * Creates a channel that succeeds immediately with the given value. Unlike
   * `succeed`, the value is evaluated when this method is called.
   */
  def succeedNow[Z](result: Z)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Z] =
    SucceedNow(result)

  /**
   * Creates a channel that succeeds with the result of applying `f` to the
   * environment of the channel.
   */
  def succeedWith[R, Z](f: ZEnvironment[R] => Z)(implicit
    trace: Trace
  ): ZChannel[R, Any, Any, Any, Nothing, Nothing, Z] =
    ZChannel.fromZIO(ZIO.environmentWith[R](f))

  /**
   * Creates a channel that writes the given element as its only output element
   * and succeeds with `Unit`.
   */
  def write[Out](out: Out)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Out, Unit] =
    Emit(out)

  /**
   * Creates a channel that writes the given elements one by one, in order, and
   * succeeds with `Unit`.
   */
  def writeAll[Out](outs: Out*)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Out, Unit] =
    writeChunk(Chunk.fromIterable(outs))

  /**
   * Creates a channel that writes the elements of the given chunk one by one,
   * in order, as separate output elements, and succeeds with `Unit`.
   */
  def writeChunk[Out](
    outs: Chunk[Out]
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Out, Unit] = {
    def writer(idx: Int, len: Int): ZChannel[Any, Any, Any, Any, Nothing, Out, Unit] =
      if (idx == len) ZChannel.unit
      else ZChannel.write(outs(idx)) *> writer(idx + 1, len)

    writer(0, outs.size)
  }

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

  /**
   * Creates a channel that fails with the given error. The error is evaluated
   * lazily, when the channel runs.
   */
  def fail[E](e: => E)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, E, Nothing, Nothing] =
    failCause(Cause.fail(e))

  /**
   * Creates a channel that fails with the value of a `Left` or succeeds with
   * the value of a `Right`. The `Either` is evaluated when the channel runs.
   */
  def fromEither[E, A](either: => Either[E, A])(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, E, Nothing, A] =
    ZChannel.suspend(either.fold(ZChannel.fail(_), ZChannel.succeed(_)))

  /**
   * Creates a channel that subscribes to the hub when it starts and writes the
   * value of each `Right` it receives. The first `Left` ends the channel: it
   * fails or succeeds as that `Exit` does. The subscription is closed when the
   * channel finishes.
   */
  def fromHub[Err, Done, Elem](
    hub: => Hub[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
    ZChannel.unwrapScoped(hub.subscribe.map(fromQueue(_)))

  /**
   * Subscribes to the hub in the current scope and returns a channel that
   * writes the value of each `Right` it receives. The first `Left` ends the
   * channel: it fails or succeeds as that `Exit` does. The subscription stays
   * open until the scope closes.
   */
  def fromHubScoped[Err, Done, Elem](
    hub: => Hub[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZIO[Scope, Nothing, ZChannel[Any, Any, Any, Any, Err, Elem, Done]] =
    hub.subscribe.map(fromQueue(_))

  /**
   * Creates a channel that succeeds with the value of a `Some`, or fails with
   * `None` when the option is empty. The option is evaluated when the channel
   * runs.
   */
  def fromOption[A](option: => Option[A])(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, None.type, Nothing, A] =
    ZChannel.suspend(
      option.fold[ZChannel[Any, Any, Any, Any, None.type, Nothing, A]](ZChannel.fail(None))(ZChannel.succeed(_))
    )

  /**
   * Creates a channel that takes values from the queue and writes the value of
   * each `Right`. The first `Left` ends the channel: it fails or succeeds as
   * that `Exit` does, and later queue values are not taken.
   */
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

  /**
   * Creates a channel that runs the given effect when the channel runs and
   * succeeds with its result, or fails with its error. The channel writes no
   * output.
   */
  def fromZIO[R, E, A](zio: => ZIO[R, E, A])(implicit trace: Trace): ZChannel[R, Any, Any, Any, E, Nothing, A] =
    FromZIO(() => zio)

  /**
   * Creates a channel that fails with the given cause. The cause is evaluated
   * lazily, when the channel runs.
   */
  def failCause[E](cause: => Cause[E])(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, E, Nothing, Nothing] =
    Fail(() => cause)

  /**
   * Creates a channel that writes every element it reads and passes upstream
   * failures and the upstream done value through unchanged.
   */
  def identity[Err, Elem, Done](implicit trace: Trace): ZChannel[Any, Err, Elem, Done, Err, Elem, Done] =
    identityAny.asInstanceOf[ZChannel[Any, Err, Elem, Done, Err, Elem, Done]]

  /**
   * Creates a channel that fails with an interruption cause carrying the given
   * fiber id.
   */
  def interruptAs(fiberId: => FiberId)(implicit
    trace: Trace
  ): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Nothing] =
    failCause(Cause.interrupt(fiberId))

  /**
   * Creates a channel that runs the scoped effect in a new scope, writes its
   * result as the only output element and closes the scope when the channel is
   * closed. When the element is consumed by `concatMap`, the scope closes after
   * that consumer has finished.
   */
  def scoped[R]: ScopedPartiallyApplied[R] =
    new ScopedPartiallyApplied[R]

  /**
   * Creates a channel that runs `f` with a new scope, writes its result as the
   * only output element and closes the scope when the channel is closed. When
   * the element is consumed by `concatMap`, the scope closes after that
   * consumer has finished.
   */
  def scopedWith[R, E, A](f: Scope => ZIO[R, E, A])(implicit trace: Trace): ZChannel[R, Any, Any, Any, E, A, Any] =
    ZChannel.unwrapScoped(ZIO.scopeWith(scope => Exit.succeed(ZChannel.fromZIO(f(scope)).flatMap(ZChannel.write))))

  /**
   * Creates a channel that runs the channels written by `channels`, up to `n`
   * of them at the same time, and writes their output elements in the order
   * they arrive. When one of them fails, the others are interrupted and the
   * returned channel fails with that error.
   *
   * When a new channel is written, `MergeStrategy.BackPressure` waits while `n`
   * channels are running. `MergeStrategy.BufferSliding` starts the new channel
   * without waiting, and an earlier channel that is still running may be
   * interrupted. `bufferSize` is the capacity of the queue that holds output
   * elements until downstream reads them.
   */
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
    mergeAllWith(channels, n, bufferSize, mergeStrategy)(unitFn2)

  /**
   * Creates a channel that runs all the channels written by `channels` at the
   * same time and writes their output elements in the order they arrive, like
   * `mergeAll` with `n` set to `Int.MaxValue`.
   */
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

  /**
   * Creates a channel that runs all the channels written by `channels` at the
   * same time, like `mergeAllWith` with `n` set to `Int.MaxValue`. `f` combines
   * the terminal values of the inner channels and of `channels`.
   */
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

  /**
   * Creates a channel that runs the channels written by `channels`, up to `n`
   * of them at the same time, and writes their output elements in the order
   * they arrive, like `mergeAll`.
   *
   * `f` combines the terminal values of the inner channels in the order they
   * finish, and then combines that result with the terminal value of
   * `channels`. When `channels` writes no channel, the returned channel
   * succeeds with the terminal value of `channels`.
   */
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
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] = {
    sealed trait Result
    object Result {
      final case class Value(value: OutElem)        extends Result
      final case class Error(error: OutErr)         extends Result
      final case class Done(done: OutDone)          extends Result
      final case class Fatal(cause: Cause[Nothing]) extends Result
    }

    unwrapScopedWith { scope =>
      ZIO.fiberIdWith { fiberId =>
        val input          = SingleProducerAsyncInput.unsafe.make[InErr, InElem, InDone](fiberId)(Unsafe)
        val incoming       = ZChannel.fromInput(input)
        val n0             = n.toLong
        val bufferSize0    = bufferSize
        val mergeStrategy0 = mergeStrategy
        val outgoing       = Queue.unsafe.bounded[Result](bufferSize0, fiberId)(Unsafe)
        val cancelers      = Queue.unsafe.unbounded[Promise[Nothing, Unit]](fiberId)(Unsafe)
        val lastDone       = new AtomicReference(null.asInstanceOf[OutDone])
        val errorSignal    = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)
        val permits        = Semaphore.unsafe.make(n0)(Unsafe)

        type Pull[A] = ZIO[Env, Either[OutErr, OutDone], A]

        def evaluatePull(pull: Pull[OutElem]): URIO[Env, Unit] =
          pull
            .flatMap(outElem => outgoing.offer(Result.Value(outElem)))
            .forever
            .catchAllCause(cause =>
              cause.failureOrCause match {
                case Left(x: Right[OutErr, OutDone]) =>
                  ZIO.succeed {
                    lastDone.updateAndGet {
                      case null     => x.value
                      case lastDone => f(lastDone, x.value)
                    }
                  }
                case Left(l: Left[OutErr, OutDone]) =>
                  outgoing.offer(Result.Error(l.value)) *> errorSignal.succeedUnit.unit
                case Right(cause) =>
                  outgoing.offer(Result.Fatal(cause)) *> errorSignal.succeedUnit.unit
              }
            )

        type Channel = ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]

        def backPressurePull(childScope: Scope, pull: Pull[Channel]): Pull[Unit] =
          pull.flatMap { channel =>
            val latch = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)
            val raceIOs =
              ZIO.scopedWith { scope =>
                (incoming >>> channel)
                  .toPullInAlt(scope)
                  .flatMap(evaluatePull)
              }

            permits
              .withPermit(latch.succeedUnit *> raceIOs)
              .interruptible
              .forkIn(childScope) *> latch.await
          }

        def bufferSlidingPull(childScope: Scope, pull: Pull[Channel]): Pull[Unit] =
          pull.flatMap { channel =>
            val canceler = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)
            val latch    = Promise.unsafe.make[Nothing, Unit](fiberId)(Unsafe)

            for {
              size <- cancelers.size
              _    <- ZIO.when(size >= n0)(cancelers.take.flatMap(_.succeedUnit))
              _    <- cancelers.offer(canceler)
              raceIOs =
                ZIO.scopedWith { scope =>
                  (incoming >>> channel)
                    .toPullInAlt(scope)
                    .flatMap(evaluatePull(_).race(canceler.await.interruptible))
                }
              _ <- permits
                     .withPermit(latch.succeedUnit *> raceIOs)
                     .interruptible
                     .forkIn(childScope)
              _ <- latch.await
            } yield ()
          }

        val setFinalizers: UIO[Unit] =
          ZIO.uninterruptible {
            scope.addFinalizer(outgoing.shutdown) *>
              scope.addFinalizer(cancelers.shutdown)
          }

        for {
          _          <- setFinalizers
          pull       <- (incoming >>> channels).toPullInAlt(scope)
          childScope <- scope.fork
          _ <- (
                 mergeStrategy0 match {
                   case MergeStrategy.BackPressure  => backPressurePull(childScope, pull)
                   case MergeStrategy.BufferSliding => bufferSlidingPull(childScope, pull)
                 }
               ).forever
                 .catchAllCause(cause =>
                   cause.failureOrCause match {
                     case Left(x: Right[OutErr, OutDone]) =>
                       permits.withPermits(n0)(ZIO.unit).interruptible *>
                         ZIO.suspendSucceed {
                           lastDone.get match {
                             case null     => outgoing.offer(Result.Done(x.value))
                             case lastDone => outgoing.offer(Result.Done(f(lastDone, x.value)))
                           }
                         }
                     case Left(l: Left[OutErr, OutDone]) => outgoing.offer(Result.Error(l.value))
                     case Right(cause)                   => outgoing.offer(Result.Fatal(cause))
                   }
                 )
                 .raceFirst(awaitErrorSignal(childScope, fiberId)(errorSignal))
                 .forkIn(scope)
        } yield {
          lazy val consumer: ZChannel[Env, Any, Any, Any, OutErr, OutElem, OutDone] =
            unwrap[Env, Any, Any, Any, OutErr, OutElem, OutDone] {
              outgoing.take.flatMap {
                case Result.Value(outElem)  => Exit.succeed(ZChannel.write(outElem) *> consumer)
                case Result.Done(outDone)   => outgoing.shutdown.as(ZChannel.succeedNow(outDone))
                case Result.Error(outError) => outgoing.shutdown.as(ZChannel.fail(outError))
                case Result.Fatal(cause)    => outgoing.shutdown.as(ZChannel.refailCause(cause))
              }
            }

          consumer.embedInput(input).ensuring(outgoing.shutdown)
        }
      }
    }
  }

  /**
   * Creates a channel that runs the channels written by `channels`, up to `n`
   * of them at the same time, with the back pressure merge strategy, like the
   * other `mergeAllWith` overload. Passing `MergeStrategy.BackPressure` as the
   * argument selects this overload. To call the other one, omit the argument or
   * pass a value typed as `MergeStrategy`.
   */
  @deprecated("use mergeAllWith with `MergeStrategy`", "2.1.7")
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
    bufferSize: => Int /* = 16*/,
    mergeStrategy: MergeStrategy.BackPressure.type
  )(
    f: (OutDone, OutDone) => OutDone
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    mergeAllWith(channels, n, bufferSize, mergeStrategy: MergeStrategy)(f)

  /** Returns a channel that never completes */
  final def never(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Nothing] =
    ZChannel.fromZIO(ZIO.never)

  /**
   * Provides the part `Env` of the environment of `channel` with the given
   * layer, leaving the remainder `Env1` to be provided. The layer is built when
   * the channel starts and released when the channel finishes.
   */
  def provideLayer[Env0, Env, Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone](layer: ZLayer[Env0, OutErr, Env])(
    channel: => ZChannel[Env with Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit
    ev: EnvironmentTag[Env],
    tag: EnvironmentTag[Env1],
    trace: Trace
  ): ZChannel[Env0 with Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.suspend(channel.provideSomeLayer[Env0 with Env1](ZLayer.environment[Env1] <*> layer))

  /**
   * Creates a channel that reads one input: `in` receives the next upstream
   * element, `halt` receives the cause of an upstream failure and `done`
   * receives the upstream done value. The returned channel continues as the
   * channel that the called function creates.
   */
  def readWithCause[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    in: InElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    halt: Cause[InErr] => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    done: InDone => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    Read[Env, InErr, InElem, InDone, InErr, OutErr, OutElem, OutElem, InDone, OutDone](in, new Fold.K(done, halt))

  /**
   * Creates a channel that reads one input: `in` receives the next upstream
   * element, `error` receives a typed upstream error and `done` receives the
   * upstream done value. The returned channel continues as the channel that the
   * called function creates.
   *
   * An upstream cause without a typed error (a defect or an interruption) makes
   * the returned channel fail with that cause.
   */
  def readWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    in: InElem => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    error: InErr => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
    done: InDone => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    readWithCause(in, (c: Cause[InErr]) => c.failureOrCause.fold(error, ZChannel.refailCause(_)), done)

  /**
   * Creates a channel that reads one element and succeeds with it. When
   * upstream finishes or fails instead, the channel fails with `e`.
   */
  def readOrFail[E, In](e: => E)(implicit trace: Trace): ZChannel[Any, Any, In, Any, E, Nothing, In] =
    Read[Any, Any, In, Any, Any, E, Nothing, Nothing, In, In](
      in => SucceedNow(in),
      new Fold.K((_: Any) => ZChannel.fail(e), (_: Any) => ZChannel.fail(e))
    )

  /**
   * Creates a channel that reads one element and succeeds with it. When
   * upstream finishes or fails instead, the channel fails with `None`.
   */
  def read[In](implicit trace: Trace): ZChannel[Any, Any, In, Any, None.type, Nothing, In] =
    readOrFail(None)

  /**
   * Creates a channel that fails with the given cause, which is already
   * evaluated. Use it to propagate a cause received from another channel.
   */
  def refailCause[E](cause: Cause[E]): ZChannel[Any, Any, Any, Any, E, Nothing, Nothing] =
    Fail(() => cause)

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

  /** Creates a channel that succeeds immediately with the given value */
  def succeed[Z](z: => Z)(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Nothing, Nothing, Z] =
    Succeed(() => z)

  /**
   * Returns a lazily constructed channel.
   */
  def suspend[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    Suspend(() => channel)

  /** Channel that succeeds with the unit value */
  val unit: ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] =
    succeedNow(())(Trace.empty)

  /**
   * Creates a channel that runs the given effect and then continues as the
   * channel it produces. If the effect fails, the channel fails with that
   * error.
   */
  def unwrap[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    channel: => ZIO[Env, OutErr, ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.fromZIO(channel).flatten

  /**
   * Creates a channel that runs the scoped effect in a new scope and then
   * continues as the channel it produces. The scope closes when that channel
   * finishes.
   */
  def unwrapScoped[Env]: UnwrapScopedPartiallyApplied[Env] =
    new UnwrapScopedPartiallyApplied[Env]

  /**
   * Creates a channel that runs `f` with a new scope and then continues as the
   * channel it produces. The scope closes when that channel finishes.
   */
  def unwrapScopedWith[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
    f: Scope => ZIO[Env, OutErr, ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
  )(implicit trace: Trace): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
    ZChannel.concatAllWith(ZChannel.scopedWith(f))((d, _) => d, (d, _) => d)

  /**
   * Creates a channel that publishes every element it reads to the hub as a
   * `Right`. When upstream finishes or fails, it publishes a `Left` holding
   * that `Exit` and finishes.
   */
  def toHub[Err, Done, Elem](
    hub: => Hub[Either[Exit[Err, Done], Elem]]
  )(implicit trace: Trace): ZChannel[Any, Err, Elem, Done, Nothing, Nothing, Any] =
    toQueue(hub)

  /**
   * Creates a channel that offers every element it reads to the queue as a
   * `Right`. When upstream finishes or fails, it offers a `Left` holding that
   * `Exit` and finishes.
   */
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

  private[zio] def fromInput[Err, Elem, Done](
    input: => AsyncInputConsumer[Err, Elem, Done]
  )(implicit trace: Trace): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
    ZChannel.suspend {
      def fromInput[Err, Elem, Done](
        input: => AsyncInputConsumer[Err, Elem, Done]
      ): ZChannel[Any, Any, Any, Any, Err, Elem, Done] =
        ZChannel.unwrap(
          input.takeWith(
            ZChannel.refailCause,
            ZChannel.write(_) *> fromInput(input),
            ZChannel.succeedNow(_)
          )
        )

      fromInput(input)
    }

  private[zio] sealed trait MergeState[Env, Err, Err1, Err2, Elem, Done, Done1, Done2]
  private[zio] object MergeState {
    case class BothRunning[Env, Err, Err1, Err2, Elem, Done, Done1, Done2](
      left: Fiber.Runtime[Err, Either[Done, Elem]],
      right: Fiber.Runtime[Err1, Either[Done1, Elem]],
      preferLeft: Boolean // to maintain fairness when polling fibers
    ) extends MergeState[Env, Err, Err1, Err2, Elem, Done, Done1, Done2]
    case class LeftDone[Env, Err, Err1, Err2, Elem, Done, Done1, Done2](
      f: Exit[Err1, Done1] => ZIO[Env, Err2, Done2]
    ) extends MergeState[Env, Err, Err1, Err2, Elem, Done, Done1, Done2]
    case class RightDone[Env, Err, Err1, Err2, Elem, Done, Done1, Done2](
      f: Exit[Err, Done] => ZIO[Env, Err2, Done2]
    ) extends MergeState[Env, Err, Err1, Err2, Elem, Done, Done1, Done2]
  }

  /**
   * Decides what a channel built with `ZChannel#mergeWith` does when one of the
   * two merged channels finishes: either finish now, or wait for the other
   * channel.
   */
  sealed abstract class MergeDecision[-R, -E0, -Z0, +E, +Z]

  /**
   * Constructors for [[ZChannel.MergeDecision]].
   */
  object MergeDecision {

    /**
     * Interrupts the other channel and finishes the merged channel with the
     * result of `zio`.
     */
    case class Done[R, E, Z](zio: ZIO[R, E, Z]) extends MergeDecision[R, Any, Any, E, Z]

    /**
     * Keeps writing the output of the other channel until it finishes, then
     * finishes the merged channel with the result of `f` applied to the `Exit`
     * of the other channel.
     */
    case class Await[R, E0, Z0, E, Z](f: Exit[E0, Z0] => ZIO[R, E, Z]) extends MergeDecision[R, E0, Z0, E, Z]

    /**
     * Interrupts the other channel and finishes the merged channel with the
     * result of `zio`.
     */
    def done[R, E, Z](zio: ZIO[R, E, Z]): MergeDecision[R, Any, Any, E, Z] = Done(zio)

    /**
     * Keeps writing the output of the other channel until it finishes, then
     * finishes the merged channel with the result of `f` applied to the `Exit`
     * of the other channel.
     */
    def await[R, E0, Z0, E, Z](f: Exit[E0, Z0] => ZIO[R, E, Z]): MergeDecision[R, E0, Z0, E, Z] = Await(f)

    /**
     * Keeps writing the output of the other channel until it finishes, then
     * finishes the merged channel with the result of `zio`, ignoring how the
     * other channel ended.
     */
    def awaitConst[R, E, Z](zio: ZIO[R, E, Z]): MergeDecision[R, Any, Any, E, Z] = Await(_ => zio)
  }

  /**
   * Decides how `ZChannel.mergeAll` and `ZChannel#mergeMap` limit the number of
   * channels that run at the same time.
   */
  sealed trait MergeStrategy

  /**
   * The merge strategies.
   */
  object MergeStrategy {

    /**
     * When `n` channels are running, waits until one of them finishes before
     * starting the new one.
     */
    case object BackPressure extends MergeStrategy

    /**
     * Starts the new channel without waiting for a running channel to finish,
     * and may interrupt a channel that was started earlier and is still
     * running.
     */
    case object BufferSliding extends MergeStrategy
  }

  /**
   * Partially applied form of `ZChannel.environmentWith`, which fixes the
   * environment type.
   */
  final class EnvironmentWithPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that succeeds with the result of applying `f` to the
     * environment.
     */
    def apply[OutDone](f: ZEnvironment[Env] => OutDone)(implicit
      trace: Trace
    ): ZChannel[Env, Any, Any, Any, Nothing, Nothing, OutDone] =
      ZChannel.environment.map(f)
  }

  /**
   * Partially applied form of `ZChannel.environmentWithChannel`, which fixes
   * the environment type.
   */
  final class EnvironmentWithChannelPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that continues as the channel that `f` creates from the
     * environment.
     */
    def apply[Env1 <: Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      f: ZEnvironment[Env] => ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    )(implicit trace: Trace): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.environment.flatMap(f)
  }

  /**
   * Partially applied form of `ZChannel.environmentWithZIO`, which fixes the
   * environment type.
   */
  final class EnvironmentWithZIOPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that runs the effect that `f` creates from the
     * environment and succeeds with its result.
     */
    def apply[Env1 <: Env, OutErr, OutDone](f: ZEnvironment[Env] => ZIO[Env1, OutErr, OutDone])(implicit
      trace: Trace
    ): ZChannel[Env1, Any, Any, Any, OutErr, Nothing, OutDone] =
      ZChannel.fromZIO(ZIO.environmentWithZIO[Env1](f))
  }

  /**
   * Partially applied form of `ZChannel#provideSomeLayer`, which fixes the type
   * `Env0` of the environment that remains to be provided.
   */
  final class ProvideSomeLayer[Env0, -Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {

    /**
     * Provides the part `Env1` of the environment with the given layer, leaving
     * `Env0` to be provided. The layer is built when the channel starts and
     * released when the channel finishes.
     */
    def apply[OutErr1 >: OutErr, Env1](
      layer: => ZLayer[Env0, OutErr1, Env1]
    )(implicit
      ev: Env0 with Env1 <:< Env,
      tagged: EnvironmentTag[Env1],
      trace: Trace
    ): ZChannel[Env0, InErr, InElem, InDone, OutErr1, OutElem, OutDone] =
      self
        .asInstanceOf[ZChannel[Env0 with Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
        .provideLayer(ZLayer.environment[Env0] <*> layer)
  }

  /**
   * Partially applied form of `ZChannel.scoped`, which fixes the environment
   * type.
   */
  final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that runs the scoped effect in a new scope, writes its
     * result as the only output element and closes the scope when the channel
     * is closed. If the effect fails, the scope is closed and the channel fails
     * with that error.
     */
    def apply[E, A](
      zio: => ZIO[Scope with R, E, A]
    )(implicit trace: Trace): ZChannel[R, Any, Any, Any, E, A, Any] =
      ZChannel.unwrap {
        ZIO.uninterruptibleMask { restore =>
          Scope.make.map { scope =>
            acquireReleaseOutExitWith {
              restore(scope.extend[R](zio)).tapErrorCause(cause => scope.close(Exit.failCause(cause)))
            } { (_, exit) =>
              scope.close(exit)
            }
          }
        }
      }
  }

  /**
   * Partially applied form of `ZChannel.serviceAt`, which fixes the service
   * type.
   */
  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that succeeds with `Some` of the service stored under
     * `key` in the `Map` environment, or `None` when there is no such key.
     */
    def apply[Key](
      key: => Key
    )(implicit
      tag: EnvironmentTag[Map[Key, Service]],
      trace: Trace
    ): ZChannel[Map[Key, Service], Any, Any, Any, Nothing, Nothing, Option[Service]] =
      ZChannel.environmentWith(_.getAt(key))
  }

  /**
   * Partially applied form of `ZChannel.serviceWith`, which fixes the service
   * type.
   */
  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that succeeds with the result of applying `f` to the
     * service.
     */
    def apply[OutDone](f: Service => OutDone)(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Service, Any, Any, Any, Nothing, Nothing, OutDone] =
      ZChannel.fromZIO(ZIO.serviceWith[Service](f))
  }

  /**
   * Partially applied form of `ZChannel.serviceWithChannel`, which fixes the
   * service type.
   */
  final class ServiceWithChannelPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that continues as the channel that `f` creates from the
     * service.
     */
    def apply[Env <: Service, InErr, InElem, InDone, OutErr, OutElem, OutDone](
      f: Service => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
    )(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.service[Service].flatMap(f)
  }

  /**
   * Partially applied form of `ZChannel.serviceWithZIO`, which fixes the
   * service type.
   */
  final class ServiceWithZIOPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that runs the effect that `f` creates from the service
     * and succeeds with its result.
     */
    def apply[Env <: Service, OutErr, OutDone](f: Service => ZIO[Env, OutErr, OutDone])(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Env, Any, Any, Any, OutErr, Nothing, OutDone] =
      ZChannel.fromZIO(ZIO.serviceWithZIO[Service](f))
  }

  /**
   * Partially applied form of `ZChannel.unwrapScoped`, which fixes the
   * environment type.
   */
  final class UnwrapScopedPartiallyApplied[Env](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Creates a channel that runs the scoped effect in a new scope and then
     * continues as the channel it produces. The scope closes when that channel
     * finishes.
     */
    def apply[InErr, InElem, InDone, OutErr, OutElem, OutDone](
      channel: => ZIO[Scope with Env, OutErr, ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]]
    )(implicit
      trace: Trace
    ): ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      ZChannel.concatAllWith(scoped[Env](channel))((d, _) => d, (d, _) => d)
  }

  /**
   * Partially applied form of `ZChannel#updateService`, which fixes the service
   * type.
   */
  final class UpdateService[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone, Service](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {

    /**
     * Returns the channel with the service in its environment replaced by the
     * result of applying `f` to it.
     */
    def apply[Env1 <: Env with Service](
      f: Service => Service
    )(implicit
      tag: Tag[Service],
      trace: Trace
    ): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      self.provideSomeEnvironment(_.update(f))
  }

  /**
   * Partially applied form of `ZChannel#updateServiceAt`, which fixes the
   * service type.
   */
  final class UpdateServiceAt[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone, Service](
    private val self: ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone]
  ) extends AnyVal {

    /**
     * Returns the channel with the service stored under `key` in its `Map`
     * environment replaced by the result of applying `f` to it.
     */
    def apply[Env1 <: Env with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit
      tag: Tag[Map[Key, Service]],
      trace: Trace
    ): ZChannel[Env1, InErr, InElem, InDone, OutErr, OutElem, OutDone] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

  private[stream] val unitChannelFn: Any => ZChannel[Any, Any, Any, Any, Nothing, Nothing, Unit] = (_: Any) => unit
  private val unitFn2: (Any, Any) => Unit                                                        = (_, _) => ()
  private val failCauseFn: Cause[Any] => ZChannel[Any, Any, Any, Any, Any, Any, Any]             = cause => Fail(() => cause)
  private val identityAny: ZChannel[Any, Any, Any, Any, Any, Any, Any] =
    Read(
      (in: Any) => ZChannel.Fold(Emit(in), new ZChannel.Fold.K((_: Any) => identityAny, failCauseFn)),
      new Fold.K[Any, Any, Any, Any, Any, Any, Any, Any, Any](
        (done: Any) => SucceedNow(done),
        failCauseFn
      )
    )
}
