lazy val threadlocalBridge = RootProject(file("threadlocal-bridge"))

lazy val reloadableServices = RootProject(file("reloadable-services"))

lazy val schedule = RootProject(file("schedule"))

lazy val differCompositionalUpdates = RootProject(file("differ-compositional-updates"))

lazy val migrateCatsEffect = RootProject(file("migrate-cats-effect"))

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(threadlocalBridge, reloadableServices, schedule, differCompositionalUpdates, migrateCatsEffect)
