package zio.test

import zio.UIO
import zio.duration.Duration
import zio.test.reflect.Reflect.EnableReflectiveInstantiation

@EnableReflectiveInstantiation
trait TestRenderer[-E] {
  def render(
    executedSpec: ExecutedSpec[E],
    annotationRenderer: TestAnnotationRenderer
  ): UIO[Seq[RenderedResult[String]]]
  def renderStats(duration: Duration, executedSpec: ExecutedSpec[E]): UIO[String]
}
