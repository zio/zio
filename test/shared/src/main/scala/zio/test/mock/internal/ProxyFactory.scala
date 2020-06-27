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

import scala.util.{ Properties, Try }

import zio.test.Assertion
import zio.test.mock.{ Capability, DebugProperties, Expectation, Proxy }
import zio.{ Has, IO, Tag, UIO, ULayer, ZIO, ZLayer }

object ProxyFactory {

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
        def findMatching(scopes: List[Scope[R]]): UIO[Matched[R, E, A]] = {
          Debug.print(s"::: invoked $invoked\n${Debug.prettify(scopes)}")
          scopes match {
            case Nil => ZIO.die(UnexpectedCallExpection(invoked, args))
            case Scope(expectation, id, update0) :: nextScopes =>
              val update: Expectation[R] => Expectation[R] = updated => {
                Debug.print(s"::: updated state to: ${updated.state}")
                update0(updated)
              }

              expectation match {
                case anyExpectation if anyExpectation.state == Saturated =>
                  Debug.print("::: skipping saturated expectation")
                  findMatching(nextScopes)

                case call @ Call(capability, assertion, returns, _, invocations) if invoked isEqual capability =>
                  Debug.print(s"::: matched call $capability")
                  assertion.asInstanceOf[Assertion[I]].test(args) match {
                    case true =>
                      val result = returns.asInstanceOf[I => IO[E, A]](args)
                      val updated = call
                        .asInstanceOf[Call[R, I, E, A]]
                        .copy(
                          state = Saturated,
                          invocations = id :: invocations
                        )

                      UIO.succeedNow(Matched[R, E, A](update(updated), result))

                    case false =>
                      handleLeafFailure(
                        InvalidArguments(invoked, args, assertion.asInstanceOf[Assertion[Any]]),
                        nextScopes
                      )
                  }

                case Call(capability, assertion, _, _, _) =>
                  Debug.print(s"::: invalid call $capability")
                  val invalidCall =
                    if (invoked.id == capability.id) InvalidPolyType(invoked, args, capability, assertion)
                    else InvalidCapability(invoked, capability, assertion)

                  handleLeafFailure(invalidCall, nextScopes)

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

                  findMatching(scope.get :: nextScopes)

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

                  findMatching(scopes ++ nextScopes)

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

                      findMatching(scope :: nextScopes)
                    case None =>
                      val scopes = children.zipWithIndex.collect {
                        case (child, index) =>
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

                      findMatching(scopes ++ nextScopes)
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

                  findMatching(scope :: nextScopes)
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
            case self: Repeated[R] =>
              self.copy(
                child = resetTree(self.child),
                state = Unsatisfied,
                completed = 0
              )
          }

        for {
          id      <- state.callsCountRef.updateAndGet(_ + 1)
          _       <- state.failedMatchesRef.set(List.empty)
          root    <- state.expectationRef.get
          scope   = Scope[R](root, id, identity)
          matched <- findMatching(scope :: Nil)
          _       = Debug.print(s"::: setting root to\n${Debug.prettify(matched.expectation)}")
          _       <- state.expectationRef.set(matched.expectation)
          output  <- matched.result
        } yield output
      }
    })

  private[ProxyFactory] object Debug {

    val enabled: Boolean = Properties.propIsSetTo(DebugProperties.`zio.test.mock.debug`, "true")

    def print(message: => String): Unit =
      if (enabled) println(message)

    def prettify[R <: Has[_]](expectation: Expectation[R], identSize: Int = 1): String = {
      val ident   = " " * 4 * identSize
      val state   = s"state = ${expectation.state}"
      val invoked = s"""invocations = [${expectation.invocations.mkString(", ")}]"""

      def renderRoot(name: String, children: List[Expectation[R]]): String = {
        val header    = (s"$name(" :: s"$state," :: s"$invoked," :: Nil).mkString(s"\n$ident")
        val content   = renderChildren(children).mkString("\n")
        val prevIdent = " " * 4 * (identSize - 1)
        s"$header,\n$content\n$prevIdent)"
      }

      def renderChildren(list: List[Expectation[R]]): List[String] =
        list.map { child =>
          val rendered = prettify(child, identSize + 1)
          s"$ident$rendered"
        }

      expectation match {
        case Expectation.Call(capability, assertion, _, _, _) =>
          s"Call($state, $invoked, $capability, $assertion)"
        case Expectation.And(children, _, _, _) =>
          renderRoot("And", children)
        case Expectation.Chain(children, _, _, _) =>
          renderRoot("Chain", children)
        case Expectation.Or(children, _, _, _) =>
          renderRoot("Or", children)
        case Expectation.Repeated(child, range, _, _, started, completed) =>
          val progress = s"progress = $started out of $completed,"
          ("Repeated(" :: state :: s"range = $range," :: progress :: invoked :: prettify(child) :: ")" :: Nil)
            .mkString(s"\n$ident")
      }
    }

    def prettify[R <: Has[_]](scopes: List[Scope[R]]): String =
      scopes.map {
        case Scope(expectation, id, _) =>
          val rendered = prettify(expectation)
          s">>>\nInvocation ID: $id\n$rendered"
      } match {
        case Nil         => ""
        case head :: Nil => s"[Head]:\n$head"
        case head :: tail =>
          val renderedTail = tail.mkString("\n")
          s"[Head]:\n$head\n[Tail]:\n$renderedTail"
      }
  }
}
