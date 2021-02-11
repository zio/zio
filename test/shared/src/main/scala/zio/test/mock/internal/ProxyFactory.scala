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

import zio.test.Assertion
import zio.test.mock.{Capability, Expectation, Proxy}
import zio.{Has, IO, Tag, ULayer, ZIO, ZLayer}

import scala.util.Try

object ProxyFactory {

  import Debug._
  import Expectation._
  import ExpectationState._
  import InvalidCall._
  import MockException._

  /**
   * Given initial `MockState[R]`, constructs a `Proxy` running that state.
   */
  def mockProxy[R <: Has[_]: Tag](state: MockState[R]): ULayer[Has[Proxy]] =
    ZLayer.succeed(new Proxy {
      def invoke[RIn <: Has[_], ROut, I, E, A](invoked: Capability[RIn, I, E, A], args: I): ZIO[ROut, E, A] = {
        sealed trait MatchResult
        object MatchResult {
          case object UnexpectedCall                      extends MatchResult
          case class Success(value: Matched[R, E, A])     extends MatchResult
          case class Failure(failures: List[InvalidCall]) extends MatchResult
        }

        def findMatching(scopes: List[Scope[R]], failedMatches: List[InvalidCall]): MatchResult = {
          debug(s"::: invoked $invoked\n${prettify(scopes)}")
          scopes match {
            case Nil => MatchResult.UnexpectedCall
            case Scope(expectation, id, update0) :: nextScopes =>
              val update: Expectation[R] => Expectation[R] = updated => {
                debug(s"::: updated state to: ${updated.state}")
                update0(updated)
              }

              expectation match {
                case anyExpectation if anyExpectation.state == Saturated =>
                  debug("::: skipping saturated expectation")
                  findMatching(nextScopes, failedMatches)

                case NoCalls(_) =>
                  findMatching(nextScopes, failedMatches)
                case call @ Call(capability, assertion, returns, _, invocations) if invoked isEqual capability =>
                  debug(s"::: matched call $capability")
                  assertion.asInstanceOf[Assertion[I]].test(args) match {
                    case true =>
                      val result = returns.asInstanceOf[I => IO[E, A]](args)
                      val updated = call
                        .asInstanceOf[Call[R, I, E, A]]
                        .copy(
                          state = Saturated,
                          invocations = id :: invocations
                        )

                      MatchResult.Success(Matched[R, E, A](update(updated), result))

                    case false =>
                      handleLeafFailure(
                        InvalidArguments(invoked, args, assertion.asInstanceOf[Assertion[Any]]),
                        nextScopes,
                        failedMatches
                      )
                  }

                case Call(capability, assertion, _, _, _) =>
                  debug(s"::: invalid call $capability")
                  val invalidCall =
                    if (invoked.id == capability.id) InvalidPolyType(invoked, args, capability, assertion)
                    else InvalidCapability(invoked, capability, assertion)

                  handleLeafFailure(invalidCall, nextScopes, failedMatches)

                case self @ Chain(children, _, invocations, _) =>
                  val scope = children.zipWithIndex.collectFirst {
                    case (child, index) if child.state < Saturated =>
                      Scope[R](
                        child,
                        id,
                        updatedChild => {
                          val updatedChildren = children.updated(index, updatedChild)

                          update(
                            self.copy(
                              children = updatedChildren,
                              state = minimumState(updatedChildren),
                              invocations = id :: invocations
                            )
                          )
                        }
                      )
                  }

                  findMatching(scope.get :: nextScopes, failedMatches)

                case self @ And(children, _, invocations, _) =>
                  val scopes = children.zipWithIndex.collect {
                    case (child, index) if child.state < Saturated =>
                      Scope[R](
                        child,
                        id,
                        updatedChild => {
                          val updatedChildren = children.updated(index, updatedChild)

                          update(
                            self.copy(
                              children = updatedChildren,
                              state = minimumState(updatedChildren),
                              invocations = id :: invocations
                            )
                          )
                        }
                      )
                  }

                  findMatching(scopes ++ nextScopes, failedMatches)

                case self @ Or(children, _, invocations, _) =>
                  children.zipWithIndex.find(_._1.state == PartiallySatisfied) match {
                    case Some((child, index)) =>
                      val scope = Scope[R](
                        child,
                        id,
                        updatedChild => {
                          val updatedChildren = children.updated(index, updatedChild)

                          update(
                            self.copy(
                              children = updatedChildren,
                              state = maximumState(updatedChildren),
                              invocations = id :: invocations
                            )
                          )
                        }
                      )

                      findMatching(scope :: nextScopes, failedMatches)
                    case None =>
                      val scopes = children.zipWithIndex.collect { case (child, index) =>
                        Scope[R](
                          child,
                          id,
                          updatedChild => {
                            val updatedChildren = children.updated(index, updatedChild)

                            update(
                              self.copy(
                                children = updatedChildren,
                                state = maximumState(updatedChildren),
                                invocations = id :: invocations
                              )
                            )
                          }
                        )
                      }

                      findMatching(scopes ++ nextScopes, failedMatches)
                  }

                case self @ Repeated(expectation, range, state, invocations, started, completed) =>
                  val initialize = (state == Saturated) && completed < range.max
                  val child      = if (initialize) resetTree(expectation) else expectation
                  val scope = Scope[R](
                    child,
                    id,
                    updatedChild => {
                      val updatedStarted =
                        if (started == completed) started + 1
                        else started

                      val updatedCompleted =
                        if (updatedChild.state == Saturated) completed + 1
                        else completed

                      val inRepeatsRange: Boolean =
                        if (range.end != -1) range contains updatedStarted
                        else {
                          val fakeUnboundedRange = range.start to Int.MaxValue by range.step
                          fakeUnboundedRange contains updatedStarted
                        }

                      val maxRepeatsReached: Boolean =
                        Try(range.max == updatedCompleted).getOrElse(false)

                      update(
                        self.copy(
                          child = if (updatedChild.state == Saturated) resetTree(updatedChild) else updatedChild,
                          state = updatedChild.state match {
                            case Saturated =>
                              if (!inRepeatsRange) PartiallySatisfied
                              else if (maxRepeatsReached) Saturated
                              else Satisfied

                            case Satisfied =>
                              if (!inRepeatsRange) PartiallySatisfied
                              else Satisfied

                            case childState => childState
                          },
                          invocations = id :: invocations,
                          started = updatedStarted,
                          completed = updatedCompleted
                        )
                      )
                    }
                  )

                  findMatching(scope :: nextScopes, failedMatches)
              }
          }
        }

        def minimumState(children: List[Expectation[R]]): ExpectationState = {
          val states = children.map(_.state)
          val min    = states.min
          val max    = states.max

          if (min >= Satisfied) min
          else if (max >= Satisfied) PartiallySatisfied
          else Unsatisfied
        }

        def maximumState(children: List[Expectation[R]]): ExpectationState =
          children.map(_.state).max

        def handleLeafFailure(
          failure: => InvalidCall,
          nextScopes: List[Scope[R]],
          failedMatches: List[InvalidCall]
        ): MatchResult = {
          val nextFailed = failure :: failedMatches
          if (nextScopes.isEmpty) MatchResult.Failure(nextFailed)
          else findMatching(nextScopes, nextFailed)
        }

        def resetTree(expectation: Expectation[R]): Expectation[R] =
          expectation match {
            case self: Call[R, _, _, _] =>
              self.copy(state = Unsatisfied)
            case self: Chain[R] =>
              self.copy(
                children = self.children.map(resetTree),
                state = Unsatisfied
              )
            case self: And[R] =>
              self.copy(
                children = self.children.map(resetTree),
                state = Unsatisfied
              )
            case self: Or[R] =>
              self.copy(
                children = self.children.map(resetTree),
                state = Unsatisfied
              )
            case self: NoCalls[R] => self
            case self: Repeated[R] =>
              self.copy(
                child = resetTree(self.child),
                state = Unsatisfied,
                completed = 0
              )
          }

        for {
          id <- state.callsCountRef.updateAndGet(_ + 1)
          matchResult <-
            state.expectationRef.modify { root =>
              val scope = Scope[R](root, id, identity)
              val res   = findMatching(scope :: Nil, Nil)
              res match {
                case MatchResult.Success(matched) => res -> matched.expectation
                case MatchResult.UnexpectedCall   => res -> root
                case MatchResult.Failure(_)       => res -> root
              }
            }
          matched <-
            matchResult match {
              case MatchResult.Success(matched)  => ZIO.succeed(matched)
              case MatchResult.UnexpectedCall    => ZIO.die(UnexpectedCallException(invoked, args))
              case MatchResult.Failure(failures) => ZIO.die(InvalidCallException(failures))
            }
          _       = debug(s"::: setting root to\n${prettify(matched.expectation)}")
          output <- matched.result
        } yield output
      }
    })
}
