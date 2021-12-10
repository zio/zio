package zio.stream.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZChannel

import scala.collection.immutable.Queue

class ChannelExecutor[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
  initialChannel: () => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
  @volatile private var providedEnv: ZEnvironment[Any],
  executeCloseLastSubstream: URIO[Env, Any] => URIO[Env, Any]
) {
  import ChannelExecutor._

  private[this] def restorePipe(exit: Exit[Any, Any], prev: ErasedExecutor[Env])(implicit trace: ZTraceElement) = {
    val currInput = input
    input = prev

    currInput.close(exit)
  }

  private[this] final def popAllFinalizers(
    exit: Exit[Any, Any]
  )(implicit trace: ZTraceElement): URIO[Env, Exit[Any, Any]] = {
    def unwind(acc: Exit[Any, Any], conts: List[ErasedContinuation[Env]]): ZIO[Env, Any, Any] =
      conts match {
        case Nil                                => ZIO.done(acc)
        case ZChannel.Fold.K(_, _) :: rest      => unwind(acc, rest)
        case ZChannel.Fold.Finalizer(f) :: rest => f(exit).exit.flatMap(finExit => unwind(acc *> finExit, rest))
      }

    val effect = unwind(Exit.unit, doneStack).exit
    doneStack = Nil
    storeInProgressFinalizer(effect)
    effect
  }

  private[this] final def popNextFinalizers(): List[ZChannel.Fold.Finalizer[Env, Any, Any]] = {
    val builder = List.newBuilder[ZChannel.Fold.Finalizer[Env, Any, Any]]

    def go(stack: List[ErasedContinuation[Env]]): List[ErasedContinuation[Env]] =
      stack match {
        case Nil                        => Nil
        case ZChannel.Fold.K(_, _) :: _ => stack
        case (finalizer @ ZChannel.Fold.Finalizer(_)) :: rest =>
          builder += finalizer.asInstanceOf[ZChannel.Fold.Finalizer[Env, Any, Any]]
          go(rest)
      }

    doneStack = go(doneStack)
    builder.result()
  }

  private[this] final def storeInProgressFinalizer(finalizer: URIO[Env, Exit[Any, Any]]): Unit =
    inProgressFinalizer = finalizer

  private[this] final def clearInProgressFinalizer(): Unit =
    inProgressFinalizer = null

  def close(ex: Exit[Any, Any])(implicit trace: ZTraceElement): ZIO[Env, Nothing, Any] = {
    def ifNotNull[R, E](zio: URIO[R, Exit[E, Any]]): URIO[R, Exit[E, Any]] =
      if (zio ne null) zio else UIO.succeed(Exit.unit)

    val runInProgressFinalizers = {
      val finalizer = inProgressFinalizer

      if (finalizer ne null)
        finalizer.ensuring(UIO(clearInProgressFinalizer()))
      else null
    }

    val closeSubexecutors =
      if (subexecutorStack eq null) null
      else
        subexecutorStack match {
          case exec: SubexecutorStack.Inner[_] =>
            exec.close(ex).asInstanceOf[ZIO[Env, Nothing, Exit[Any, Any]]]

          case SubexecutorStack.FromKAnd(fromK, rest) =>
            val fin1 = fromK.close(ex)
            val fin2 = rest.close(ex)

            if ((fin1 eq null) && (fin2 eq null)) null
            else if ((fin1 ne null) && (fin2 ne null)) fin1.exit.zipWith(fin2.exit)(_ *> _)
            else if (fin1 ne null) fin1.exit
            else fin2.exit
        }

    val closeSelf: URIO[Env, Exit[Any, Any]] = {
      val selfFinalizers = popAllFinalizers(ex)

      if (selfFinalizers ne null)
        selfFinalizers.ensuring(UIO(clearInProgressFinalizer()))
      else null
    }

    if ((closeSubexecutors eq null) && (runInProgressFinalizers eq null) && (closeSelf eq null)) null
    else
      (
        ifNotNull(closeSubexecutors) <*>
          ifNotNull(runInProgressFinalizers) <*>
          ifNotNull(closeSelf)
      ).map { case (a, b, c) => a *> b *> c }.uninterruptible
  }

  def getDone: Exit[OutErr, OutDone] = done.asInstanceOf[Exit[OutErr, OutDone]]

  def getEmit: OutElem = emitted.asInstanceOf[OutElem]

  def cancelWith(exit: Exit[OutErr, OutDone]): Unit =
    cancelled = exit

  final def run()(implicit trace: ZTraceElement): ChannelState[Env, Any] = {
    var result: ChannelState[Env, Any] = null

    while (result eq null) {
      if (cancelled ne null) {
        result = processCancellation()
      } else if (subexecutorStack ne null) {
        result = drainSubexecutor()
      } else {
        currentChannel match {
          case null =>
            result = ChannelState.Done

          case ZChannel.Bridge(bridgeInput, channel) =>
            // PipeTo(left, Bridge(queue, channel))
            // In a fiber: repeatedly run left and push its outputs to the queue
            // Add a finalizer to interrupt the fiber and close the executor
            currentChannel = channel

            if (input ne null) {
              val inputExecutor = input
              input = null

              lazy val drainer: URIO[Env, Any] =
                bridgeInput.awaitRead *> ZIO.suspendSucceed {
                  val state = inputExecutor.run()

                  state match {
                    case ChannelState.Done =>
                      val sendInput = inputExecutor.getDone match {
                        case Exit.Failure(cause) => bridgeInput.error(cause)
                        case Exit.Success(value) => bridgeInput.done(value)
                      }

                      sendInput

                    case ChannelState.Emit =>
                      bridgeInput.emit(inputExecutor.getEmit) *> drainer

                    case ChannelState.Effect(zio) =>
                      zio.foldCauseZIO(
                        cause => bridgeInput.error(cause),
                        _ => drainer
                      )
                  }
                }

              result = ChannelState.Effect(
                drainer.fork.flatMap { fiber =>
                  UIO(addFinalizer { exit =>
                    fiber.interrupt *>
                      ZIO.suspendSucceed {
                        val effect = restorePipe(exit, inputExecutor)

                        if (effect ne null) effect
                        else UIO.unit
                      }
                  })
                }
              )
            }

          case ZChannel.PipeTo(left, right) =>
            val previousInput = input

            val leftExec: ErasedExecutor[Env] = new ChannelExecutor(left, providedEnv, executeCloseLastSubstream)
            leftExec.input = previousInput
            input = leftExec

            addFinalizer { exit =>
              val effect = restorePipe(exit, previousInput)

              if (effect ne null) effect
              else UIO.unit
            }

            currentChannel = right().asInstanceOf[Channel[Env]]

          case read @ ZChannel.Read(_, _) =>
            result = runRead(read.asInstanceOf[ZChannel.Read[Env, Any, Any, Any, Any, Any, Any, Any, Any, Any]])

          case ZChannel.SucceedNow(terminal) =>
            result = doneSucceed(terminal)

          case ZChannel.Fail(error) =>
            result = doneHalt(error())

          case ZChannel.Succeed(effect) =>
            result = doneSucceed(effect())

          case ZChannel.Suspend(effect) =>
            currentChannel = effect()

          case ZChannel.FromZIO(zio) =>
            val pzio =
              (if (providedEnv eq null) zio()
               else zio().provideEnvironment(providedEnv.asInstanceOf[ZEnvironment[Env]]))
                .asInstanceOf[ZIO[Env, OutErr, OutDone]]

            result = ChannelState.Effect(
              pzio
                .foldCauseZIO(
                  cause =>
                    doneHalt(cause) match {
                      case ChannelState.Effect(zio) => zio
                      case _                        => ZIO.unit
                    },
                  z =>
                    doneSucceed(z) match {
                      case ChannelState.Effect(zio) => zio
                      case _                        => ZIO.unit
                    }
                )
            )

          case ZChannel.Emit(out) =>
            emitted = out
            currentChannel = ZChannel.unit
            result = ChannelState.Emit

          case ensuring @ ZChannel.Ensuring(_, _) =>
            runEnsuring(ensuring.asInstanceOf[ZChannel.Ensuring[Env, Any, Any, Any, Any, Any, Any]])

          case ZChannel.ConcatAll(combineSubK, combineSubKAndInner, value, k) =>
            val innerExecuteLastClose =
              (f: URIO[Env, Any]) =>
                UIO {
                  val prevLastClose = if (closeLastSubstream eq null) ZIO.unit else closeLastSubstream
                  closeLastSubstream = prevLastClose *> f
                }

            val exec: ErasedExecutor[Env] = new ChannelExecutor(value, providedEnv, innerExecuteLastClose)
            exec.input = input

            subexecutorStack = SubexecutorStack.Inner(
              exec,
              k.asInstanceOf[Any => Channel[Env]],
              lastDone = null,
              combineSubK.asInstanceOf[(Any, Any) => Any],
              combineSubKAndInner.asInstanceOf[(Any, Any) => Any]
            )
            closeLastSubstream = null
            currentChannel = null

          case ZChannel.Fold(value, k) =>
            doneStack = k.asInstanceOf[ErasedContinuation[Env]] :: doneStack
            currentChannel = value

          case bracketOut @ ZChannel.BracketOut(_, _) =>
            result = runBracketOut(bracketOut.asInstanceOf[ZChannel.BracketOut[Env, Any, Any]])

          case ZChannel.Provide(env, inner) =>
            val previousEnv = providedEnv
            providedEnv = env()
            currentChannel = inner.asInstanceOf[Channel[Env]]

            addFinalizer { _ =>
              URIO {
                providedEnv = previousEnv
              }
            }
        }
      }
    }

    result
  }

  private[this] var currentChannel: Channel[Env] = erase(initialChannel())

  private[this] var done: Exit[Any, Any] = _

  private[this] var doneStack: List[ErasedContinuation[Env]] = Nil

  private[this] var emitted: Any = _

  @volatile
  private[this] var inProgressFinalizer: URIO[Env, Exit[Any, Any]] = _

  @volatile
  var input: ErasedExecutor[Env] = _

  private[this] var subexecutorStack: SubexecutorStack[Env] = _

  private[this] var cancelled: Exit[OutErr, OutDone] = _

  private[this] var closeLastSubstream: URIO[Env, Any] = _

  private[this] def doneSucceed(z: Any)(implicit trace: ZTraceElement): ChannelState[Env, Any] =
    doneStack match {
      case Nil =>
        done = Exit.succeed(z)
        currentChannel = null
        ChannelState.Done

      case ZChannel.Fold.K(onSuccess, _) :: rest =>
        doneStack = rest
        currentChannel = onSuccess(z)
        null

      case ZChannel.Fold.Finalizer(_) :: _ =>
        val finalizers = popNextFinalizers()

        if (doneStack.isEmpty) {
          doneStack = finalizers
          done = Exit.succeed(z)
          currentChannel = null
          ChannelState.Done
        } else {
          val finalizerEffect =
            runFinalizers(finalizers.map(_.finalizer), Exit.succeed(z))
          storeInProgressFinalizer(finalizerEffect)

          ChannelState.Effect(
            finalizerEffect
              .ensuring(
                UIO(clearInProgressFinalizer())
              )
              .uninterruptible *> UIO(doneSucceed(z))
          )
        }
    }

  private[this] def doneHalt(cause: Cause[Any])(implicit trace: ZTraceElement): ChannelState[Env, Any] =
    doneStack match {
      case Nil =>
        done = Exit.failCause(cause)
        currentChannel = null
        ChannelState.Done

      case ZChannel.Fold.K(_, onHalt) :: rest =>
        doneStack = rest
        currentChannel = onHalt(cause)
        null

      case ZChannel.Fold.Finalizer(_) :: _ =>
        val finalizers = popNextFinalizers()

        if (doneStack.isEmpty) {
          doneStack = finalizers
          done = Exit.failCause(cause)
          currentChannel = null
          ChannelState.Done
        } else {
          val finalizerEffect = runFinalizers(finalizers.map(_.finalizer), Exit.failCause(cause))
          storeInProgressFinalizer(finalizerEffect)

          ChannelState.Effect(
            finalizerEffect
              .ensuring(UIO(clearInProgressFinalizer()))
              .uninterruptible *> UIO(doneHalt(cause))
          )
        }
    }

  private[this] def processCancellation(): ChannelState[Env, Any] = {
    currentChannel = null
    done = cancelled
    cancelled = null
    ChannelState.Done
  }

  private def runRead(
    read: ZChannel.Read[Env, Any, Any, Any, Any, Any, Any, Any, Any, Any]
  )(implicit trace: ZTraceElement): ChannelState.Effect[Env, Any] =
    if (input eq null) {
      currentChannel = read.more(())
      null
    } else {
      def go(state: ChannelState[Env, Any]): URIO[Env, Unit] =
        state match {
          case ChannelState.Emit =>
            UIO {
              currentChannel = read.more(input.getEmit)
            }

          case ChannelState.Done =>
            UIO {
              currentChannel = read.done.onExit(input.getDone)
            }

          case ChannelState.Effect(zio) =>
            zio.foldCauseZIO(
              cause =>
                UIO {
                  currentChannel = read.done.onHalt(cause)
                },
              _ => go(input.run())
            )
        }

      input.run() match {
        case ChannelState.Emit =>
          currentChannel = read.more(input.getEmit)
          null

        case ChannelState.Done =>
          currentChannel = read.done.onExit(input.getDone)
          null

        case ChannelState.Effect(zio) =>
          ChannelState.Effect(
            zio.foldCauseZIO(
              cause =>
                UIO {
                  currentChannel = read.done.onHalt(cause)
                },
              _ => go(input.run())
            )
          )
      }
    }

  private def runBracketOut(
    bracketOut: ZChannel.BracketOut[Env, Any, Any]
  )(implicit trace: ZTraceElement): ChannelState.Effect[Env, Any] =
    ChannelState.Effect {
      ZIO.uninterruptibleMask { restore =>
        restore(bracketOut.acquire()).foldCauseZIO(
          cause => UIO { currentChannel = ZChannel.failCause(cause) },
          out =>
            UIO {
              addFinalizer(bracketOut.finalizer(out, _))
              currentChannel = ZChannel.write(out)
            }
        )
      }
    }

  private[this] def runEnsuring(ensuring: ZChannel.Ensuring[Env, Any, Any, Any, Any, Any, Any]) = {
    addFinalizer(ensuring.finalizer)
    currentChannel = ensuring.channel
  }

  private[this] def addFinalizer(f: Finalizer[Env]): Unit =
    doneStack = ZChannel.Fold.Finalizer(f) :: doneStack

  private[this] def runFinalizers(finalizers: List[Finalizer[Env]], ex: Exit[Any, Any])(implicit
    trace: ZTraceElement
  ): URIO[Env, Exit[Any, Any]] =
    if (finalizers.isEmpty) null
    else
      ZIO
        .foreach(finalizers)(_.apply(ex).exit)
        .map(results => Exit.collectAll(results) getOrElse Exit.unit)

  private[this] def drainSubexecutor()(implicit trace: ZTraceElement): ChannelState[Env, Any] =
    subexecutorStack match {
      case inner @ SubexecutorStack.Inner(_, _, _, _, _) =>
        drainInnerSubexecutor(inner.asInstanceOf[SubexecutorStack.Inner[Env]])

      case SubexecutorStack.FromKAnd(exec, rest) =>
        drainFromKAndSubexecutor(exec.asInstanceOf[ErasedExecutor[Env]], rest.asInstanceOf[SubexecutorStack.Inner[Env]])
    }

  private def replaceSubexecutor(nextSubExec: SubexecutorStack.Inner[Env]): Unit = {
    currentChannel = null
    subexecutorStack = nextSubExec
  }

  private def finishSubexecutorWithCloseEffect(
    subexecDone: Exit[Any, Any],
    closeFns: (Exit[Any, Any]) => URIO[Env, Any]*
  )(implicit trace: ZTraceElement): ChannelState[Env, Any] = {
    addFinalizer { _ =>
      ZIO.foreachDiscard(closeFns) { closeFn =>
        UIO(closeFn(subexecDone)).flatMap { closeEffect =>
          if (closeEffect ne null) {
            closeEffect
          } else {
            ZIO.unit
          }
        }
      }
    }

    val state = subexecDone.fold(doneHalt, doneSucceed)
    subexecutorStack = null
    state
  }

  def finishWithExit(exit: Exit[Any, Any])(implicit trace: ZTraceElement): ZIO[Env, Any, Any] = {
    val state = exit.fold(doneHalt, doneSucceed)
    subexecutorStack = null

    if (state eq null) UIO.unit
    else state.effect
  }

  private def drainFromKAndSubexecutor(
    exec: ErasedExecutor[Env],
    rest: SubexecutorStack.Inner[Env]
  )(implicit trace: ZTraceElement): ChannelState[Env, Any] = {
    def handleSubexecFailure(cause: Cause[Any]): ChannelState[Env, Any] =
      finishSubexecutorWithCloseEffect(
        Exit.failCause(cause),
        rest.exec.close,
        exec.close
      )

    exec.run() match {
      case ChannelState.Emit =>
        emitted = exec.getEmit
        ChannelState.Emit

      case ChannelState.Effect(zio) =>
        ChannelState.Effect(
          zio.catchAllCause(cause => handleSubexecFailure(cause).effect)
        )

      case ChannelState.Done =>
        exec.getDone match {
          case Exit.Failure(cause) => handleSubexecFailure(cause)
          case e @ Exit.Success(doneValue) =>
            val modifiedRest =
              rest.copy(
                lastDone =
                  if (rest.lastDone != null) rest.combineSubK(rest.lastDone, doneValue)
                  else doneValue
              )
            closeLastSubstream = exec.close(e)
            replaceSubexecutor(modifiedRest)
            null
        }
    }
  }

  private final def drainInnerSubexecutor(
    inner: SubexecutorStack.Inner[Env]
  )(implicit trace: ZTraceElement): ChannelState[Env, Any] =
    inner.exec.run() match {
      case ChannelState.Emit =>
        if (this.closeLastSubstream ne null) {
          val closeLast = this.closeLastSubstream
          closeLastSubstream = null

          ChannelState.Effect {
            executeCloseLastSubstream(closeLast).map { _ =>
              val fromK: ErasedExecutor[Env] =
                new ChannelExecutor(() => inner.subK(inner.exec.getEmit), providedEnv, executeCloseLastSubstream)
              fromK.input = input

              subexecutorStack = SubexecutorStack.FromKAnd[Env](fromK, inner)
            }
          }
        } else {
          val fromK: ErasedExecutor[Env] =
            new ChannelExecutor(() => inner.subK(inner.exec.getEmit), providedEnv, executeCloseLastSubstream)
          fromK.input = input

          subexecutorStack = SubexecutorStack.FromKAnd[Env](fromK, inner)
          null
        }

      case ChannelState.Done =>
        val lastClose = this.closeLastSubstream
        inner.exec.getDone match {
          case e @ Exit.Failure(_) =>
            finishSubexecutorWithCloseEffect(
              e,
              _ => lastClose,
              inner.exec.close
            )

          case Exit.Success(innerDoneValue) =>
            val doneValue =
              Exit.succeed(inner.combineSubKAndInner(inner.lastDone, innerDoneValue))

            finishSubexecutorWithCloseEffect(
              doneValue,
              _ => lastClose,
              inner.exec.close
            )
        }

      case ChannelState.Effect(zio) =>
        val closeLast =
          if (closeLastSubstream eq null) ZIO.unit else closeLastSubstream
        closeLastSubstream = null
        ChannelState.Effect(
          executeCloseLastSubstream(closeLast) *> zio.catchAllCause(cause =>
            finishSubexecutorWithCloseEffect(
              Exit.failCause(cause),
              inner.exec.close
            ).effect
          )
        )
    }
}

