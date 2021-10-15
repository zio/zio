package fix

import scalafix.v1._

import scala.annotation.tailrec
import scala.meta._

class Zio2Upgrade extends SemanticRule("Zio2Upgrade") {

  val renames =
    Map(
      "accessM"                -> "accessZIO",
      "bimap"                  -> "mapBoth",
      "bracket"                -> "acquireReleaseWith",
      "bracketExit"            -> "acquireReleaseExitWith",
      "bracketExit_"           -> "acquireReleaseExit",
      "bracketOnError"         -> "acquireReleaseOnErrorWith",
      "bracketOnError_"        -> "acquireReleaseOnError",
      "bracket_"               -> "acquireRelease",
      "bracket_"               -> "acquireRelease",
      "collectAllParN"         -> "collectAllPar",
      "collectAllParNDiscard"  -> "collectAllParDiscard",
      "collectAllParN_"        -> "collectAllParNDiscard",
      "collectAllPar_"         -> "collectAllParDiscard",
      "collectAll_"            -> "collectAllDiscard",
      "collectM"               -> "collectZIO",
      "contramapM"             -> "contramapZIO",
      "effect"                 -> "attempt",
      "effectAsync"            -> "async",
      "effectAsyncInterrupt"   -> "asyncInterrupt",
      "effectAsyncM"           -> "asyncZIO",
      "effectAsyncMaybe"       -> "asyncMaybe",
      "effectSuspend"          -> "suspend",
      "effectSuspendTotal"     -> "suspendSucceed",
      "effectSuspendWith"      -> "suspendWith",
      "effectTotal"            -> "succeed",
      "filterInputM"           -> "filterInputZIO",
      "filterM"                -> "filterZIO",
      "filterOrElse"           -> "filterOrElseWith",
      "filterOrElse_"          -> "filterOrElse",
      "flattenM"               -> "flattenZIO",
      "foldCauseM"             -> "foldCauseZIO",
      "foldLeftM"              -> "foldLeftZIO",
      "foldM"                  -> "foldZIO",
      "foldTraceM"             -> "foldTraceZIO",
      "foldWeightedDecomposeM" -> "foldWeightedDecomposeZIO",
      "foldWeightedM"          -> "foldWeightedZIO",
      "foreachParN"            -> "foreachPar",
      "foreachParNDiscard"     -> "foreachParDiscard",
      "foreachParN_"           -> "foreachParNDiscard",
      "foreachPar_"            -> "foreachParDiscard",
      "foreach_"               -> "foreachDiscard",
      "fromEffect"             -> "fromZIO",
      "fromEffect"             -> "fromZIO",
      "fromEffectOption"       -> "fromZIOOption",
      "fromIterableM"          -> "fromIterableZIO",
      "fromIteratorTotal"      -> "fromIteratorSucceed",
      "halt"                   -> "failCause",
      "haltWith"               -> "failCauseWith",
      "ifM"                    -> "ifZIO",
      "loop_"                  -> "loopDiscard",
      "makeReserve"            -> "fromReservationZIO",
      "mapConcatM"             -> "mapConcatZIO",
      "mapEffect"              -> "mapAttempt",
      "mapM"                   -> "mapZIO",
      "mapMPar"                -> "mapZIOPar",
      "mapMParUnordered"       -> "mapZIOParUnordered",
      "optional"               -> "unsome",
      "paginateChunkM"         -> "paginateChunkZIO",
      "paginateM"              -> "paginateZIO",
      "rejectM"                -> "rejectZIO",
      "repeatEffect"           -> "repeatZIO",
      "repeatEffectChunk"      -> "repeatZIOChunk",
      "repeatEffectOption"     -> "repeatZIOOption",
      "repeatEffectWith"       -> "repeatZIOWithSchedule",
      "repeatUntilM"           -> "repeatUntilZIO",
      "repeatWhileM"           -> "repeatWhileZIO",
      "repeatZIOWith"          -> "repeatZIOWithSchedule",
      "replicateM"             -> "replicateZIO",
      "replicateM_"            -> "replicateZIODiscard",
      "reserve"                -> "fromReservation",
      "retryUntilM"            -> "retryUntilZIO",
      "retryWhileM"            -> "retryWhileZIO",
      "someOrElseM"            -> "someOrElseZIO",
      "tapM"                   -> "tapZIO",
      "testM"                  -> "test",
      "toManaged"              -> "toManagedWith",
      "toManaged_"             -> "toManaged",
      "unfoldM"                -> "unfoldZIO",
      "unlessM"                -> "unlessZIO",
      "unsafeRunAsync"         -> "unsafeRunAsyncWith",
      "unsafeRunAsync_"        -> "unsafeRunAsync",
      "use_"                   -> "useDiscard",
      "validateParN_"          -> "validateParNDiscard",
      "validatePar_"           -> "validateParDiscard",
      "validate_"              -> "validateDiscard",
      "whenCaseM"              -> "whenCaseZIO",
      "whenM"                  -> "whenZIO"
    )

