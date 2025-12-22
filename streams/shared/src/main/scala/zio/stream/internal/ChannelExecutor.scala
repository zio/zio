package zio.stream.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZChannel

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.collection.mutable.Stack
import scala.collection.mutable.ListBuffer

private[stream] final class ChannelExecutor[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone](
  initialChannel: () => ZChannel[Env, InErr, InElem, InDone, OutErr, OutElem, OutDone],
  @volatile private var providedEnv: ZEnvironment[Any],
  executeCloseLastSubstream: URIO[Env, Any] => URIO[Env, Any]
) {
  import ChannelExecutor._

  private[this] def restorePipe(exit: Exit[Any, Any], prev: ErasedExecutor[Env])(implicit
    trace: Trace
  ): ZIO[Env, Nothing, Any] = {
    val currInput = input
    input = prev

    if (currInput ne null) currInput.close(exit) else Exit.unit
  }

  private[this] final def popAllFinalizersOrNull(exit: Exit[Any, Any])(implicit trace: Trace): URIO[Env, Any] = {

    @tailrec
    def unwind(
      acc: ListBuffer[ZChannel.Fold.Finalizer[Env, Any, Any]]
    ): Iterable[ZChannel.Fold.Finalizer[Env, Any, Any]] =
      if (doneStack.isEmpty) acc
      else {
        doneStack.pop() match {
          case f: ZChannel.Fold.Finalizer[Env, Any, Any] => unwind(acc += f)
          case _                                         => unwind(acc)
        }
      }

    val finalizers = unwind(ListBuffer.empty)
    val effect     = runFinalizersOrNull(finalizers, exit)
    storeInProgressFinalizer(effect)
    effect
  }

  private[this] final def popNextFinalizers(): Stack[ZChannel.Fold.Finalizer[Env, Any, Any]] = {
    val builder = Stack.newBuilder[ZChannel.Fold.Finalizer[Env, Any, Any]]

    @tailrec
    def loop(): Unit =
      if (doneStack.nonEmpty) {
        doneStack.head match {
          case finalizer: ZChannel.Fold.Finalizer[Env, Any, Any] =>
            builder += finalizer
            doneStack.pop()
            loop()
          case _ => ()
        }
      }

    loop()
    builder.result()
  }

  private[this] final def storeInProgressFinalizer(finalizer: URIO[Env, Any]): Boolean =
    if (isNullOrZIOUnit(finalizer)) {
      clearInProgressFinalizer()
      false
    } else {
      inProgressFinalizer = finalizer
      true
    }

  private[this] final def clearInProgressFinalizer(): Unit =
    inProgressFinalizer = null

  def close(ex: Exit[Any, Any])(implicit trace: Trace): ZIO[Env, Nothing, Any] = ZIO.uninterruptible {
    val fs = Array.ofDim[URIO[Env, Any]](4)

    // NOTE: Each finalizer might have altered the state of the vars, so we need to suspend them all
    fs(0) = ZIO.suspendSucceed {
      val close0 = closeLastSubstream
      closeLastSubstream = null
      if (close0 eq null) Exit.unit
      else close0
    }

    fs(1) = ZIO.suspendSucceed {
      val finalizer = inProgressFinalizer

      if (finalizer eq null) Exit.unit
      else finalizer.ensuring(ZIO.succeed(clearInProgressFinalizer()))
    }

    fs(2) = ZIO.suspendSucceed {
      if (activeSubexecutor eq null) Exit.unit
      else activeSubexecutor.close(ex)
    }

    fs(3) = ZIO.suspendSucceed {
      val selfFinalizers = popAllFinalizersOrNull(ex)

      if (selfFinalizers eq null) Exit.unit
      else selfFinalizers.ensuring(ZIO.succeed(clearInProgressFinalizer()))
    }

    ZIO.foreach(fs)(_.exit).flatMap(Exit.collectAllDiscard(_))
  }

  def getDone: Exit[OutErr, OutDone] = done.asInstanceOf[Exit[OutErr, OutDone]]

  def getEmit: OutElem = emitted.asInstanceOf[OutElem]

  final def run()(implicit trace: Trace): ChannelState[Env, Any] = {
    var result: ChannelState[Env, Any] = null

    while (result eq null) {
      if (activeSubexecutor ne null) {
        result = runSubexecutor()
      } else {
        try {
          currentChannel match {
            case null =>
              result = ChannelState.Done

            case ZChannel.Fold(value, k) =>
              doneStack.push(k.asInstanceOf[ErasedContinuation[Env]])
              currentChannel = value

            case ZChannel.Emit(out) =>
              emitted = out
              currentChannel = if (activeSubexecutor ne null) null else ZChannel.unit
              result = ChannelState.Emit

            case ZChannel.SucceedNow(terminal) =>
              result = doneSucceed(terminal)

            case ZChannel.Succeed(effect) =>
              result = doneSucceed(effect())

            case ZChannel.Fail(error) =>
              result = doneHalt(error())

            case ZChannel.Suspend(effect) =>
              currentChannel = effect()

            case ZChannel.FromZIO(zio) =>
              val pzio = provide(zio())

              if (pzio eq ZIO.unit) {
                result = doneSucceed(()) match {
                  case effect: ChannelState.Effect[?, ?] => effect
                  case _                                 => ChannelState.Effect.unit
                }
              } else {
                result = ChannelState.Effect(
                  pzio
                    .foldCauseZIO(
                      cause =>
                        doneHalt(cause) match {
                          case ChannelState.Effect(zio) => zio
                          case _                        => Exit.unit
                        },
                      z =>
                        doneSucceed(z) match {
                          case ChannelState.Effect(zio) => zio
                          case _                        => Exit.unit
                        }
                    )
                )
              }

            case ZChannel.PipeTo(left, right) =>
              val previousInput = input

              val leftExec: ErasedExecutor[Env] = new ChannelExecutor(left, providedEnv, executeCloseLastSubstream)
              leftExec.input = previousInput
              input = leftExec

              addFinalizer(exit => restorePipe(exit, previousInput))

              currentChannel = right().asInstanceOf[Channel[Env]]

            case read: ZChannel.Read[?, ?, ?, ?, ?, ?, ?, ?, ?, ?] =>
              result = ChannelState.Read(
                input,
                onEffect = ZIO.identityFn[ZIO[Env, Nothing, Unit]],
                onEmit = { (out: Any) =>
                  try {
                    currentChannel = read.more(out)
                  } catch {
                    case e: Throwable =>
                      currentChannel = read.done.onExit(Exit.die(e))
                  }
                  null
                },
                onDone = { (exit: Exit[Any, Any]) =>
                  val onExit: Exit[Any, Any] => Channel[Env] =
                    (read.done.onExit _).asInstanceOf[Exit[Any, Any] => Channel[Env]]
                  currentChannel = onExit(exit)
                  null
                }
              )

            case ensuring: ZChannel.Ensuring[Env, Any, Any, Any, Any, Any, Any] =>
              runEnsuring(ensuring)

            case ZChannel.ConcatAll(combineSubK, combineSubKAndInner, value, k) =>
              val innerExecuteLastClose =
                (f: URIO[Env, Any]) =>
                  ZIO.succeed {
                    closeLastSubstream = if (closeLastSubstream eq null) f else closeLastSubstream *> f
                  }

              val exec: ErasedExecutor[Env] = new ChannelExecutor(value, providedEnv, innerExecuteLastClose)
              exec.input = input

              activeSubexecutor = Subexecutor.PullFromUpstream(
                exec,
                k.asInstanceOf[Any => Channel[Env]],
                lastDone = null,
                combineSubK.asInstanceOf[(Any, Any) => Any],
                combineSubKAndInner.asInstanceOf[(Any, Any) => Any]
              )
              closeLastSubstream = null
              currentChannel = null

            case bracketOut: ZChannel.BracketOut[Env, Any, Any] =>
              result = runBracketOut(bracketOut)

            case ZChannel.Provide(env, inner) =>
              val previousEnv = providedEnv
              providedEnv = env()
              currentChannel = inner.asInstanceOf[Channel[Env]]

              addFinalizer { _ =>
                ZIO.succeed {
                  providedEnv = previousEnv
                }
              }

            case ZChannel.DeferedUpstream(mkChannel) =>
              val inpAsChannel: ZChannel[Env, Any, Any, Any, Any, Any, Any] = execToPullingChannel(input)

              // when input's provided env is null, we have to explicitly provide it with the 'outer' env
              // otherwise any env provided by downstream will override the 'correct' env when the input channel executes effects.
              val nextChannel: Channel[Env] =
                if (null ne input.providedEnv)
                  mkChannel(inpAsChannel.asInstanceOf[ZChannel[Any, Any, Any, Any, Any, Any, Any]])
                else
                  ZChannel // TODO: can we eliminate the effect evaluation here? i.e. by usingFiber.currentFiber()
                    .environmentWithChannel[Env] { env =>
                      mkChannel(inpAsChannel.provideEnvironment(env))
                    }

              val previousInput = input
              input = null
              addFinalizer(exit => restorePipe(exit, previousInput))
              currentChannel = nextChannel

            case ZChannel.Bridge(bridgeInput, channel) =>
              // PipeTo(left, Bridge(queue, channel))
              // In a fiber: repeatedly run left and push its outputs to the queue
              // Add a finalizer to interrupt the fiber and close the executor
              currentChannel = channel

              if (input ne null) {
                val inputExecutor = input
                input = null

                lazy val drainer: URIO[Env, Any] =
                  bridgeInput.awaitRead.flatMap { _ =>
                    val state = inputExecutor.run()

                    state match {
                      case _: ChannelState.Done.type =>
                        val sendInput = inputExecutor.getDone match {
                          case Exit.Failure(cause) => bridgeInput.error(cause)
                          case Exit.Success(value) => bridgeInput.done(value)
                        }

                        sendInput

                      case _: ChannelState.Emit.type =>
                        bridgeInput.emit(inputExecutor.getEmit) *> drainer

                      case ChannelState.Effect(zio) =>
                        zio.foldCauseZIO(
                          cause => bridgeInput.error(cause),
                          _ => drainer
                        )

                      case r: ChannelState.Read[Env, Any] =>
                        ChannelExecutor
                          .readUpstream(
                            r,
                            () => drainer,
                            (cause: Cause[Any]) => bridgeInput.error(cause)
                          )
                    }
                  }

                result = ChannelState.Effect(
                  drainer.forkDaemon.map { fiber =>
                    addFinalizer(exit => fiber.interrupt *> restorePipe(exit, inputExecutor))
                  }
                )
              }
          }
        } catch {
          case t: Throwable => currentChannel = ZChannel.failCause(Cause.die(t))
        }
      }
    }

    result
  }

  private[this] var currentChannel: Channel[Env] = erase(initialChannel())

  private[this] var done: Exit[Any, Any] = _

  private[this] var doneStack: Stack[ErasedContinuation[Env]] = Stack.empty[ErasedContinuation[Env]]

  private[this] var emitted: Any = _

  @volatile
  private[this] var inProgressFinalizer: URIO[Env, Any] = _

  @volatile
  var input: ErasedExecutor[Env] = _

  private[this] var activeSubexecutor: Subexecutor[Env] = _

  @volatile
  private[this] var closeLastSubstream: URIO[Env, Any] = _

  private[this] def doneSucceed(z: Any)(implicit trace: Trace): ChannelState[Env, Any] =
    if (doneStack.isEmpty) {
      done = Exit.succeed(z)
      currentChannel = null
      ChannelState.Done
    } else {
      doneStack.head match {
        case ZChannel.Fold.K(onSuccess, _) =>
          doneStack.pop()
          currentChannel = onSuccess(z)
          null

        case _: ZChannel.Fold.Finalizer[?, ?, ?] =>
          val finalizers = popNextFinalizers()

          if (doneStack.isEmpty) {
            doneStack = finalizers.asInstanceOf[Stack[ErasedContinuation[Env]]]
            done = Exit.succeed(z)
            currentChannel = null
            ChannelState.Done
          } else {
            val finalizerEffect = runFinalizersOrNull(finalizers, Exit.succeed(z))
            if (storeInProgressFinalizer(finalizerEffect))
              ChannelState.Effect(
                finalizerEffect
                  .ensuring(ZIO.succeed(clearInProgressFinalizer()))
                  .uninterruptible *> ZIO.succeed(doneSucceed(z))
              )
            else ChannelState.Effect(ZIO.succeed(doneSucceed(z)))
          }
      }
    }

  private[this] def doneHalt(cause: Cause[Any])(implicit trace: Trace): ChannelState[Env, Any] =
    if (doneStack.isEmpty) {
      done = Exit.failCause(cause)
      currentChannel = null
      ChannelState.Done
    } else {
      doneStack.head match {
        case ZChannel.Fold.K(_, onHalt) =>
          doneStack.pop()
          currentChannel = onHalt(cause)
          null

        case _: ZChannel.Fold.Finalizer[?, ?, ?] =>
          val finalizers = popNextFinalizers()

          if (doneStack.isEmpty) {
            doneStack = finalizers.asInstanceOf[Stack[ErasedContinuation[Env]]]
            done = Exit.failCause(cause)
            currentChannel = null
            ChannelState.Done
          } else {
            val finalizerEffect = runFinalizersOrNull(finalizers, Exit.failCause(cause))
            if (storeInProgressFinalizer(finalizerEffect))
              ChannelState.Effect(
                finalizerEffect
                  .ensuring(ZIO.succeed(clearInProgressFinalizer()))
                  .uninterruptible *> ZIO.succeed(doneHalt(cause))
              )
            else ChannelState.Effect(ZIO.succeed(doneHalt(cause)))
          }
      }
    }

  private def runBracketOut(
    bracketOut: ZChannel.BracketOut[Env, Any, Any]
  )(implicit trace: Trace): ChannelState.Effect[Env, Any] =
    ChannelState.Effect {
      ZIO.uninterruptible {
        provide(bracketOut.acquire()).foldCauseZIO(
          cause => ZIO.succeed { currentChannel = ZChannel.refailCause(cause) },
          out =>
            ZIO.succeed {
              addFinalizer(exit => provide(bracketOut.finalizer(out, exit)))
              currentChannel = ZChannel.write(out)
            }
        )
      }
    }

  private def provide[Env, OutErr, OutDone](
    zio: ZIO[Env, OutErr, OutDone]
  )(implicit trace: Trace): ZIO[Env, OutErr, OutDone] =
    if ((providedEnv eq null) || (zio eq ZIO.unit)) zio
    else zio.provideEnvironment(providedEnv.asInstanceOf[ZEnvironment[Env]])

  private[this] def runEnsuring(ensuring: ZChannel.Ensuring[Env, Any, Any, Any, Any, Any, Any]) = {
    addFinalizer(ensuring.finalizer)
    currentChannel = ensuring.channel
  }

  private[this] def addFinalizer(f: Finalizer[Env]): Unit =
    doneStack.push(ZChannel.Fold.Finalizer(f))

  private[this] def runFinalizersOrNull(
    finalizers: Iterable[ZChannel.Fold.Finalizer[Env, Any, Any]],
    ex: Exit[Any, Any]
  )(implicit trace: Trace): URIO[Env, Any] =
    if (finalizers.isEmpty) null
    else
      provide(ZIO.foreach(finalizers)(_.finalizer.apply(ex).exit))
        .map(results => Exit.collectAllDiscard(results))
        .unexit

  private[this] def runSubexecutor()(implicit trace: Trace): ChannelState[Env, Any] =
    activeSubexecutor match {
      case subexec: Subexecutor.PullFromUpstream[Env] =>
        pullFromUpstream(subexec)

      case subexec: Subexecutor.PullFromChild[Env] =>
        pullFromChild(
          subexec.childExecutor,
          subexec.parentSubexecutor
        )

      case Subexecutor.Emit(value, next) =>
        this.emitted = value
        this.activeSubexecutor = next
        ChannelState.Emit
    }

  private def replaceSubexecutor(nextSubExec: Subexecutor[Env]): Unit = {
    currentChannel = null
    activeSubexecutor = nextSubExec
  }

  private def finishSubexecutorWithCloseEffect(
    subexecDone: Exit[Any, Any],
    closeFns: (Exit[Any, Any]) => URIO[Env, Any]*
  )(implicit trace: Trace): ChannelState[Env, Any] = {
    addFinalizer { _ =>
      ZIO
        .foreach(closeFns) { closeFn =>
          {
            val closeEffect = closeFn(subexecDone)
            if (closeEffect eq null) Exit.unit
            else closeEffect
          }.exit
        }
        .flatMap(Exit.collectAllDiscard(_))
    }

    val state = subexecDone.foldExit(doneHalt, doneSucceed)
    activeSubexecutor = null
    state
  }

  private final def pullFromUpstream(
    self: Subexecutor.PullFromUpstream[Env]
  )(implicit trace: Trace): ChannelState[Env, Any] =
    ChannelState.Read(
      self.upstreamExecutor,
      onEffect = (effect: ZIO[Env, Nothing, Unit]) => {
        val closeLast = ZIO.uninterruptible {
          val close = this.closeLastSubstream
          closeLastSubstream = null
          if (close ne null) close else Exit.unit
        }
        executeCloseLastSubstream(closeLast) *> effect
      },
      onEmit = { (emitted: Any) =>
        if (this.closeLastSubstream ne null) {
          val closeLast = ZIO.uninterruptible {
            val close = this.closeLastSubstream
            closeLastSubstream = null
            if (close ne null) close else Exit.unit
          }

          executeCloseLastSubstream(closeLast).map { _ =>
            val childExecutor: ErasedExecutor[Env] =
              new ChannelExecutor(
                () => self.createChild(emitted),
                providedEnv,
                executeCloseLastSubstream
              )
            childExecutor.input = input

            activeSubexecutor = Subexecutor.PullFromChild[Env](childExecutor, self)
          }
        } else {
          val childExecutor: ErasedExecutor[Env] =
            new ChannelExecutor(
              () => self.createChild(emitted),
              providedEnv,
              executeCloseLastSubstream
            )
          childExecutor.input = input

          activeSubexecutor = Subexecutor.PullFromChild[Env](childExecutor, self)
          null
        }
      },
      onDone = { (exit: Exit[Any, Any]) =>
        val lastClose = this.closeLastSubstream
        finishSubexecutorWithCloseEffect(
          exit.mapExit(self.combineWithChildResult(self.lastDone, _)),
          _ => lastClose,
          self.upstreamExecutor.close
        ).effectOrNullIgnored // NOTE: assuming finalizers cannot fail
      }
    )

  private def pullFromChild(
    childExecutor: ErasedExecutor[Env],
    parentSubexecutor: Subexecutor.PullFromUpstream[Env]
  )(implicit trace: Trace): ChannelState[Env, Any] = {
    def handleSubexecFailure(cause: Cause[Any]): ChannelState[Env, Any] =
      finishSubexecutorWithCloseEffect(
        Exit.failCause(cause),
        parentSubexecutor.close,
        childExecutor.close
      )

    def finishWithDoneValue(doneValue: Any): Unit = {
      val modifiedParent =
        parentSubexecutor.copy(
          lastDone =
            if (parentSubexecutor.lastDone != null)
              parentSubexecutor.combineChildResults(parentSubexecutor.lastDone, doneValue)
            else doneValue
        )

      val thisClose = childExecutor.close(Exit.succeed(doneValue))
      if (thisClose ne null) {
        val lastClose = closeLastSubstream
        if (lastClose ne null) {
          closeLastSubstream = lastClose *> thisClose
        } else {
          closeLastSubstream = thisClose
        }
      }

      replaceSubexecutor(modifiedParent)
    }

    ChannelState.Read(
      childExecutor,
      onEffect = ZIO.identityFn[ZIO[Env, Nothing, Unit]],
      onEmit = { (emitted: Any) =>
        this.activeSubexecutor = Subexecutor.Emit(emitted, this.activeSubexecutor)
        null
      },
      onDone = { (exit: Exit[Any, Any]) =>
        exit match {
          case Exit.Failure(cause) =>
            handleSubexecFailure(cause).effectOrNullIgnored // NOTE: assuming finalizers cannot fail
          case Exit.Success(doneValue) =>
            finishWithDoneValue(doneValue)
            null
        }
      }
    )
  }
}