object ChannelExecutor {
  type Channel[R]            = ZChannel[R, Any, Any, Any, Any, Any, Any]
  type ErasedExecutor[Env]   = ChannelExecutor[Env, Any, Any, Any, Any, Any, Any]
  type ErasedContinuation[R] = ZChannel.Fold.Continuation[R, Any, Any, Any, Any, Any, Any, Any, Any]
  type Finalizer[R]          = Exit[Any, Any] => URIO[R, Any]

  sealed trait ChannelState[-R, +E] { self =>
    def effect: ZIO[R, E, Any] =
      self match {
        case ChannelState.Effect(zio) => zio
        case _                        => UIO.unit
      }
  }

  object ChannelState {
    def unroll[R, E](
      runStep: () => ChannelState[R, E]
    )(implicit trace: ZTraceElement): ZIO[R, E, Either[ChannelState.Emit.type, ChannelState.Done.type]] =
      runStep() match {
        case Done        => UIO.succeed(Right(Done))
        case Emit        => UIO.succeed(Left(Emit))
        case Effect(zio) => zio *> unroll(runStep)
      }

    case object Emit                                   extends ChannelState[Any, Nothing]
    case object Done                                   extends ChannelState[Any, Nothing]
    final case class Effect[R, E](zio: ZIO[R, E, Any]) extends ChannelState[R, E]
  }

