package zio.test

/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import zio.UIO
import zio.duration._
import zio.test.ConsoleUtils._

object TestAnnotationRenderer {

  val Timed: TestAnnotationRenderer[Any, String, Any] = { executedSpec =>
    val results = executedSpec.fold[UIO[Vector[(String, Duration)]]] {
      case Spec.SuiteCase(_, executedSpecs, _) =>
        executedSpecs.flatMap(UIO.collectAll(_).map(_.foldLeft(Vector.empty[(String, Duration)])(_ ++ _)))
      case Spec.TestCase(label, test) =>
        test.map {
          case (_, annotationMap) =>
            val d = annotationMap.get(TestAnnotation.Timing)
            if (d > Duration.Zero)
              Vector(label -> annotationMap.get(TestAnnotation.Timing))
            else
              Vector.empty
        }
    }
    results.map { times =>
      val count   = times.length
      val sum     = times.map(_._2).fold(Duration.Zero)(_ + _)
      val summary = s"Timed $count tests in ${sum.render}:\n"
      val details = times
        .sortBy(_._2)
        .reverse
        .map {
          case (label, duration) =>
            f"  ${green("+")} $label: ${duration.render} (${(duration.toNanos.toDouble / sum.toNanos) * 100}%2.2f%%)"
        }
        .mkString("\n")
      if (count > 0) Some(summary ++ details) else None
    }
  }

  val Default =
    List(Timed)
}
