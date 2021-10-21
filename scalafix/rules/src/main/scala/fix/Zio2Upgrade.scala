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
      "checkM"                 -> "check",
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
      "partitionPar_"          -> "partitionParDiscard",
      "partition_"             -> "partitionDiscard",
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
  val TestService_Old      = SymbolMatcher.normalized("zio/test/package.TestConfig#")

  val Blocking_Old_Exact   = SymbolMatcher.exact("zio/blocking/package.Blocking#")
  val Random_Old_Exact     = SymbolMatcher.exact("zio/random/package.Random#")
  val Clock_Old_Exact      = SymbolMatcher.exact("zio/clock/package.Clock#")
  val System_Old_Exact     = SymbolMatcher.exact("zio/system/package.System#")
  val Console_Old_Exact    = SymbolMatcher.exact("zio/console/package.Console#")
  val Test_Clock_Old_Exact = SymbolMatcher.exact("zio/test/environment/package.TestClock#")
  val Sized_Old_Exact      = SymbolMatcher.exact("zio/test/package.Sized#")
  val Live_Old_Exact       = SymbolMatcher.exact("zio/test/environment/package.Live#")
  val TestService_Old_Exact      = SymbolMatcher.exact("zio/test/package.TestConfig#")

  val hasImport    = Symbol("zio/Has#")
  val newRandom    = Symbol("zio/Random#")
  val newConsole   = Symbol("zio/Console#")
  val newSystem    = Symbol("zio/System#")
  val newClock     = Symbol("zio/Clock#")
  val newTestClock = Symbol("zio/test/environment/TestClock#")
  val newSized     = Symbol("zio/test/Sized#")
  val newLive      = Symbol("zio/test/environment/Live#")
  val newTestConfig      = Symbol("zio/test/TestConfig#")

  val Clock_Old_Package   = SymbolMatcher.normalized("zio.clock")
  val Random_Old_Package  = SymbolMatcher.normalized("zio.random")
  val Console_Old_Package = SymbolMatcher.normalized("zio.console")
  val System_Old_Package  = SymbolMatcher.normalized("zio.system")

  val Test_Clock_Old_Service = SymbolMatcher.exact("zio/clock/environment/package.TestClock.Service#")
  val Clock_Old_Service      = SymbolMatcher.exact("zio/clock/package.Clock.Service#")
  val Random_Old_Service     = SymbolMatcher.exact("zio/random/package.Random.Service#")
  val Console_Old_Service    = SymbolMatcher.exact("zio/console/package.Console.Service#")
  val System_Old_Service     = SymbolMatcher.exact("zio/system/package.System.Service#")

  def replaceSymbols(implicit doc: SemanticDocument) = Patch.replaceSymbols(
    // System
    "zio.system.env"              -> "zio.System.env",
    "zio.system.envOrElse"        -> "zio.System.envOrElse",
    "zio.system.envOrOption"      -> "zio.System.envOrOption",
    "zio.system.envs"             -> "zio.System.envs",
    "zio.system.lineSeparator"    -> "zio.System.lineSeparator",
    "zio.system.properties"       -> "zio.System.properties",
    "zio.system.property"         -> "zio.System.property",
    "zio.system.propertyOrElse"   -> "zio.System.propertyOrElse",
    "zio.system.propertyOrOption" -> "zio.System.propertyOrOption",
    // Console
    "zio.console.putStrLn"    -> "zio.Console.printLine",
    "zio.console.getStrLn"    -> "zio.Console.readLine",
    "zio.console.putStr"      -> "zio.Console.print",
    "zio.console.putStrLnErr" -> "zio.Console.printLineError",
    "zio.console.putStrErr"   -> "zio.Console.printError",
    // Clock
    "zio.clock.sleep"           -> "zio.Clock.sleep",
    "zio.clock.instant"         -> "zio.Clock.instant",
    "zio.clock.nanoTime"        -> "zio.Clock.nanoTime",
    "zio.clock.localDateTime"   -> "zio.Clock.localDateTime",
    "zio.clock.currentTime"     -> "zio.Clock.currentTime",
    "zio.clock.currentDateTime" -> "zio.Clock.currentDateTime",
    // Duration
    "zio.duration.Duration"     -> "zio.Duration",
    // Random
    "zio.random.nextString"        -> "zio.Random.nextString",
    "zio.random.nextBoolean"       -> "zio.Random.nextBoolean",
    "zio.random.nextBytes"         -> "zio.Random.nextBytes",
    "zio.random.nextDouble"        -> "zio.Random.nextDouble",
    "zio.random.nextDoubleBetween" -> "zio.Random.nextDoubleBetween",
    "zio.random.nextFloat"         -> "zio.Random.nextFloat",
    "zio.random.nextFloatBetween"  -> "zio.Random.nextFloatBetween",
    "zio.random.nextGaussian"      -> "zio.Random.nextGaussian",
    "zio.random.nextInt"           -> "zio.Random.nextInt",
    "zio.random.nextIntBetween"    -> "zio.Random.nextIntBetween",
    "zio.random.nextIntBounded"    -> "zio.Random.nextIntBounded",
    "zio.random.nextLong"          -> "zio.Random.nextLong",
    "zio.random.nextLongBetween"   -> "zio.Random.nextLongBetween",
    "zio.random.nextLongBounded"   -> "zio.Random.nextLongBounded",
    "zio.random.nextPrintableChar" -> "zio.Random.nextPrintableChar",
    "zio.random.nextString"        -> "zio.Random.nextString",
    "zio.random.nextUUID"          -> "zio.Random.nextUUID",
    "zio.random.setSeed"           -> "zio.Random.setSeed",
    "zio.random.shuffle"           -> "zio.Random.shuffle",
    // Blocking
    "zio.blocking.effectBlockingIO"         -> "zio.ZIO.attemptBlockingIO",
    "zio.blocking.effectBlocking"           -> "zio.ZIO.attemptBlocking",
    "zio.blocking.effectBlockingCancelable" -> "zio.ZIO.attemptBlockingCancelable",
    "zio.blocking.effectBlockingInterrupt"  -> "zio.ZIO.attemptBlockingInterrupt",
    "zio.blocking.blocking"                 -> "zio.ZIO.blocking",
    "zio.blocking.blockingExecutor"         -> "zio.ZIO.blockingExecutor",
    // Gen
    "zio.test.Gen.anyInt" -> "zio.test.Gen.int",
    "zio.test.Gen.anyString" -> "zio.test.Gen.string",
    "zio.test.Gen.anyUnicodeChar" -> "zio.test.Gen.unicodeChar",
    "zio.test.Gen.anyASCIIChar" -> "zio.test.Gen.asciiChar",
    "zio.test.Gen.anyByte" -> "zio.test.Gen.byte",
    "zio.test.Gen.anyChar" -> "zio.test.Gen.char",
    "zio.test.Gen.anyDouble" -> "zio.test.Gen.double",
    "zio.test.Gen.anyFloat" -> "zio.test.Gen.float",
    "zio.test.Gen.anyHexChar" -> "zio.test.Gen.hexChar",
    "zio.test.Gen.anyLong" -> "zio.test.Gen.long",
    "zio.test.Gen.anyLowerHexChar" -> "zio.test.Gen.hexCharLower",
    "zio.test.Gen.anyShort" -> "zio.test.Gen.short",
    "zio.test.Gen.anyUpperHexChar" -> "zio.test.Gen.hexCharUpper",
    "zio.test.Gen.anyASCIIString" -> "zio.test.Gen.asciiString",
    "zio.test.Gen.anyUUID" -> "zio.test.Gen.uuid",
    "zio.test.TimeVariants.anyDayOfWeek" -> "zio.test.Gen.dayOfWeek",
    "zio.test.TimeVariants.anyFiniteDuration" -> "zio.test.Gen.finiteDuration",
    "zio.test.TimeVariants.anyLocalDate" -> "zio.test.Gen.localDate",
    "zio.test.TimeVariants.anyLocalTime" -> "zio.test.Gen.localTime",
    "zio.test.TimeVariants.anyLocalDateTime" -> "zio.test.Gen.localDateTime",
    "zio.test.TimeVariants.anyMonth" -> "zio.test.Gen.month",
    "zio.test.TimeVariants.anyMonthDay" -> "zio.test.Gen.monthDay",
    "zio.test.TimeVariants.anyOffsetDateTime" -> "zio.test.Gen.offsetDateTime",
    "zio.test.TimeVariants.anyOffsetTime" -> "zio.test.Gen.offsetTime",
    "zio.test.TimeVariants.anyPeriod" -> "zio.test.Gen.period",
    "zio.test.TimeVariants.anyYear" -> "zio.test.Gen.year",
    "zio.test.TimeVariants.anyYearMonth" -> "zio.test.Gen.yearMonth",
    "zio.test.TimeVariants.anyZonedDateTime" -> "zio.test.Gen.zonedDateTime",
    "zio.test.TimeVariants.anyZoneOffset" -> "zio.test.Gen.zoneOffset",
    "zio.test.TimeVariants.anyZoneId" -> "zio.test.Gen.zoneId",
    // App
    "zio.App" -> "zio.ZIOAppDefault"
  )

  val foreachParN             = ParNRenamer("foreachPar", 3)
  val collectAllParN          = ParNRenamer("collectAllPar", 2)
  val collectAllSuccessesParN = ParNRenamer("collectAllSuccessPar", 2)
  val collectAllWithParN      = ParNRenamer("collectAllWithPar", 3)
  val reduceAllParN           = ParNRenamer("reduceAllPar", 3)
  val partitionParN           = ParNRenamer("partitionPar", 3)
  val mergeAllParN            = ParNRenamer("mergeAllPar", 4)
  // TODO: Fill out remaining ParN stuff

  // TODO: rename all anyGen things (anyString ->  @deprecated("use string", "2.0.0"))
  override def fix(implicit doc: SemanticDocument): Patch =
    doc.tree.collect {
      case ZIORenames.Matcher(patch)       => patch
      case ZManagedRenames.Matcher(patch)  => patch
      case UniversalRenames.Matcher(patch) => patch

      // Replace >>= with flatMap. For some reason, this doesn't work with the
      // technique used above.
      case t @ q"$lhs >>= $rhs" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs flatMap $rhs")
      case t @ q"$lhs.>>=($rhs)" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs.flatMap($rhs)")

      case t @ q"$lhs.collectAllParN($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllPar($as).withParallelism($n)")

      case t @ q"implicit val runtime: Runtime[ZEnv] = $rhs" =>
        Patch.removeTokens(t.tokens)

      case t @ q"$lhs.collectAllParN_($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllParDiscard($as).withParallelism($n)")
      case t @ q"$lhs.collectAllParNDiscard($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllParDiscard($as).withParallelism($n)")

      case foreachParN.Matcher(patch)             => patch
      case collectAllParN.Matcher(patch)          => patch
      case collectAllSuccessesParN.Matcher(patch) => patch
      case collectAllWithParN.Matcher(patch)      => patch
      case partitionParN.Matcher(patch)           => patch
      case reduceAllParN.Matcher(patch)           => patch
      case mergeAllParN.Matcher(patch)            => patch

      case t @ q"import zio.blocking._" =>
        Patch.removeTokens(t.tokens)

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
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newRandom) +
          Patch.replaceTree(unwindSelect(t), s"Has[Random]")

      case t @ Random_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newRandom) +
          Patch.replaceTree(unwindSelect(t), s"Has[Random]")

      case t @ Console_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newConsole) +
          Patch.replaceTree(unwindSelect(t), s"Has[Console]")

      case t @ System_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newSystem) +
          Patch.replaceTree(unwindSelect(t), s"Has[System]")

      case t @ Clock_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newClock) +
          Patch.replaceTree(unwindSelect(t), s"Has[Clock]")

      case t @ Test_Clock_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newTestClock) +
          Patch.replaceTree(unwindSelect(t), s"Has[TestClock]")

      case t @ Sized_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newSized) +
          Patch.replaceTree(unwindSelect(t), s"Has[Sized]")

      case t @ TestService_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newTestConfig) +
          Patch.replaceTree(unwindSelect(t), s"Has[TestConfig]")

      case t @ Live_Old_Exact(Name(_)) =>
        Patch.addGlobalImport(hasImport) +
          Patch.addGlobalImport(newLive) +
          Patch.replaceTree(unwindSelect(t), s"Has[Live]")

      case t @ ImporteeNameOrRename(Random_Old(_) | Clock_Old(_) | Console_Old(_) | System_Old(_) | Sized_Old(_) | Live_Old(_) | TestService_Old(_)) =>
        Patch.removeImportee(t)

      case t @ q"import zio.console._" =>
        Patch.replaceTree(t, "") +
          Patch.addGlobalImport(wildcardImport(q"zio.Console"))
    }.asPatch + replaceSymbols

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