  def maybeCloseBoth[Env](l: ZIO[Env, Nothing, Any], r: ZIO[Env, Nothing, Any])(implicit
    trace: ZTraceElement
  ): URIO[Env, Exit[Nothing, Any]] =
    if ((l eq null) && (r eq null)) null
    else if ((l ne null) && (r ne null)) l.exit.zipWith(r.exit)(_ *> _)
    else if (l ne null) l.exit
    else r.exit

  sealed abstract class SubexecutorStack[-R]
  object SubexecutorStack {
    case class FromKAnd[R](fromK: ErasedExecutor[R], rest: Inner[R]) extends SubexecutorStack[R]
    final case class Inner[R](
      exec: ErasedExecutor[R],
      subK: Any => Channel[R],
      lastDone: Any,
      combineSubK: (Any, Any) => Any,
      combineSubKAndInner: (Any, Any) => Any
    ) extends SubexecutorStack[R] { self =>
      def close(ex: Exit[Any, Any])(implicit trace: ZTraceElement): URIO[R, Exit[Any, Any]] = {
        val fin = exec.close(ex)

        if (fin ne null) fin.exit
        else null
      }
    }
  }

  private def erase[R](conduit: ZChannel[R, _, _, _, _, _, _]): Channel[R] =
    conduit.asInstanceOf[Channel[R]]
}

