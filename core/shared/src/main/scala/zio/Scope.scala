/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

package zio

trait Scope {
  def addFinalizer(finalizer: Exit[Any, Any] => UIO[Any]): UIO[Unit]
  def close(exit: Exit[Any, Any]): UIO[Unit]
  def use[R, E, A](zio: ZIO[Scope with R, E, A]): ZIO[R, E, A]
}

object Scope {

  def addFinalizer(finalizer: Exit[Any, Any] => UIO[Any]): ZIO[Scope, Nothing, Unit] =
    ZIO.serviceWithZIO(_.addFinalizer(finalizer))

  def make: UIO[Scope] =
    makeWith(ExecutionStrategy.Sequential)

  def parallel: UIO[Scope] =
    makeWith(ExecutionStrategy.Parallel)

  def makeWith(executionStrategy: ExecutionStrategy): UIO[Scope] =
    ZManaged.ReleaseMap.make.map { releaseMap =>
      new Scope { self =>
        def addFinalizer(finalizer: Exit[Any, Any] => UIO[Any]): UIO[Unit] =
          releaseMap.add(finalizer).unit

        def close(exit: Exit[Any, Any]): UIO[Unit] =
          releaseMap.releaseAll(exit, executionStrategy).unit

        def use[R, E, A](zio: ZIO[Scope with R, E, A]): ZIO[R, E, A] =
          zio.provideSomeEnvironment[R](_ ++ [Scope] ZEnvironment(self)).onExit(self.close(_))
      }
    }

        // def use[R, E, A](zio: ZIO[Scope with R, E, A])(implicit tag: EnvironmentTag[R]): ZIO[R, E, A] =
}
