package zio.test.internal

import zio.test.diff.DiffResult
import zio.test.{ErrorMessage => M}
import zio.test.ConsoleUtils
import zio.test.PrettyPrint

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