/**
 * Consumer-side view of [[SingleProducerAsyncInput]] for variance purposes.
 */
private[zio] trait AsyncInputConsumer[+Err, +Elem, +Done] {
  def takeWith[A](
    onError: Cause[Err] => A,
    onElement: Elem => A,
    onDone: Done => A
  )(implicit trace: ZTraceElement): UIO[A]
}

/**
 * Producer-side view of [[SingleProducerAsyncInput]] for variance purposes.
 */
private[zio] trait AsyncInputProducer[-Err, -Elem, -Done] {
  def emit(el: Elem)(implicit trace: ZTraceElement): UIO[Any]
  def done(a: Done)(implicit trace: ZTraceElement): UIO[Any]
  def error(cause: Cause[Err])(implicit trace: ZTraceElement): UIO[Any]
  def awaitRead(implicit trace: ZTraceElement): UIO[Any]
}

/**
 * An MVar-like abstraction for sending data to channels asynchronously.
 * Designed for one producer and multiple consumers.
 *
 * Features the following semantics:
 *   - Buffer of size 1
 *   - When emitting, the producer waits for a consumer to pick up the value to
 *     prevent "reading ahead" too much.
 *   - Once an emitted element is read by a consumer, it is cleared from the
 *     buffer, so that at most one consumer sees every emitted element.
 *   - When sending a done or error signal, the producer does not wait for a
 *     consumer to pick up the signal. The signal stays in the buffer after
 *     being read by a consumer, so it can be propagated to multiple consumers.
 *   - Trying to publish another emit/error/done after an error/done have
 *     already been published results in an interruption.
 */
