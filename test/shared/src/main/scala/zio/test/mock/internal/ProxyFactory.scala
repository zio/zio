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

package zio.test.mock.internal

import scala.util.Try

import zio.test.Assertion
import zio.test.mock.{ Expectation, Method, Proxy }
import zio.{ Has, IO, Promise, Tagged, UIO, ULayer, ZIO, ZLayer }

object ProxyFactory {

  import Expectation._
  import InvalidCall._
  import MockException._

  /**
   * Given initial `State[R]`, constructs a `Proxy` running that state.
   */
  def mockProxy[R <: Has[_]: Tagged](state: State[R]): ULayer[Has[Proxy]] =
    ZLayer.succeed(new Proxy {
      def invoke[RIn <: Has[_], ROut, I, E, A](invokedMethod: Method[RIn, I, E, A], args: I): ZIO[ROut, E, A] = {

        def findMatching(scopes: List[Scope[R]]): UIO[Matched[R, E, A]] =
          scopes match {
            case Nil => ZIO.die(UnexpectedCallExpection(invokedMethod, args))
            case Scope(expectation, id, update) :: nextScopes =>
              expectation match {
                case anyExpectation if anyExpectation.saturated =>
                  findMatching(nextScopes)

                case call @ Call(method, assertion, returns, _, _, invocations) if invokedMethod isEqual method =>
                  assertion.asInstanceOf[Assertion[I]].test(args).flatMap {
                    case true =>
                      val result = returns.asInstanceOf[I => IO[E, A]](args)
                      val updated = call
                        .asInstanceOf[Call[R, I, E, A]]
                        .copy(
                          satisfied = true,
                          saturated = true,
                          invocations = id :: invocations
                        )

                      UIO.succeed(Matched[R, E, A](update(updated), result))

                    case false =>
                      handleLeafFailure(
                        InvalidArguments(invokedMethod, args, assertion.asInstanceOf[Assertion[Any]]),
                        nextScopes
                      )
                  }

                case Call(method, assertion, _, _, _, _) =>
                  val invalidCall =
                    if (invokedMethod.id == method.id) InvalidPolyType(invokedMethod, args, method, assertion)
                    else InvalidMethod(invokedMethod, method, assertion)

                  handleLeafFailure(invalidCall, nextScopes)

                case self @ Chain(children, _, _, invocations) =>
                  children.zipWithIndex.collectFirst {
                    case (child, index) if !child.saturated =>
                      Scope[R](
                        child,
                        id,
                        updatedChild => {
                          val updatedChildren = children.updated(index, updatedChild)

                          update(
                            self.copy(
                              children = updatedChildren,
                              satisfied = updatedChildren.forall(_.satisfied),
                              saturated = updatedChildren.forall(_.saturated),
                              invocations = id :: invocations
                            )
                          )
                        }
                      )
                  } match {
                    case None =>
                      ZIO.dieMessage(
                        "Illegal state. Unsaturated `Chain` node implies at least one unsaturated child expectation."
                      )
                    case Some(scope) => findMatching(scope :: nextScopes)
                  }

                case self @ And(children, _, _, invocations) =>
                  children.zipWithIndex.collect {
                    case (child, index) if !child.saturated =>
                      Scope[R](
                        child,
                        id,
                        updatedChild => {
                          val updatedChildren = children.updated(index, updatedChild)

                          update(
                            self.copy(
                              children = updatedChildren,
                              satisfied = updatedChildren.forall(_.satisfied),
                              saturated = updatedChildren.forall(_.saturated),
                              invocations = id :: invocations
                            )
                          )
                        }
                      )
                  } match {
                    case Nil =>
                      ZIO.dieMessage(
                        "Illegal state. Unsaturated `And` node implies at least one unsaturated child expectation."
                      )
                    case scopes => findMatching(scopes ++ nextScopes)
                  }

                case self @ Or(children, _, _, invocations) =>
                  children.zipWithIndex.collect {
                    case (child, index) =>
                      Scope[R](
                        child,
                        id,
                        updatedChild => {
                          val updatedChildren = children.updated(index, updatedChild)

                          update(
                            self.copy(
                              children = updatedChildren,
                              satisfied = updatedChildren.exists(_.satisfied),
                              saturated = updatedChildren.exists(_.saturated),
                              invocations = id :: invocations
                            )
                          )
                        }
                      )
                  } match {
                    case Nil =>
                      ZIO.dieMessage(
                        "Illegal state. Unsaturated `Or` node implies at least one unsaturated child expectation."
                      )
                    case scopes => findMatching(scopes ++ nextScopes)
                  }

                case self @ Repeated(expectation, range, _, _, invocations, started, completed) =>
                  val initialize = expectation.saturated && completed < range.max
                  val child      = if (initialize) resetTree(expectation) else expectation
                  val scope = Scope[R](
                    child,
                    id,
                    updatedChild => {

                      val updatedStarted =
                        if (started == completed) started + 1
                        else started

                      val updatedCompleted =
                        if (updatedChild.saturated) completed + 1
                        else completed

                      val subtree =
                        if (updatedChild.saturated) resetTree(updatedChild)
                        else updatedChild

                      def inRange(value: Int): Boolean =
                        if (range.end != -1) range contains value
                        else {
                          val fakeUnboundedRange = range.start to Int.MaxValue by range.step
                          fakeUnboundedRange contains value
                        }

                      update(
                        self.copy(
                          child = subtree,
                          satisfied = inRange(updatedStarted) && updatedChild.satisfied,
                          saturated = Try(range.max == updatedCompleted).getOrElse(false),
                          invocations = id :: invocations,
                          started = updatedStarted,
                          completed = updatedCompleted
                        )
                      )
                    }
                  )

                  findMatching(scope :: nextScopes)
              }
          }

        def handleLeafFailure(failure: => InvalidCall, nextScopes: List[Scope[R]]): UIO[Matched[R, E, A]] =
          state.failedMatchesRef
            .updateAndGet(failure :: _)
            .flatMap { failures =>
              if (nextScopes.isEmpty) ZIO.die(InvalidCallException(failures))
              else findMatching(nextScopes)
            }

        def resetTree(expectation: Expectation[R]): Expectation[R] =
          expectation match {
            case self: Call[R, _, _, _] =>
              self.copy(
                satisfied = false,
                saturated = false
              )
            case self: Chain[R] =>
              self.copy(
                children = self.children.map(resetTree),
                satisfied = false,
                saturated = false
              )
            case self: And[R] =>
              self.copy(
                children = self.children.map(resetTree),
                satisfied = false,
                saturated = false
              )
            case self: Or[R] =>
              self.copy(
                children = self.children.map(resetTree),
                satisfied = false,
                saturated = false
              )
            case self: Repeated[R] =>
              self.copy(
                child = resetTree(self.child),
                satisfied = false,
                saturated = false,
                completed = 0
              )
          }

        for {
          promise <- Promise.make[E, A]
          id      <- state.callsCountRef.updateAndGet(_ + 1)
          _       <- state.failedMatchesRef.set(List.empty)
          _ <- state.expectationRef.update { root =>
                val rootScope = Scope[R](root, id, identity)
                findMatching(rootScope :: Nil).flatMap {
                  case Matched(expectation, result) =>
                    promise.complete(result) as (expectation)
                }
              }
          output <- promise.await
        } yield output
      }
    })
}
