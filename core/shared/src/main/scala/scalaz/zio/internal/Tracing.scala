package scalaz.zio.internal

import scalaz.zio.internal.stacktracer.Tracer
import scalaz.zio.internal.tracing.TracingConfig

final case class Tracing(tracer: Tracer, tracingConfig: TracingConfig)
