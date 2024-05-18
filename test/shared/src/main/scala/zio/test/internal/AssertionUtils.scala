package zio.test.internal

import zio.test.diff.DiffResult
import zio.test.{ErrorMessage => M}
import zio.test.ConsoleUtils
import zio.test.PrettyPrint
import zio.internal.ansi.AnsiStringOps

/*
* Renders the difference between expected and actual values based on the provided `DiffResult`.
* @param diffResult The result of the diff operation.*
* @param expected The expected value.
* @param actual The actual value.
* @return An `ErrorMessage` that represents the difference.
*/

object AssertionUtils {
  def renderDiffResult[A](diffResult: DiffResult, expected: A, actual: A): M = 
    diffResult match {
      case DiffResult.Different(_, _, None) =>
        M.pretty(expected) + M.equals + M.pretty(actual)
      case diffResult =>
        M.choice("There was no difference", "There was a difference") ++
          M.custom(ConsoleUtils.underlined("Expected")) ++ M.custom(PrettyPrint(expected)) ++
          M.custom(
            ConsoleUtils.underlined(
              "Diff"
            ) + s" ${scala.Console.RED}-expected ${scala.Console.GREEN}+obtained".faint
          ) ++
          M.custom(scala.Console.RESET + diffResult.render)
    }
}
