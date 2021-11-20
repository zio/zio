package zio.internal

import ansi._

object TerminalRendering {

  def circularityError(circularDependencies: List[(String, String)]): String = {
    val circularDependencyMessage =
      circularDependencies.map { case (a0, b0) =>
        val a = a0.red.bold.underlined
        val b = b0.cyan.bold
        s"""${" Circular Dependency Detected ".red.inverted}
           |
           |A layer simultaneously requires and is required by another:
           |
           |  ${"◉".faint} $b
           |  ${"╰─◉".faint.red} $a
           |    ${"╰─ ◉".faint} $b
           |""".stripMargin.indent(1)

      }.mkString("\n")

    s"""
       |
       |${title("ZLayer Error").red}
       |
       |$circularDependencyMessage
       |       
       |${line.red}
       |
       |""".stripMargin
  }

  def missingLayersError(
    toplevel: List[String],
    transitive: Map[String, List[String]] = Map.empty
  ) = {

    println(toplevel.mkString("\n"))
    println(transitive.mkString("\n"))

    var index   = 0
    def next    = { index += 1; index }
    var indices = Map.empty[String, String]
    def id(layer: String) = indices.getOrElse(
      layer, {
        val i = s"$next.".faint
        indices += (layer -> i)
        i
      }
    )

    val topLevelString = toplevel.map { layer =>
      s"${id(layer)} ${layer.magenta.bold}"
    }.mkString("\n").indent(3)

    val transitiveStrings = transitive.map { case (layer, deps) =>
      val depsString = deps.map { dep =>
        s"${id(dep)} ${dep.magenta.bold}"
      }.mkString("\n")
      s"Required by ${layer.cyan}\n" + depsString
    }.mkString("\n\n").indent(3)

    val errors =
      List(topLevelString, transitiveStrings).filter(_.trim.nonEmpty).mkString("\n\n")

    val message =
      if (indices.size > 1) {
        s"Please provide layers for the following ${indices.size.toString.underlined} types:"
      } else {
        s"Please provide a layer for the following type:"
      }

    s"""
       |
       |${title("ZLayer Error").red}
       |
       | ${message.bold}
       |
       |$errors
       |       
       |${line.red}
       |
       |""".stripMargin
  }

  def unusedLayersError(layers: List[String]) = {

    val layerStrings = layers.zipWithIndex.map { case (layer, index) =>
      val i = s"${index + 1}.".faint
      s"$i ${layer.cyan}"
    }.mkString("\n").indent(2)

    s"""
       |${title("ZLayer Warning").yellow}
       |
       |${"You have provided more than is required.".bold}
       |You may remove the following ${pluralizeLayers(layers.size).bold}:
       |   
       |$layerStrings
       |  
       |${line.yellow}
       |
       |""".stripMargin
  }

  val unexpectedOutputError = {
    val B = "Option".cyan
    val A = "Int".cyan
    s"""${title("Invalid Error Type")}
       |
       | This method is only callable when the error type is an $B
       | But the error type of this effect is an $A
       |       
       |$line
       |""".stripMargin
  }

  def example: Unit = {
    val missing = Map(
      "UserService.live" -> List("zio.Clock", "example.UserService"),
      "Database.live"    -> List("java.sql.Connection"),
      "Logger.live"      -> List("zio.Console")
    )
    println(missingLayersError(List("java.lang.String", "List[Boolean]"), missing))
    println(unusedLayersError(List("java.lang.String", "List[Boolean]", "zio.Console")))
  }

  /**
   * Pluralizes a word based on the number of items.
   */
  def pluralize(n: Int, singular: String, plural: String): String =
    if (n == 1) singular else s"$n $plural"

  /**
   * Pluralizes the word "layer".
   *
   * layer, 2 layers, 3 layers, etc.
   */
  def pluralizeLayers(n: Int): String =
    pluralize(n, "layer", "layers")

  /**
   * Makes text look like this:
   *
   * ──── NAME ───────────────────────────────
   */
  private def title(name: String): String =
    s"─── ${name.toUpperCase} ".padTo(80, '─')

  /**
   * A solid line, 80 characters long.
   *
   * ────────────────────────────────
   */
  private def line: String =
    "─" * 80

  implicit final class StringOps(private val self: String) extends AnyVal {
    def center(n: Int): String =
      " " * n + self + " " * n

    def indent(n: Int): String =
      self.split("\n").map(line => " " * n + line).mkString("\n")
  }

  sealed trait LayerWiringError

  object LayerWiringError {
    final case class MissingTopLevel(layer: String) extends LayerWiringError

    final case class MissingTransitive(layer: String, deps: List[String]) extends LayerWiringError

    final case class Circular(layer: String, dependency: String) extends LayerWiringError
  }

}
