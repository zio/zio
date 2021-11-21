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

  def missingLayersForZIOApp(value: Set[String]): String = {

    val missingLayers = value.toList.map(_.magenta.bold)
    val missingLayersString = missingLayers.zipWithIndex.map { case (layer, index) =>
      val i = s"${index + 1}.".faint
      s"$i $layer"
    }.mkString("\n").indent(3)

    val message =
      if (missingLayers.size > 1)
        s"Your effect requires services that are not in the environment.\n".bold +
          s"Please provide layers for the following ${missingLayers.size.toString.underlined} types:"
      else
        s"Your effect requires a that is not in the environment.\n".bold +
          s"Please provide a layer for the following type:"

    s"""
       |
       |${title("ZIO App Error").red}
       |
       |${message.indent(1)}
       |
       |$missingLayersString
       |
       | Call your effect's ${"provide".green.bold} method with the layers you need.
       | You can read more about layers and providing services here:
       | 
       |   https://zio.dev/version-1.x/datatypes/contextual/index
       |
       |${line.red}
       |
       |""".stripMargin
  }

  def unusedLayersError(layers: List[String]) = {

    val layerStrings = layers.zipWithIndex.map { case (layer, index) =>
      val i = s"${index + 1}.".faint
      s"$i ${layer.cyan}"
    }.mkString("\n").indent(3)

    s"""
       |${title("ZLayer Warning").yellow}
       |
       | ${"You have provided more than is required.".bold}
       | You may remove the following ${pluralizeLayers(layers.size).bold}:
       |   
       |$layerStrings
       |  
       |${line.yellow}
       |
       |""".stripMargin
  }

  def injectSomeNothingEnvError: String = {
    val message = s"You must provide a type to ${"injectSome".green}.".bold
    val A       = "A".cyan
    val B       = "B".magenta
    val C       = "C".cyan
    val example = s"effect" + s".injectSome[$B".green + "]".green + "(layer)"
    s"""${title("ZLayer Error").red}
       |
       | $message
       | Specify the types of the leftover services, e.g.:
       | 
       |   val effect: URIO[$A & $B & $C, Unit] = ???
       |   
       |   val layer: ULayer[$A & $C] = ???
       | 
       |   $example 
       |       
       |${line.red}
       |""".stripMargin
  }

  def example(): Unit = {
    val missing = Map(
      "UserService.live" -> List("zio.Clock", "example.UserService"),
      "Database.live"    -> List("java.sql.Connection"),
      "Logger.live"      -> List("zio.Console")
    )
    println(missingLayersError(List("java.lang.String", "List[Boolean]"), missing))
    println(unusedLayersError(List("java.lang.String", "List[Boolean]", "zio.Console")))
    println(injectSomeNothingEnvError)
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

  private val width = 70

  /**
   * Makes text look like this:
   *
   * ──── NAME ───────────────────────────────
   */
  private def title(name: String): String =
    s"──── ${name.toUpperCase} ".padTo(width, '─')

  /**
   * A solid line, 80 characters long.
   *
   * ────────────────────────────────
   */
  private def line: String =
    "─" * width

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
