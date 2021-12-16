package fix

import scalafix.v1._

import scala.annotation.tailrec
import scala.meta._

class Zio2Upgrade extends SemanticRule("Zio2Upgrade") {

  val renames =
    Map(
      "accessM"                -> "accessZIO",
      "asEC"                   -> "asExecutionContext",
      "bimap"                  -> "mapBoth",
      "bracket"                -> "acquireReleaseWith",
      "bracketExit"            -> "acquireReleaseExitWith",
      "bracketExit_"           -> "acquireReleaseExit",
      "bracketOnError"         -> "acquireReleaseOnErrorWith",
      "bracketOnError_"        -> "acquireReleaseOnError",
      "bracket_"               -> "acquireRelease",
      "bracket_"               -> "acquireRelease",
      "checkM"                 -> "check",
      "checkNM"                -> "checkN",
      "checkAllM"              -> "checkAll",
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
      "interrupted"            -> "isInterrupted",
      "lockExecutionContext"   -> "onExecutionContext", // Hard to test, because this only existed in a non-deprecated state in an earlier milestone
      "loop_"                  -> "loopDiscard",
      "makeReserve"            -> "fromReservationZIO",
      "mapConcatM"             -> "mapConcatZIO",
      "mapEffect"              -> "mapAttempt",
      "mapM"                   -> "mapZIO",
      "mapMPar"                -> "mapZIOPar",
      "mapMParUnordered"       -> "mapZIOParUnordered",
      "on"                     -> "onExecutionContext",
      "optional"               -> "unsome",
      "unoption"               -> "unsome",
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
      "whenM"                  -> "whenZIO",
    )