  lazy val scopes = List(
    "zio.test.package",
    "zio.test.DefaultRunnableSpec",
    "zio.Exit",
    "zio.ZIO",
    "zio.IO",
    "zio.Managed",
    "zio.RIO",
    "zio.Task",
    "zio.UIO",
    "zio.URIO",
    "zio.ZManaged",
    "zio.Fiber",
    "zio.ZRef",
    "zio.Ref",
    "zio.Promise",
    "zio.stream.ZSink",
    "zio.stream.ZStream",
    "zio.stream.ZTransducer",
    "zio.stream.experimental.ZChannel",
    "zio.stream.experimental.Take",
    "zio.stream.experimental.ZPipeline",
    "zio.test.TestFailure",
    "zio.Runtime"
  )

  case class GenericRename(scopes: List[String], oldName: String, newName: String) {
    val companions = scopes.map(_ + ".")
    val traits     = scopes.map(_ + "#")
    val allPaths   = companions ++ traits

    val list    = allPaths.map(path => s"$path$oldName")
    val matcher = SymbolMatcher.normalized(list: _*)

    def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
      tree match {
        case matcher(name @ Name(_)) =>
          Some(Patch.renameSymbol(name.symbol, newName))
        case _ => None
      }
  }

  case class Renames(scopes: List[String], renames: Map[String, String]) {
    val normalizedRenames = renames.map { case (k, v) =>
      GenericRename(scopes, k, v)
    }

    object Matcher {
      def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
        normalizedRenames.flatMap(_.unapply(tree)).headOption
    }
  }

  val UniversalRenames = Renames(scopes, renames)

  val ZIORenames = Renames(
    List("zio.ZIO"),
    Map("run" -> "exit")
  )

  val ZManagedRenames = Renames(
    List("zio.ZManaged", "zio.Managed"),
    Map(
      "collectM"                  -> "collectManaged",
      "foldCauseM"                -> "foldCauseManaged",
      "foldM"                     -> "foldManaged",
      "fromEffectUninterruptible" -> "fromZIOUninterruptible",
      "fromFunctionM"             -> "fromFunctionManaged",
      "ifM"                       -> "ifManaged",
      "make"                      -> "acquireReleaseWith",
      "makeEffect"                -> "acquireReleaseAttemptWith",
      "makeEffectTotal"           -> "acquireReleaseSucceedWith",
      "makeEffectTotal_"          -> "acquireReleaseSucceed",
      "makeEffect_"               -> "acquireReleaseAttempt",
      "makeExit"                  -> "acquireReleaseExitWith",
      "makeExit_"                 -> "acquireReleaseExit",
      "make_"                     -> "acquireRelease",
      "mapM"                      -> "mapZIO",
      "rejectM"                   -> "rejectManaged",
      "run"                       -> "exit",
      "someOrElseM"               -> "someOrElseManaged",
      "unlessM"                   -> "unlessManaged",
      "whenCaseM"                 -> "whenCaseManaged",
      "whenM"                     -> "whenManaged"
    )
  )

  val Random_Old  = SymbolMatcher.normalized("zio/random/package.Random#")
  val Clock_Old   = SymbolMatcher.normalized("zio/clock/package.Clock#")
  val System_Old  = SymbolMatcher.normalized("zio/system/package.System#")
  val Console_Old = SymbolMatcher.normalized("zio/console/package.Console#")
  val Sized_Old   = SymbolMatcher.normalized("zio/test/package.Sized#")
  val Live_Old    = SymbolMatcher.normalized("zio/test/environment/package.Live#")

  val Blocking_Old_Exact   = SymbolMatcher.exact("zio/blocking/package.Blocking#")
  val Random_Old_Exact     = SymbolMatcher.exact("zio/random/package.Random#")
  val Clock_Old_Exact      = SymbolMatcher.exact("zio/clock/package.Clock#")
  val System_Old_Exact     = SymbolMatcher.exact("zio/system/package.System#")
  val Console_Old_Exact    = SymbolMatcher.exact("zio/console/package.Console#")
  val Test_Clock_Old_Exact = SymbolMatcher.exact("zio/test/environment/package.TestClock#")
  val Sized_Old_Exact      = SymbolMatcher.exact("zio/test/package.Sized#")
  val Live_Old_Exact       = SymbolMatcher.exact("zio/test/environment/package.Live#")

  val newRandom    = Symbol("zio/Random#")
  val newConsole   = Symbol("zio/Console#")
  val newSystem    = Symbol("zio/System#")
  val newClock     = Symbol("zio/Clock#")
  val newTestClock = Symbol("zio/test/environment/TestClock#")
  val newSized     = Symbol("zio/test/Sized#")
  val newLive      = Symbol("zio/test/environment/Live#")

  val Clock_Old_Package   = SymbolMatcher.normalized("zio.clock")
  val Random_Old_Package  = SymbolMatcher.normalized("zio.random")
  val Console_Old_Package = SymbolMatcher.normalized("zio.console")
  val System_Old_Package  = SymbolMatcher.normalized("zio.system")