private[stream] object ChannelExecutor {
  type Channel[R]            = ZChannel[R, Any, Any, Any, Any, Any, Any]
  type ErasedExecutor[Env]   = ChannelExecutor[Env, Any, Any, Any, Any, Any, Any]
  type ErasedContinuation[R] = ZChannel.Fold.Continuation[R, Any, Any, Any, Any, Any, Any, Any, Any]
  type Finalizer[R]          = Exit[Any, Any] => URIO[R, Any]

  sealed trait ChannelState[-R, +E] { self =>
    def effectOrNullIgnored(implicit trace: Trace): ZIO[R, Nothing, Unit] =
      self match {
        case ChannelState.Effect(zio) => zio.ignore
        case _                        => null
      }
  }

  @inline
  private def isNullOrZIOUnit[R, E, A](zio: ZIO[R, E, A]): Boolean =
    (zio eq null) || (zio eq ZIO.unit)

  /**
   * The maximum number of steps that can be taken without yielding back to the
   * Fiber runloop. After this, we will yield back to the runloop to maintain
   * interruptability.
   */
  @inline
  private def MaxSteps = 128

  object ChannelState {
    case object Emit                                   extends ChannelState[Any, Nothing]
    case object Done                                   extends ChannelState[Any, Nothing]
    final case class Effect[R, E](zio: ZIO[R, E, Any]) extends ChannelState[R, E]
    object Effect {
      val unit: ChannelState[Any, Nothing] = Effect(ZIO.unit)
    }
    final case class Read[R, E](
      upstream: ErasedExecutor[R],
      onEffect: ZIO[R, Nothing, Unit] => ZIO[R, Nothing, Unit],
      onEmit: Any => ZIO[R, Nothing, Unit],
      onDone: Exit[Any, Any] => ZIO[R, Nothing, Unit]
    ) extends ChannelState[R, E]
  }

  private[ChannelExecutor] sealed abstract class Subexecutor[-R] {
    def close(ex: Exit[Any, Any])(implicit trace: Trace): URIO[R, Any]
  }
  private[ChannelExecutor] object Subexecutor {

    /**
     * Execute upstreamExecutor and for each emitted element, spawn a child
     * channel and continue with processing it by PullFromChild
     */
    final case class PullFromUpstream[R](
      upstreamExecutor: ErasedExecutor[R],
      createChild: Any => Channel[R],
      lastDone: Any,
      combineChildResults: (Any, Any) => Any,
      combineWithChildResult: (Any, Any) => Any
    ) extends Subexecutor[R] { self =>
      def close(ex: Exit[Any, Any])(implicit trace: Trace): URIO[R, Any] =
        upstreamExecutor.close(ex)
    }

    /**
     * Execute the childExecutor and on each emitted value, decide what to do by
     * onEmit
     */
    final case class PullFromChild[R](
      childExecutor: ErasedExecutor[R],
      parentSubexecutor: PullFromUpstream[R]
    ) extends Subexecutor[R] {
      def close(ex: Exit[Any, Any])(implicit trace: Trace): URIO[R, Any] = {
        val fin1 = childExecutor.close(ex)
        val fin2 = parentSubexecutor.close(ex)

        if (fin1 eq null) fin2
        else if (fin2 eq null) fin1
        else fin1.exit.zipWith(fin2.exit)(_ *> _).unexit
      }
    }

    final case class Emit[R](value: Any, next: Subexecutor[R]) extends Subexecutor[R] {
      def close(ex: Exit[Any, Any])(implicit trace: Trace): URIO[R, Any] =
        next.close(ex)
    }
  }

  private def erase[R](channel: ZChannel[R, _, _, _, _, _, _]): Channel[R] =
    channel.asInstanceOf[Channel[R]]

  private[stream] def readUpstream[R, E, E2, A](
    r: ChannelExecutor.ChannelState.Read[R, E],
    onSuccess: () => ZIO[R, E2, A],
    onFailure: Cause[E] => ZIO[R, E2, A]
  )(implicit trace: Trace): ZIO[R, E2, A] = {
    val readStack = Stack.empty[ChannelState.Read[Any, Any]]

    @tailrec def read(opsTillYield: Int, current: ChannelState.Read[Any, Any]): ZIO[R, E2, A] =
      if (current.upstream eq null) ZIO.dieMessage("Unexpected end of input for channel execution")
      else if (0 == opsTillYield) readAux(current)
      else {
        current.upstream.run() match {
          case _: ChannelState.Emit.type =>
            val emitEffect = current.onEmit(current.upstream.getEmit)
            if (readStack.isEmpty) {
              if (emitEffect eq null) ZIO.suspendSucceed(onSuccess())
              else
                emitEffect.foldCauseZIO(onFailure, _ => onSuccess())
            } else {
              val next = readStack.pop()
              if (emitEffect eq null) read(opsTillYield - 1, next)
              else (emitEffect.foldCauseZIO(onFailure, _ => readAux(next)))
            }
          case _: ChannelState.Done.type =>
            val doneEffect = current.onDone(current.upstream.getDone)
            if (readStack.isEmpty) {
              if (doneEffect eq null) ZIO.suspendSucceed(onSuccess())
              else
                doneEffect.foldCauseZIO(onFailure, _ => onSuccess())
            } else {
              val next = readStack.pop()
              if (doneEffect eq null) read(opsTillYield - 1, next)
              else (doneEffect.foldCauseZIO(onFailure, _ => readAux(next)))
            }
          case ChannelState.Effect(zio) =>
            current
              .onEffect(zio.asInstanceOf[ZIO[Any, Nothing, Unit]])
              .catchAllCause { cause =>
                val doneEffect = current.onDone(Exit.failCause(cause))
                if (doneEffect eq null) Exit.unit
                else doneEffect
              }
              .foldCauseZIO(onFailure, _ => readAux(current))
          case r2: ChannelState.Read[Any, Any] =>
            readStack.push(current)
            read(opsTillYield - 1, r2)
        }
      }

    def readAux(current: ChannelState.Read[Any, Any]): ZIO[R, E2, A] =
      ZIO.suspendSucceed(read(MaxSteps, current))

    readAux(r.asInstanceOf[ChannelState.Read[Any, Any]])
  }

  private[ChannelExecutor] def execToPullingChannel[Env](
    exec: ErasedExecutor[Env]
  )(implicit trace: Trace): ZChannel[Env, Any, Any, Any, Any, Any, Any] = {
    val MAX_STEPS = 128
    def ch2(st: ChannelState[Env, Any]): ZChannel[Env, Any, Any, Any, Any, Any, Any] =
      st match {
        case _: ChannelState.Done.type =>
          exec.getDone match {
            case Exit.Success(res) =>
              ZChannel.succeedNow(res)
            case Exit.Failure(c) =>
              ZChannel.refailCause(c)
          }
        case _: ChannelState.Emit.type =>
          ZChannel.write(exec.getEmit) *> ch2(exec.run())
        case ChannelState.Effect(zio) =>
          if (zio eq ZIO.unit) ZChannel.suspend(ch2(exec.run()))
          else ZChannel.fromZIO(zio) *> ch2(exec.run())
        case r: ChannelState.Read[Env, Any] =>
          readChAux(r, Stack.empty[ChannelState.Read[Env, Any]]) *> ch2(exec.run())
      }

    def readChAux(
      current: ChannelState.Read[Env, Any],
      readStack: Stack[ChannelState.Read[Env, Any]]
    ): ZChannel[Env, Any, Any, Any, Any, Any, Any] =
      ZChannel.unwrap(ZIO.unit as readChAux0(current, readStack))

    def readChAux0(
      current: ChannelState.Read[Env, Any],
      readStack: Stack[ChannelState.Read[Env, Any]]
    ): ZChannel[Env, Any, Any, Any, Any, Any, Any] =
      readCh(current, readStack, MaxSteps)

    @tailrec def readCh(
      current: ChannelState.Read[Env, Any],
      readStack: Stack[ChannelState.Read[Env, Any]],
      opsTillYield: Int
    ): ZChannel[Env, Any, Any, Any, Any, Any, Any] =
      if (0 == opsTillYield) readChAux(current, readStack)
      else {
        current.upstream.run() match {
          case _: ChannelState.Emit.type =>
            val emitEffect = current.onEmit(current.upstream.getEmit)
            if (readStack.isEmpty) {
              if (emitEffect eq null) ZChannel.unit
              else ZChannel.fromZIO(emitEffect)
            } else {
              val next = readStack.pop()
              if (emitEffect eq null) readCh(next, readStack, opsTillYield - 1)
              else ZChannel.fromZIO(emitEffect) *> readChAux0(next, readStack)
            }
          case _: ChannelState.Done.type =>
            val doneEffect = current.onDone(current.upstream.getDone)
            if (readStack.isEmpty) {
              if (doneEffect eq null) ZChannel.unit
              else ZChannel.fromZIO(doneEffect)
            } else {
              val next = readStack.pop()
              if (doneEffect eq null) readCh(next, readStack, opsTillYield - 1)
              else ZChannel.fromZIO(doneEffect) *> readChAux0(next, readStack)
            }
          case ChannelState.Effect(zio) =>
            ZChannel.fromZIO(
              current
                .onEffect(zio.asInstanceOf[ZIO[Any, Nothing, Unit]])
                .catchAllCause { cause =>
                  val doneEffect = current.onDone(Exit.failCause(cause))
                  if (doneEffect eq null) Exit.unit
                  else doneEffect
                }
            ) *> readChAux0(current, readStack)
          case r2: ChannelState.Read[Env, Any] =>
            readStack.push(current)
            readCh(r2, readStack, opsTillYield - 1)
        }
      }

    ZChannel.suspend(ch2(exec.run()))
  }

}

