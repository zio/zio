/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.{ Ref, UIO, ZIO }

/**
 * The `TestAnnotations` trait provides access to an annotation map that tests
 * can add arbitrary annotations to. Each annotation consists of a string
 * identifier, an initial value, and a function for combining two values.
 * Annotations form monoids and you can think of `TestAnnotations` as a more
 * structured logging service or as a super polymorphic version of the writer
 * monad effect.
 */
trait TestAnnotations {
  val testAnnotations: TestAnnotations.Service[Any]
}

object TestAnnotations {

  trait Service[R] {
    def annotate[V](key: TestAnnotation[V], value: V): ZIO[R, Nothing, Unit]
    def get[V](key: TestAnnotation[V]): ZIO[R, Nothing, V]
    val testAnnotationMap: ZIO[R, Nothing, TestAnnotationMap]
  }

  /**
   * Accesses a `TestAnnotations` instance in the environment and appends the
   * specified annotation to the annotation map.
   */
  def annotate[V](key: TestAnnotation[V], value: V): ZIO[TestAnnotations, Nothing, Unit] =
    ZIO.accessM(_.testAnnotations.annotate(key, value))

  /**
   * Accesses a `TestAnnotations` instance in the environment and retrieves the
   * annotation of the specified type, or its default value if there is none.
   */
  def get[V](key: TestAnnotation[V]): ZIO[TestAnnotations, Nothing, V] =
    ZIO.accessM(_.testAnnotations.get(key))

  /**
   * Constructs a new `TestAnnotations` instance.
   */
  def make: UIO[TestAnnotations] =
    makeService.map { service =>
      new TestAnnotations {
        val testAnnotations = service
      }
    }

  /**
   * Constructs a new `TestAnnotations` service.
   */
  def makeService: UIO[TestAnnotations.Service[Any]] =
    Ref.make(TestAnnotationMap.empty).map { map =>
      new TestAnnotations.Service[Any] {
        def annotate[V](key: TestAnnotation[V], value: V): UIO[Unit] =
          map.update(_.annotate(key, value)).unit
        def get[V](key: TestAnnotation[V]): UIO[V] =
          map.get.map(_.get(key))
        val testAnnotationMap: UIO[TestAnnotationMap] =
          map.get
      }
    }

  /**
   * Accesses a `TestAnnotations` instance in the environment and returns
   * the test annotation map.
   */
  val testAnnotationMap: ZIO[TestAnnotations, Nothing, TestAnnotationMap] =
    ZIO.accessM(_.testAnnotations.testAnnotationMap)
}
