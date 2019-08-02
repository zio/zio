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

package zio.test

import scala.{ Console => SConsole }

import zio.{ Cause, ZIO }
import zio.console.{ putStrLn, Console }

object DefaultTestReporter {
  def apply(console: Console): TestReporter[String] = { (executedSpec: ExecutedSpec[String]) =>
    {
      def loop(executedSpec: ExecutedSpec[String], offset: Int): ZIO[Console, Nothing, Unit] =
        executedSpec.caseValue match {
          case Spec.SuiteCase(label, executedSpecs, _) =>
            val reportSuite =
              if (executedSpecs.exists(_.exists { case Spec.TestCase(_, test) => test.failure; case _ => false }))
                reportFailure(label, offset)
              else reportSuccess(label, offset)
            reportSuite *> ZIO.foreach_(executedSpecs)(loop(_, offset + tabSize))
          case Spec.TestCase(label, result) =>
            result match {
              case Assertion.Success =>
                reportSuccess(label, offset)
              case Assertion.Failure(details) =>
                reportFailure(label, offset) *> reportFailureDetails(details, offset)
              case Assertion.Ignore =>
                ZIO.unit
            }
        }

      loop(executedSpec, 0).provide(console)
    }
  }

  private def reportSuccess(label: String, offset: Int): ZIO[Console, Nothing, Unit] =
    putStrLn(withOffset(offset)(green("+") + " " + label))

  private def reportFailure(label: String, offset: Int): ZIO[Console, Nothing, Unit] =
    putStrLn(withOffset(offset)(red("- " + label)))

  private def reportFailureDetails(failureDetails: FailureDetails, offset: Int): ZIO[Console, Nothing, Unit] =
    failureDetails match {
      case FailureDetails.Predicate(fragment, whole) => reportPredicate(fragment, whole, offset)
      case FailureDetails.Runtime(cause)             => reportCause(cause, offset)
    }

  private def reportPredicate(
    fragment: PredicateValue,
    whole: PredicateValue,
    offset: Int
  ): ZIO[Console, Nothing, Unit] =
    if (whole.predicate == fragment.predicate)
      reportFragment(fragment, offset)
    else
      reportWhole(fragment, whole, offset) *> reportFragment(fragment, offset)

  private def reportWhole(fragment: PredicateValue, whole: PredicateValue, offset: Int): ZIO[Console, Nothing, Unit] =
    putStrLn {
      withOffset(offset + tabSize) {
        blue(whole.value.toString) +
          " did not satisfy " +
          highlight(cyan(whole.predicate.toString), fragment.predicate.toString)
      }
    }

  private def reportFragment(fragment: PredicateValue, offset: Int): ZIO[Console, Nothing, Unit] =
    putStrLn {
      withOffset(offset + tabSize) {
        blue(fragment.value.toString) +
          " did not satisfy " +
          cyan(fragment.predicate.toString)
      }
    }

  private def reportCause(cause: Cause[Any], offset: Int): ZIO[Console, Nothing, Unit] = {
    val pretty = cause.prettyPrint.split("\n").map(withOffset(offset + tabSize)).mkString("\n")
    putStrLn(pretty)
  }

  private def withOffset(n: Int)(s: String): String =
    " " * n + s

  private def green(s: String): String =
    SConsole.GREEN + s + SConsole.RESET

  private def red(s: String): String =
    SConsole.RED + s + SConsole.RESET

  private def blue(s: String): String =
    SConsole.BLUE + s + SConsole.RESET

  private def cyan(s: String): String =
    SConsole.CYAN + s + SConsole.RESET

  private def yellow(s: String): String =
    SConsole.YELLOW + s + SConsole.CYAN

  private def highlight(string: String, substring: String): String =
    string.replace(substring, yellow(substring))

  private val tabSize = 2
}