/**
 * Consumer-side view of [[SingleProducerAsyncInput]] for variance purposes.
 */
private[stream] sealed trait AsyncInputConsumer[+Err, +Elem, +Done] {
  def takeWith[A](
    onError: Cause[Err] => A,
    onElement: Elem => A,
    onDone: Done => A
  )(implicit trace: Trace): UIO[A]
}

/**
 * Producer-side view of [[SingleProducerAsyncInput]] for variance purposes.
 */
private[stream] sealed trait AsyncInputProducer[-Err, -Elem, -Done] {
  def emit(el: Elem)(implicit trace: Trace): UIO[Any]
  def done(a: Done)(implicit trace: Trace): UIO[Any]
  def error(cause: Cause[Err])(implicit trace: Trace): UIO[Any]
  def awaitRead(implicit trace: Trace): UIO[Any]
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
private[stream] final class SingleProducerAsyncInput[Err, Elem, Done](
  ref: Ref[SingleProducerAsyncInput.State[Err, Elem, Done]]
) extends AsyncInputConsumer[Err, Elem, Done]
    with AsyncInputProducer[Err, Elem, Done] {
  import SingleProducerAsyncInput.State

  def emit(el: Elem)(implicit trace: Trace): UIO[Any] =
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

  def done(a: Done)(implicit trace: Trace): UIO[Any] =
    ref.modify {
      case State.Emit(notifyConsumers)     => (ZIO.foreachDiscard(notifyConsumers)(_.succeed(Left(a))), State.Done(a))
      case s @ State.Error(_)              => (ZIO.interrupt, s)
      case s @ State.Done(_)               => (ZIO.interrupt, s)
      case s @ State.Empty(notifyProducer) => (notifyProducer.await, s)
    }.flatten

  def error(cause: Cause[Err])(implicit trace: Trace): UIO[Any] =
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
  )(implicit trace: Trace): UIO[A] =
    Promise.make[Err, Either[Done, Elem]].flatMap { p =>
      ref.modify {
        case State.Emit(notifyConsumers) =>
          (p.await.foldCause(onError, _.fold(onDone, onElement)), State.Emit(notifyConsumers.enqueue(p)))
        case s @ State.Error(a) => (ZIO.succeed(onError(a)), s)
        case s @ State.Done(a)  => (ZIO.succeed(onDone(a)), s)
        case s @ State.Empty(notifyProducer) =>
          (notifyProducer.succeed(()) *> p.await.foldCause(onError, _.fold(onDone, onElement)), State.Emit(Queue(p)))
      }.flatten
    }

  def take[A](implicit trace: Trace): UIO[Exit[Either[Err, Done], Elem]] =
    takeWith(c => Exit.failCause(c.map(Left(_))), Exit.succeed(_), d => Exit.fail(Right(d)))

  def close(implicit trace: Trace): UIO[Any] =
    ZIO.fiberIdWith(id => error(Cause.interrupt(id)))

  def awaitRead(implicit trace: Trace): UIO[Any] =
    ref.modify {
      case s @ State.Empty(notifyProducer) => (notifyProducer.await, s)
      case s                               => (ZIO.unit, s)
    }.flatten
}