final case class ParNRenamer(methodName: String, paramCount: Int) {
  object Matcher {
    def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
      tree match {
        case t @ q"$lhs.$method(...$params)"
            if method.value.startsWith(methodName + "N") && paramCount == params.length =>
          val generatedName =
            if (method.value.endsWith("_") || method.value.endsWith("Discard"))
              s"${methodName}Discard"
            else methodName
          val n          = params.head.head
          val paramLists = params.drop(1).map(_.mkString("(", ", ", ")")).mkString("")
          Some(Patch.replaceTree(t, s"$lhs.$generatedName$paramLists.withParallelism($n)"))

        case t @ q"$lhs.$method[..$types](...$params)"
            if method.value.startsWith(methodName + "N") && paramCount == params.length =>
          val generatedName =
            if (method.value.endsWith("_") || method.value.endsWith("Discard"))
              s"${methodName}Discard"
            else methodName
          val n          = params.head.head
          val paramLists = params.drop(1).map(_.mkString("(", ", ", ")")).mkString("")
          Some(Patch.replaceTree(t, s"$lhs.$generatedName[${types.mkString(", ")}]$paramLists.withParallelism($n)"))

        case _ =>
          None
      }
    //        normalizedRenames.flatMap(_.unapply(tree)).headOption
  }
}
