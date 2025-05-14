package zio

private[zio] object OptimizedTimeout {
  def timeoutTo[R, E, A, B1](self: ZIO[R, E, A], b: () => B1, f: A => B1, duration: Duration)(
    implicit trace: Trace
  ): ZIO[R, E, B1] = {
    ZIO.uninterruptibleMask { restore =>
      ZIO.withFiberRuntime[R, E, B1] { (state, _) =>
        val clock = state.getFiberRef(DefaultServices.currentServices).get[Clock]
        clock.scheduler.flatMap { scheduler =>
          val cancelTimeout = scheduler.schedule(
            { () =>
              state.tellInterrupt(Cause.interrupt(state.id))
            },
            duration
          )(Unsafe)

          restore(self).exitWith { exit =>
            cancelTimeout()
            exit match {
              case Exit.Success(a) => Exit.succeed(f(a))
              case Exit.Failure(_) => Exit.succeed(b())
            }
          }
        }
      }
    }
  }
}