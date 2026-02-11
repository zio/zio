package zio.internal


import zio.test._
import scala.jdk.CollectionConverters._
import zio.test.TestAspect

object FiberSetPropertySpec extends ZIOSpecDefault {

  // A simple model of the expected behavior (standard Set)
  // We use String or Int to avoid identity issues for the model,
  // but wrap them in Objects for FiberSet to use identityHashCode.

  case class Wrapper(id: Int)

  val genCommand: Gen[Any, Command] = Gen.oneOf(
    Gen.int(1, 100).map(i => Add(i)),
    Gen.int(1, 100).map(i => Remove(i)),
    Gen.const(GC)
  )

  sealed trait Command
  case class Add(id: Int)    extends Command
  case class Remove(id: Int) extends Command
  case object GC             extends Command

  def spec = suite("FiberSetPropertySpec")(
    test("sequential consistency with Set model") {
      check(Gen.listOf(genCommand)) { commands =>
        val set   = FiberSet.make[Wrapper]()
        var model = Set.empty[Int]
        // maintain map of ID -> Wrapper to ensure identity stability for removal
        var registry = Map.empty[Int, Wrapper]

        def getWrapper(id: Int): Wrapper =
          registry.getOrElse(
            id, {
              val w = Wrapper(id)
              registry = registry.updated(id, w)
              w
            }
          )

        // Apply commands
        commands.foreach {
          case Add(id) =>
            val w = getWrapper(id)
            set.add(w)
            model = model + id
          case Remove(id) =>
            // Only remove if we have a wrapper (otherwise it wouldn't be in set anyway)
            registry.get(id).foreach { w =>
              set.remove(w)
            }
            model = model - id
          case GC =>
            set.gc()
        }

        // Verify size (approximate due to GC)
        // FiberSet size should be >= model size (Cold items might stick around until GC)
        // actually, logic:
        // - if in model, MUST be in FiberSet (unless weak ref cleared, but we keep strong refs in registry)
        // - if NOT in model, COULD be in FiberSet (dead/cold)

        val fiberSetItems = set.iterator.asScala.toList
        val fiberSetIds   = fiberSetItems.map(_.id).toSet

        // Check containment: Model is subset of FiberSet
        // Every item in Model MUST be in FiberSet because we hold strong refs in 'registry'
        val missingFromSet = model -- fiberSetIds

        assertTrue(missingFromSet.isEmpty)
      }
    } @@ TestAspect.samples(2500), // Run 2500 unique test scenarios to exceed 500+ requirement

    test("no data loss under churn") {
      check(Gen.listOfN(1000)(genCommand)) { commands =>
        val set      = FiberSet.make[Wrapper]()
        var registry = Map.empty[Int, Wrapper]
        var active   = Set.empty[Int]

        commands.foreach {
          case Add(id) =>
            val w = registry.getOrElse(id, Wrapper(id))
            registry += (id -> w)
            set.add(w)
            active += id
          case Remove(id) =>
            registry.get(id).foreach(set.remove)
            active -= id
          case GC => set.gc()
        }

        val currentIds = set.iterator.asScala.toList.map(_.id).toSet
        assertTrue((active -- currentIds).isEmpty)
      }
    } @@ TestAspect.samples(1000)
  )
}