private[stream] object SingleProducerAsyncInput {
  def make[Err, Elem, Done](implicit trace: Trace): UIO[SingleProducerAsyncInput[Err, Elem, Done]] =
    ZIO.fiberIdWith(fiberId => Exit.succeed(unsafe.make(fiberId)(Unsafe)))

  object unsafe {
    def make[Err, Elem, Done](fiberId: FiberId)(implicit unsafe: Unsafe): SingleProducerAsyncInput[Err, Elem, Done] = {
      val promise: Promise[Nothing, Unit]      = Promise.unsafe.make[Nothing, Unit](fiberId)
      val initialState: State[Err, Elem, Done] = State.Empty[Err, Elem, Done](promise)
      val ref: Ref[State[Err, Elem, Done]]     = Ref.unsafe.make(initialState)
      new SingleProducerAsyncInput[Err, Elem, Done](ref)
    }
  }

  private[SingleProducerAsyncInput] sealed trait State[Err, Elem, Done]
  private[SingleProducerAsyncInput] object State {
    case class Empty[Err, Elem, Done](notifyProducer: Promise[Nothing, Unit]) extends State[Err, Elem, Done]
    case class Emit[Err, Elem, Done](notifyConsumers: Queue[Promise[Err, Either[Done, Elem]]])
        extends State[Err, Elem, Done]
    case class Error[Err, Elem, Done](cause: Cause[Err]) extends State[Err, Elem, Done]
    case class Done[Err, Elem, Done](done: Done)         extends State[Err, Elem, Done]
  }
}