  lazy val scopes = List(
    "zio.test.package",
    "zio.test.DefaultRunnableSpec",
    "zio.Exit",
    "zio.ZIO",
    "zio.Cause",
    "zio.Chunk",
    "zio.Executor",
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
    "zio.internal.Executor",
    "zio.stream.ZSink",
    "zio.stream.ZStream",
    "zio.stream.ZTransducer",
    "zio.stream.ZChannel",
    "zio.stream.Take",
    "zio.stream.ZPipeline",
    "zio.test.TestFailure",
    "zio.Runtime"
  )
  /*
    TODO
       Sink renames:
         count/sum => run(ZSink.count)/run(ZSink.Sum)
         serviceWithStream
       Semantic:
         transducer is gone; replaced with Pipeline
          -Sink might be good
          ZTransducer.utf32BEDDecode into ZPipeline variations
        Try to convert these classes:
          ZStreamSpec, ZSinkSpec
   */

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
    Map(
      "run" -> "exit"
    )
  )

  val STMRenames = Renames(
    List("zio.stm.ZSTM", "zio.stm.STM"),
    Map(
      "collectAll_"   -> "collectAllDiscard",
      "foldM"         -> "foldSTM",
      "foreach_"      -> "foreachDiscard",
      "fromFunction"  -> "access",
      "fromFunctionM" -> "accessSTM",
      "ifM"           -> "ifSTM",
      "loop_"         -> "loopDiscard",
      "partial"       -> "attempt",
      "replicateM"    -> "replicateSTM",
      "replicateM_"   -> "replicateSTMDiscard",
      "require"       -> "someOrFail",
      "unlessM"       -> "unlessSTM",
      "whenCaseM"     -> "whenCaseSTM",
      "whenM"         -> "whenSTM"
    )
  )
  
  val StreamRenames = Renames(
    List("zio.stream.ZStream"),
    Map(
      "access" -> "environment",
      "accessM" -> "environmentWithZIO",
      "accessZIO" -> "environmentWithZIO", // RC only
      "dropWhileM" -> "dropWhileZIO", // RC only, cannot test
      "findM" -> "findZIO", // RC only, cannot test
      "fold"         -> "runFold",
      "foldM"         -> "runFoldZIO", // RC only
      "foldManaged" -> "runFoldManaged",
      "foldManagedM"         -> "runFoldManagedZIO",
      "foldManagedZIO" -> "runFoldManagedZIO",
      "foldWhile" -> "runFoldWhile",
      "foldWhileM" -> "runFoldWhileZIO",
      "foldWhileManagedM" -> "runFoldWhileManagedZIO",
      "foldWhileManagedZIO" -> "runFoldWhileManagedZIO", // RC only
      "foldWhileZIO" -> "runFoldWhileZIO", // RC only
      "foldWhileManaged" -> "runFoldWhileManaged",
      "foldZIO" -> "runFoldZIO", // RC only
      "foreachChunk" -> "runForeachChunk",
      "foreachChunkManaged" -> "runForeachChunkManaged",
      "foreachManaged" -> "runForeachManaged",
      "foreachWhile" -> "runForeachWhile",
      "foreachWhileManaged" -> "runForeachWhileManaged",
      "mapM"          -> "mapZIO",
      "collectWhileM" -> "collectWhileZIO",
      "collectUntilM" -> "collectUntilZIO",
      "accessStream" -> "environmentWithStream",
      "runInto" -> "runIntoQueue", // RC only
      "runIntoElementsManaged" -> "runIntoQueueElementsManaged", // RC only
      "runFoldM" -> "runFoldZIO", // RC only
      "runFoldManagedM" -> "runFoldManagedZIO",
      "runFoldWhileM" -> "runFoldWhileZIO", // RC only
      "runFoldWhileManagedM" -> "runFoldWhileManagedZIO", // RC only
      "chunkN" -> "rechunk", // RC only
      "intoHub" -> "runIntoHub",
      "intoHubManaged" -> "runIntoHubManaged",
      "intoManaged" -> "runIntoQueueManaged",
      "runIntoManaged" -> "runIntoQueueManaged", // RC only
      "intoQueue" -> "runIntoQueue",
      "intoQueueManaged" -> "runIntoQueueManaged", // RC only
      "lock" -> "onExecutor",
      "mapAccumM" -> "mapAccumZIO",
      "mapChunksM" -> "mapChunksZIO",
      "mapConcatChunkM" -> "mapConcatChunkZIO",
      "mapMPartitioned" -> "mapZIOPartitioned", 
      "scanM" -> "scanZIO",
      "scanReduceM" -> "scanReduceZIO",
      "takeUntilM" -> "takeUntilZIO",
      "throttleEnforceM" -> "throttleEnforceZIO",
      "throttleShapeM" -> "throttleShapeZIO",
      "timeoutError" -> "timeoutFail",
      "timeoutErrorCause" -> "timeoutFailCause",
      "timeoutHalt" -> "timeoutFailCause", // RC only
      "fromInputStreamEffect" -> "fromInputStreamZIO",
      "fromIteratorEffect" -> "fromIteratorZIO",
      "fromJavaIteratorEffect" -> "fromJavaIteratorZIO",
      "fromJavaIteratorTotal" -> "fromJavaIteratorSucceed",
      "halt" -> "failCause",
      "repeatEffectChunkOption" -> "repeatZIOChunkOption",
      "repeatWith" -> "repeatWithSchedule",
      "unfoldChunkM" -> "unfoldChunkZIO",
      "whenCaseM" -> "whenCaseZIO",
      // TODO Look at restructuring calls to ZStream.cross with the method version
      // TODO Look into fromBlocking* refactors
      
    )
  )

  val ScheduleRenames = Renames(
    List("zio.Schedule", "zio.stm.STM"),
    Map(
      "addDelayM"     -> "addDelayZIO",
      "checkM"        -> "checkZIO",
      "contramapM"    -> "contramapZIO",
      "delayedM"      -> "delayedZIO",
      "dimapM"        -> "dimapZIO",
      "dropWhileM" -> "dropWhileZIO", // RC only, cannot test
      "findM" -> "findZIO", // RC only, cannot test
      "foldM"         -> "foldZIO",
      "mapM"          -> "mapZIO",
      "modifyDelayM"  -> "modifyDelayZIO",
      "reconsiderM"   -> "reconsiderZIO",
      "untilInputM"   -> "untilInputZIO",
      "untilOutputM"  -> "untilOutputZIO",
      "whileInputM"   -> "whileInputZIO",
      "whileOutputM"  -> "whileOutputZIO",
      "collectWhileM" -> "collectWhileZIO",
      "collectUntilM" -> "collectUntilZIO",
      "recurWhileM"   -> "recureWhileZIO",
      "recurUntilM"   -> "recureUntilZIO"
    )
  )

  val ZManagedRenames = Renames(
    List("zio.ZManaged", "zio.Managed"),
    Map(
      "collectM"                  -> "collectManaged",
      "foldCauseM"                -> "foldCauseManaged",
      "foldM"                     -> "foldManaged",
      "fromEffectUninterruptible" -> "fromZIOUninterruptible",
      "fromFunction"              -> "access",
      "fromFunctionM"             -> "accessManaged",
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

  val FiberId_Old = SymbolMatcher.normalized("zio/Fiber.Id#")

  val Blocking_Old_Exact = SymbolMatcher.exact("zio/blocking/package.Blocking#")

  val FiberId_Old_Exact = SymbolMatcher.exact("zio/Fiber.Id#")

  val hasNormalized = SymbolMatcher.normalized("zio/Has#")

  val newFiberId = Symbol("zio/FiberId#")

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
    "zio.test.Gen.anyInt"                     -> "zio.test.Gen.int",
    "zio.test.Gen.anyString"                  -> "zio.test.Gen.string",
    "zio.test.Gen.anyUnicodeChar"             -> "zio.test.Gen.unicodeChar",
    "zio.test.Gen.anyASCIIChar"               -> "zio.test.Gen.asciiChar",
    "zio.test.Gen.anyByte"                    -> "zio.test.Gen.byte",
    "zio.test.Gen.anyChar"                    -> "zio.test.Gen.char",
    "zio.test.Gen.anyDouble"                  -> "zio.test.Gen.double",
    "zio.test.Gen.anyFloat"                   -> "zio.test.Gen.float",
    "zio.test.Gen.anyHexChar"                 -> "zio.test.Gen.hexChar",
    "zio.test.Gen.anyLong"                    -> "zio.test.Gen.long",
    "zio.test.Gen.anyLowerHexChar"            -> "zio.test.Gen.hexCharLower",
    "zio.test.Gen.anyShort"                   -> "zio.test.Gen.short",
    "zio.test.Gen.anyUpperHexChar"            -> "zio.test.Gen.hexCharUpper",
    "zio.test.Gen.anyASCIIString"             -> "zio.test.Gen.asciiString",
    "zio.test.Gen.anyUUID"                    -> "zio.test.Gen.uuid",
    "zio.test.TimeVariants.anyDayOfWeek"      -> "zio.test.Gen.dayOfWeek",
    "zio.test.TimeVariants.anyFiniteDuration" -> "zio.test.Gen.finiteDuration",
    "zio.test.TimeVariants.anyLocalDate"      -> "zio.test.Gen.localDate",
    "zio.test.TimeVariants.anyLocalTime"      -> "zio.test.Gen.localTime",
    "zio.test.TimeVariants.anyLocalDateTime"  -> "zio.test.Gen.localDateTime",
    "zio.test.TimeVariants.anyMonth"          -> "zio.test.Gen.month",
    "zio.test.TimeVariants.anyMonthDay"       -> "zio.test.Gen.monthDay",
    "zio.test.TimeVariants.anyOffsetDateTime" -> "zio.test.Gen.offsetDateTime",
    "zio.test.TimeVariants.anyOffsetTime"     -> "zio.test.Gen.offsetTime",
    "zio.test.TimeVariants.anyPeriod"         -> "zio.test.Gen.period",
    "zio.test.TimeVariants.anyYear"           -> "zio.test.Gen.year",
    "zio.test.TimeVariants.anyYearMonth"      -> "zio.test.Gen.yearMonth",
    "zio.test.TimeVariants.anyZonedDateTime"  -> "zio.test.Gen.zonedDateTime",
    "zio.test.TimeVariants.anyZoneOffset"     -> "zio.test.Gen.zoneOffset",
    "zio.test.TimeVariants.anyZoneId"         -> "zio.test.Gen.zoneId",
    // App
    "zio.App"           -> "zio.ZIOAppDefault",
    "zio.Executor.asEC" -> "zio.Executor.asExecutionContext"
  )

  val foreachParN             = ParNRenamer("foreachPar", 3)
  val collectAllParN          = ParNRenamer("collectAllPar", 2)
  val collectAllSuccessesParN = ParNRenamer("collectAllSuccessPar", 2)
  val collectAllWithParN      = ParNRenamer("collectAllWithPar", 3)
  val reduceAllParN           = ParNRenamer("reduceAllPar", 3)
  val partitionParN           = ParNRenamer("partitionPar", 3)
  val mergeAllParN            = ParNRenamer("mergeAllPar", 4)

  object BuiltInServiceFixer { // TODO Handle all built-in services?

    object ImporteeRenamer {
      def importeeRenames(implicit sdoc: SemanticDocument): PartialFunction[Tree, Option[Patch]] =
        List(
          randomMigrator,
          systemMigrator,
          consoleMigrator,
          testConfigMigrator,
          testSystemMigrator,
          testAnnotationsMigrator,
          testConsoleMigrator,
          testRandomMigrator,
          testLoggerMigrator,
          testClockMigrator,
          clockMigrator,
          sizedMigrator,
          testLiveMigrator
        ).foldLeft(List[SymbolMatcher](hasNormalized)) { case (serviceMatchers, serviceMigrator) =>
          serviceMatchers ++ List(serviceMigrator.normalizedOld, serviceMigrator.normalizedOldService)
        }.map[PartialFunction[Tree, Patch]](symbolMatcher => { case t @ ImporteeNameOrRename(symbolMatcher(_)) =>
          Patch.removeImportee(t)
        }).foldLeft[PartialFunction[Tree, Option[Patch]]] { case (_: Tree) => None } { case (totalPatch, nextPatch) =>
          (tree: Tree) => nextPatch.lift(tree).orElse(totalPatch(tree))
        }

      def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] =
        importeeRenames.apply(tree)
    }

    private val testLiveMigrator =
      ServiceMigrator(name = "Live", oldPath = "zio/test/environment/package.", newPath = "zio/test/")

    private val sizedMigrator =
      ServiceMigrator(name = "Sized", oldPath = "zio/test/package.", newPath = "zio/test/")

    private val clockMigrator =
      ServiceMigrator(name = "Clock", oldPath = "zio/clock/environment/package.", newPath = "zio/")

    private val testClockMigrator =
      ServiceMigrator(name = "TestClock", oldPath = "zio/test/environment/package.", newPath = "zio/test/")

    private val testConsoleMigrator =
      ServiceMigrator(name = "TestConsole", oldPath = "zio/test/environment/package.", newPath = "zio/test/")

    val testRandomMigrator =
      ServiceMigrator(name = "TestRandom", oldPath = "zio/test/environment/package.", newPath = "zio/test/")

    private val testLoggerMigrator =
      ServiceMigrator(name = "TestLogger", oldPath = "zio/test/package.", newPath = "zio/test/")

    private val testAnnotationsMigrator =
      ServiceMigrator(name = "Annotations", oldPath = "zio/test/package.", newPath = "zio/test/")

    private val testSystemMigrator =
      ServiceMigrator(name = "TestSystem", oldPath = "zio/test/environment/package.", newPath = "zio/test/")

    private val testConfigMigrator =
      ServiceMigrator(name = "TestConfig", oldPath = "zio/test/package.", newPath = "zio/test/")

    private val consoleMigrator =
      ServiceMigrator(name = "Console", oldPath = "zio/console/package.", newPath = "zio/")

    case class ServiceMigrator(
      oldExact: SymbolMatcher,
      oldService: SymbolMatcher,
      newSymbol: Symbol,
      plainName: String,
      normalizedOld: SymbolMatcher,
      normalizedOldService: SymbolMatcher
    ) {
      def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] = {
        val partial: PartialFunction[Tree, Patch] = {
          case t @ oldService(Name(_)) =>
            Patch.replaceTree(unwindSelect(t), plainName) +
              Patch.addGlobalImport(newSymbol)

          case t @ oldExact(Name(_)) =>
            Patch.addGlobalImport(newSymbol) +
              Patch.replaceTree(unwindSelect(t), plainName)
        }
        partial.lift(tree)
      }
    }
    object ServiceMigrator {

      def apply(name: String, oldPath: String, newPath: String): ServiceMigrator =
        ServiceMigrator(
          SymbolMatcher.exact(oldPath + name + "#"),
          SymbolMatcher.exact(oldPath + name + ".Service#"),
          Symbol(newPath + name + "#"),
          name,
          SymbolMatcher.normalized(oldPath + name + "#"),
          SymbolMatcher.normalized(oldPath + name + ".Service#")
        )
    }

    private val randomMigrator =
      ServiceMigrator(name = "Random", oldPath = "zio/random/package.", newPath = "zio/")

    private val systemMigrator =
      ServiceMigrator(name = "System", oldPath = "zio/system/package.", newPath = "zio/")

    def unapply(tree: Tree)(implicit sdoc: SemanticDocument): Option[Patch] = {
      val partial: PartialFunction[Tree, Patch] = {
        case t @ Type.Apply(tpe: Type, args: List[Type]) if hasNormalized.matches(tpe.symbol) =>
          val builtInServices: Seq[SymbolMatcher] =
            List(
              randomMigrator,
              systemMigrator,
              consoleMigrator,
              testConfigMigrator,
              testSystemMigrator,
              testAnnotationsMigrator,
              testConsoleMigrator,
              testRandomMigrator,
              testLoggerMigrator,
              testClockMigrator,
              clockMigrator,
              sizedMigrator,
              testLiveMigrator
            ).foldLeft(List.empty[SymbolMatcher]) { case (serviceMatchers, serviceMigrator) =>
              serviceMatchers ++ List(serviceMigrator.oldService, serviceMigrator.oldExact)
            }

          if (builtInServices.exists(_.matches(args.head)))
            Patch.replaceTree(t, "")
          else
            Patch.replaceTree(t, args.head.toString)

        case randomMigrator(patch)          => patch
        case systemMigrator(patch)          => patch
        case consoleMigrator(patch)         => patch
        case testConfigMigrator(patch)      => patch
        case testSystemMigrator(patch)      => patch
        case testAnnotationsMigrator(patch) => patch
        case testConsoleMigrator(patch)     => patch
        case testRandomMigrator(patch)      => patch
        case testLoggerMigrator(patch)      => patch
        case testClockMigrator(patch)       => patch
        case sizedMigrator(patch)           => patch
        case testLiveMigrator(patch)        => patch

        case t @ q"zio.random.Random" =>
          Patch.replaceTree(t, "zio.Random")

        case t @ q"import zio.duration._" =>
          Patch.replaceTree(t, "") +
            Patch.addGlobalImport(wildcardImport(q"zio"))

        case t @ q"import zio.system" =>
          Patch.replaceTree(t, "") + Patch.addGlobalImport(systemMigrator.newSymbol)

      }
      partial.lift(tree)
    }

  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    Zio2ZIOSpec.fix +
    doc.tree.collect {
      case BuiltInServiceFixer.ImporteeRenamer(patch) => patch

      case ZIORenames.Matcher(patch)       => patch
      case ZManagedRenames.Matcher(patch)  => patch
      case STMRenames.Matcher(patch)       => patch
      case ScheduleRenames.Matcher(patch)  => patch
      case StreamRenames.Matcher(patch)  => patch
      case UniversalRenames.Matcher(patch) => patch

      case BuiltInServiceFixer(patch) => patch

      // Replace >>= with flatMap. For some reason, this doesn't work with the
      // technique used above.
      case t @ q"$lhs >>= $rhs" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs flatMap $rhs")
      case t @ q"$lhs.>>=($rhs)" if lhs.symbol.owner.value.startsWith("zio") =>
        Patch.replaceTree(t, s"$lhs.flatMap($rhs)")

      case t @ q"$lhs.collectAllParN($n)($as)" =>
        Patch.replaceTree(t, s"$lhs.collectAllPar($as).withParallelism($n)")

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

      case t @ q"import zio.blocking.Blocking" =>
        Patch.removeTokens(t.tokens)

      case t @ Blocking_Old_Exact(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), s"Any")

      case t @ FiberId_Old_Exact(Name(_)) =>
        Patch.replaceTree(unwindSelect(t), "FiberId") +
          Patch.addGlobalImport(newFiberId)

      case t @ q"import zio.console._" =>
        Patch.replaceTree(t, "") +
          Patch.addGlobalImport(wildcardImport(q"zio.Console"))

      case t @ q"import zio.test.environment._" =>
        Patch.removeTokens(t.tokens)

      case t @ q"Fiber.Id" =>
        Patch.replaceTree(t, "FiberId") +
          Patch.addGlobalImport(Symbol("zio/FiberId#"))

      // TODO Safe to do for many similar types?
      case t @ q"import zio.duration.Duration" =>
        Patch.replaceTree(t, "import zio.Duration")

      case t @ q"zio.duration.Duration" =>
        Patch.replaceTree(t, "zio.Duration")

      case t @ q"import zio.clock.Clock" =>
        Patch.replaceTree(t, "import zio.Clock")

      case t @ q"zio.internal.Executor" =>
        Patch.replaceTree(t, "zio.Executor")

      case t @ q"Platform.fromExecutor" =>
        Patch.replaceTree(t, "RuntimeConfig.fromExecutor")

      case t @ q"zio.internal.Platform" =>
        Patch.replaceTree(t, "zio.RuntimeConfig")

      case t @ q"zio.internal.Tracing" =>
        Patch.replaceTree(t, "zio.internal.tracing.Tracing")

      case t @ q"import zio.internal.Tracing" =>
        Patch.replaceTree(t, "import zio.internal.tracing.Tracing")

      case t @ ImporteeNameOrRename(FiberId_Old(_)) => Patch.removeImportee(t)

    }.asPatch + replaceSymbols
  }

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