  val Test_Clock_Old_Service = SymbolMatcher.exact("zio/clock/environment/package.TestClock.Service#")
  val Clock_Old_Service      = SymbolMatcher.exact("zio/clock/package.Clock.Service#")
  val Random_Old_Service     = SymbolMatcher.exact("zio/random/package.Random.Service#")
  val Console_Old_Service    = SymbolMatcher.exact("zio/console/package.Console.Service#")
  val System_Old_Service     = SymbolMatcher.exact("zio/system/package.System.Service#")

  def fixPackages(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case q"${Clock_Old_Package(name)}.$select" if select.value != "Clock" =>
        Patch.replaceTree(name, "Clock")

      case q"${Random_Old_Package(name)}.$select" if select.value != "Random" =>
        Patch.replaceTree(name, "Random")

      case q"${System_Old_Package(name)}.$select" if select.value != "System" =>
        Patch.replaceTree(name, "System")

      case q"${Console_Old_Package(name)}.$select" if select.value != "Console" =>
        Patch.replaceTree(name, "Console")
    }.asPatch

  def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
    "zio.console.putStrLn"          -> "zio.Console.printLine",
    "zio.console.getStrLn"          -> "zio.Console.readLine",
    "zio.console.putStr"            -> "zio.Console.print",
    "zio.console.putStrLnErr"       -> "zio.Console.printLineError",
    "zio.console.putStrErr"         -> "zio.Console.printError",
    "zio.blocking.effectBlockingIO" -> "zio.ZIO.attemptBlockingIO",
    "zio.ZIO.$greater$greater$eq"   -> "zio.ZIO.flatMap"
  )

  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case ZIORenames.Matcher(patch)       => patch
      case ZManagedRenames.Matcher(patch)  => patch
      case UniversalRenames.Matcher(patch) => patch

      // Replace >>= with flatMap. For some reason, this doesn't work with the
      // technique used above.
      case t @ q"${lhs} >>= $rhs" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs flatMap $rhs")
      case t @ q"${lhs}.>>=($rhs)" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs.flatMap($rhs)")

      case t @ q"import zio.duration._" =>
        Patch.replaceTree(t, "") +
          Patch.addGlobalImport(wildcardImport(q"zio"))

      case t @ q"import zio.system" =>
        Patch.replaceTree(t, "") + Patch.addGlobalImport(newSystem)

      /**
       * Rename Services
       * Clock.Service -> Clock
       */
      case t @ Test_Clock_Old_Service(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "TestClock") +
          Patch.addGlobalImport(newTestClock)

      case t @ Clock_Old_Service(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "Clock") +
          Patch.addGlobalImport(newClock)

      case t @ Random_Old_Service(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "Random") +
          Patch.addGlobalImport(newRandom)

      case t @ System_Old_Service(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "System") +
          Patch.addGlobalImport(newSystem)

      case t @ Console_Old_Service(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "Console") +
          Patch.addGlobalImport(newConsole)

      case t @ Blocking_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newRandom) +
          Patch.replaceTree(unwindSelect(t), s"Any")

      case t @ Random_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newRandom) +
          Patch.replaceTree(unwindSelect(t), s"Has[Random]")

      case t @ Random_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newRandom) +
          Patch.replaceTree(unwindSelect(t), s"Has[Random]")

      case t @ Console_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newConsole) +
          Patch.replaceTree(unwindSelect(t), s"Has[Console]")

      case t @ System_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newSystem) +
          Patch.replaceTree(unwindSelect(t), s"Has[System]")

      case t @ Clock_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newClock) +
          Patch.replaceTree(unwindSelect(t), s"Has[Clock]")

      case t @ Test_Clock_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newTestClock) +
          Patch.replaceTree(unwindSelect(t), s"Has[TestClock]")

      case t @ Sized_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newSized) +
          Patch.replaceTree(unwindSelect(t), s"Has[Sized]")

      case t @ Live_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(newLive) +
          Patch.replaceTree(unwindSelect(t), s"Has[Live]")

      case t @ ImporteeNameOrRename(Random_Old(_) | Clock_Old(_) | Console_Old(_) | System_Old(_) | Sized_Old(_)) =>
        Patch.removeImportee(t)

      case t @ q"import zio.console._" =>
        Patch.replaceTree(t, "") +
          Patch.addGlobalImport(wildcardImport(q"zio.Console"))
    }.asPatch + fixPackages + replaceSymbols

  private def wildcardImport(ref: Term.Ref): Importer =
    Importer(ref, List(Importee.Wildcard()))

  @tailrec
  private def unwindSelect(t: Tree): Tree = t.parent match {
    case Some(t: Type.Select) => unwindSelect(t)
    case Some(t: Term.Select) => unwindSelect(t)
    case _                    => t
  }
}

private object ImporteeNameOrRename {
  def unapply(importee: Importee): Option[Name] =
    importee match {
      case Importee.Name(x)      => Some(x)
      case Importee.Rename(x, _) => Some(x)
      case _                     => None
    }
}
