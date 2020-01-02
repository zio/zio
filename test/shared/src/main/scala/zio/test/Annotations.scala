/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio.{ FiberRef, UIO, ZIO }

/**
 * The `Annotations` trait provides access to an annotation map that tests
 * can add arbitrary annotations to. Each annotation consists of a string
 * identifier, an initial value, and a function for combining two values.
 * Annotations form monoids and you can think of `Annotations` as a more
 * structured logging service or as a super polymorphic version of the writer
 * monad effect.
 */
trait Annotations {
  val annotations: Annotations.Service[Any]
}

object Annotations {

  trait Service[R] {
    def annotate[V](key: TestAnnotation[V], value: V): ZIO[R, Nothing, Unit]
    def get[V](key: TestAnnotation[V]): ZIO[R, Nothing, V]
    def withAnnotation[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Annotated[E], Annotated[A]]
  }

  /**
   * Accesses an `Annotations` instance in the environment and appends the
   * specified annotation to the annotation map.
   */
  def annotate[V](key: TestAnnotation[V], value: V): ZIO[Annotations, Nothing, Unit] =
    ZIO.accessM(_.annotations.annotate(key, value))

  /**
   * Accesses an `Annotations` instance in the environment and retrieves the
   * annotation of the specified type, or its default value if there is none.
   */
  def get[V](key: TestAnnotation[V]): ZIO[Annotations, Nothing, V] =
    ZIO.accessM(_.annotations.get(key))

  /**
   * Constructs a new `Annotations` instance.
   */
  def make: UIO[Annotations] =
    makeService.map { service =>
      new Annotations {
        val annotations = service
      }
    }

  /**
   * Constructs a new `Annotations` service.
   */
  def makeService: UIO[Annotations.Service[Any]] =
    FiberRef.make(TestAnnotationMap.empty).map { fiberRef =>
      new Annotations.Service[Any] {
        def annotate[V](key: TestAnnotation[V], value: V): UIO[Unit] =
          fiberRef.update(_.annotate(key, value)).unit
        def get[V](key: TestAnnotation[V]): UIO[V] =
          fiberRef.get.map(_.get(key))
        def withAnnotation[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Annotated[E], Annotated[A]] =
          fiberRef.locally(TestAnnotationMap.empty) {
            zio.foldM(e => fiberRef.get.map((e, _)).flip, a => fiberRef.get.map((a, _)))
          }
      }
    }

  /**
   * Accesses an `Annotations` instance in the environment and executes the
   * specified effect with an empty annotation map, returning the annotation
   * map along with the result of execution.
   */
  def withAnnotation[R <: Annotations, E, A](zio: ZIO[R, E, A]): ZIO[R, Annotated[E], Annotated[A]] =
    ZIO.accessM(_.annotations.withAnnotation(zio))
}
