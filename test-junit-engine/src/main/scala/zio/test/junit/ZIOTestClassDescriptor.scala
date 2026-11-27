/*
 * Copyright 2024-2024 Vincent Raman and the ZIO Contributors
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

package zio.test.junit

import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.{TestDescriptor, UniqueId}
import zio._
import zio.test._
import zio.test.junit.ReflectionUtils._

/**
 * The JUnit Platform descriptor of one ZIO Test spec class: a container whose
 * display name is the class name without a trailing `\$`.
 *
 * Creating it loads the spec and builds the child descriptors: a
 * [[ZIOSuiteTestDescriptor]] for each suite and a [[ZIOTestDescriptor]] for
 * each test. Building them runs the effects that build suites, with the live
 * environment, the test environment and the `bootstrap` layer of the spec. The
 * constructor throws when the `bootstrap` layer fails, when an effect that
 * builds a suite dies, or when one that isn't inside another suite fails; a
 * suite whose effect fails inside another suite is left out, with its tests.
 *
 * The spec is the Scala object of `testClass` when there is one (`testClass` is
 * the class of an object, or has a companion object), and a new instance
 * created with the no-argument constructor otherwise. When the companion object
 * of `testClass` isn't a spec, the constructor throws a
 * `java.lang.ClassCastException`.
 *
 * @param parent
 *   the parent descriptor
 * @param uniqueId
 *   the unique id of this descriptor
 * @param testClass
 *   the spec class, or the class whose companion object is the spec
 */
class ZIOTestClassDescriptor(parent: TestDescriptor, uniqueId: UniqueId, val testClass: Class[_])
    extends AbstractTestDescriptor(uniqueId, testClass.getName.stripSuffix("$"), ZIOTestSource(testClass)) {

  setParent(parent)

  /**
   * The JVM name of `testClass`, which ends with `\$` for the class of an
   * object.
   */
  val className: String = testClass.getName

  // reflection to get the spec implementation
  // by default, it would be an object, extending ZIOSpecDefault
  // but this also support class that needs to be instantiated
  /** The spec that this descriptor describes. */
  val spec: ZIOSpecAbstract = getCompanionObject(testClass)
    .getOrElse(testClass.getDeclaredConstructor().newInstance())
    .asInstanceOf[ZIOSpecAbstract]

  /**
   * Returns an effect that adds the descriptors of `spec` under `description`,
   * nested like its suites: a [[ZIOSuiteTestDescriptor]] for each suite and a
   * [[ZIOTestDescriptor]] for each test, running the effects that build suites.
   * `path` is the list of labels of the suites that enclose `spec`, outermost
   * first.
   */
  def traverse[R, E](
    spec: Spec[R, E],
    description: TestDescriptor,
    path: Vector[String] = Vector.empty
  ): ZIO[R with Scope, Any, Unit] =
    spec.caseValue match {
      case Spec.ExecCase(_, spec: Spec[R, E]) => traverse(spec, description, path)
      case Spec.LabeledCase(label, spec: Spec[R, E]) =>
        traverse(spec, description, path :+ label)
      case Spec.ScopedCase(scoped) => scoped.flatMap((s: Spec[R, E]) => traverse(s, description, path))
      case Spec.MultipleCase(specs) =>
        val suiteDesc = new ZIOSuiteTestDescriptor(
          description,
          description.getUniqueId.append(ZIOSuiteTestDescriptor.segmentType, path.lastOption.getOrElse("")),
          path.lastOption.getOrElse(""),
          testClass
        )
        ZIO.succeed(description.addChild(suiteDesc)) *>
          ZIO.foreach(specs)((s: Spec[R, E]) => traverse(s, suiteDesc, path)).ignore
      case Spec.TestCase(_, annotations) =>
        ZIO.succeed(
          description.addChild(
            new ZIOTestDescriptor(
              description,
              description.getUniqueId.append(ZIOTestDescriptor.segmentType, path.lastOption.getOrElse("")),
              path.lastOption.getOrElse(""),
              testClass,
              annotations
            )
          )
        )
    }

  /**
   * The effect that builds the child descriptors of this descriptor from the
   * spec.
   */
  lazy val scoped: ZIO[spec.Environment with TestEnvironment, Any, Unit] =
    ZIO.scoped[spec.Environment with TestEnvironment](
      traverse(spec.spec, this)
    )

  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(
        scoped
          .provide(
            Scope.default >>> (liveEnvironment >>> TestEnvironment.live <*> ZLayer.environment[Scope]),
            spec.bootstrap
          )
      )
      .getOrThrowFiberFailure()
  }

  /** Returns `CONTAINER`. */
  override def getType: TestDescriptor.Type = TestDescriptor.Type.CONTAINER
}

/** Constants of [[ZIOTestClassDescriptor]]. */
object ZIOTestClassDescriptor {

  /** The unique id segment type of spec class descriptors: `class`. */
  val segmentType = "class"
}