private[zio] class SingleProducerAsyncInput[Err, Elem, Done](
  ref: Ref[SingleProducerAsyncInput.State[Err, Elem, Done]]
) extends AsyncInputConsumer[Err, Elem, Done]
    with AsyncInputProducer[Err, Elem, Done] {
  import SingleProducerAsyncInput.State

  def emit(el: Elem)(implicit trace: ZTraceElement): UIO[Any] =
    Promise.make[Nothing, Unit].flatMap { p =>
      ref.modify {
        case s @ State.Emit(notifyConsumers) =>
          val (notifyConsumer, notifyConsumers) = s.notifyConsumers.dequeue
          (
            notifyConsumer.succeed(Right(el)),
            if (notifyConsumers.isEmpty) State.Empty(p)
            else State.Emit(notifyConsumers)
          )
        case s @ State.Error(_)              => (ZIO.interrupt, s)
        case s @ State.Done(_)               => (ZIO.interrupt, s)
        case s @ State.Empty(notifyProducer) => (notifyProducer.await, s)
      }.flatten
    }

  def done(a: Done)(implicit trace: ZTraceElement): UIO[Any] =
    ref.modify {
      case State.Emit(notifyConsumers)     => (ZIO.foreachDiscard(notifyConsumers)(_.succeed(Left(a))), State.Done(a))
      case s @ State.Error(_)              => (ZIO.interrupt, s)
      case s @ State.Done(_)               => (ZIO.interrupt, s)
      case s @ State.Empty(notifyProducer) => (notifyProducer.await, s)
    }.flatten

  def error(cause: Cause[Err])(implicit trace: ZTraceElement): UIO[Any] =
    ref.modify {
      case State.Emit(notifyConsumers)     => (ZIO.foreachDiscard(notifyConsumers)(_.failCause(cause)), State.Error(cause))
      case s @ State.Error(_)              => (ZIO.interrupt, s)
      case s @ State.Done(_)               => (ZIO.interrupt, s)
      case s @ State.Empty(notifyProducer) => (notifyProducer.await, s)
    }.flatten

  def takeWith[A](
    onError: Cause[Err] => A,
    onElement: Elem => A,
    onDone: Done => A
  )(implicit trace: ZTraceElement): UIO[A] =
    Promise.make[Err, Either[Done, Elem]].flatMap { p =>
      ref.modify {
        case State.Emit(notifyConsumers) =>
          (p.await.foldCause(onError, _.fold(onDone, onElement)), State.Emit(notifyConsumers.enqueue(p)))
        case s @ State.Error(a) => (UIO.succeed(onError(a)), s)
        case s @ State.Done(a)  => (UIO.succeed(onDone(a)), s)
        case s @ State.Empty(notifyProducer) =>
          (notifyProducer.succeed(()) *> p.await.foldCause(onError, _.fold(onDone, onElement)), State.Emit(Queue(p)))
      }.flatten
    }

  def take[A](implicit trace: ZTraceElement): UIO[Exit[Either[Err, Done], Elem]] =
    takeWith(c => Exit.failCause(c.map(Left(_))), Exit.succeed(_), d => Exit.fail(Right(d)))

  def close(implicit trace: ZTraceElement): UIO[Any] =
    ZIO.fiberId.flatMap(id => error(Cause.interrupt(id)))

  def awaitRead(implicit trace: ZTraceElement): UIO[Any] =
    ref.modify {
      case s @ State.Empty(notifyProducer) => (notifyProducer.await, s)
      case s                               => (ZIO.unit, s)
    }.flatten
}

private[zio] object SingleProducerAsyncInput {
  def make[Err, Elem, Done](implicit trace: ZTraceElement): UIO[SingleProducerAsyncInput[Err, Elem, Done]] =
    Promise
      .make[Nothing, Unit]
      .flatMap(p => Ref.make[State[Err, Elem, Done]](State.Empty(p)))
      .map(new SingleProducerAsyncInput(_))

  sealed trait State[Err, Elem, Done]
  object State {
    case class Empty[Err, Elem, Done](notifyProducer: Promise[Nothing, Unit]) extends State[Err, Elem, Done]
    case class Emit[Err, Elem, Done](notifyConsumers: Queue[Promise[Err, Either[Done, Elem]]])
        extends State[Err, Elem, Done]
    case class Error[Err, Elem, Done](cause: Cause[Err]) extends State[Err, Elem, Done]
    case class Done[Err, Elem, Done](done: Done)         extends State[Err, Elem, Done]
  }
}
