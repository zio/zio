// package zio.test.mock

// import zio.test.mock.MockSystem.Data
// import zio.TestRuntime

// class SystemSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

//   def is = "SystemSpec".title ^ s2"""
//     Fetch an environment variable and check that:
//       If it exists, return a reasonable value                         $env1
//       If it does not exist, return None                               $env2
//       If it is set, return the set value                              $env3
//       If it is cleared, return None                                   $env4

//     Fetch a VM property and check that:
//       If it exists, return a reasonable value                         $prop1
//       If it does not exist, return None                               $prop2
//       If it is set, return the set value                              $prop3
//       If it is cleared, return None                                   $prop4

//     Fetch the system's line separator and check that:
//       It is identical to Data.lineSeparator                           $lineSep1
//       If it is set, return the set value                              $lineSep2
//   """

//   def env1 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data(envs = Map("k1" -> "v1")))
//         env        <- mockSystem.env("k1")
//       } yield env must_=== Option("v1")
//     )

//   def env2 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data())
//         env        <- mockSystem.env("k1")
//       } yield env must_=== Option.empty
//     )

//   def env3 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data())
//         _          <- mockSystem.putEnv("k1", "v1")
//         env        <- mockSystem.env("k1")
//       } yield env must_=== Option("v1")
//     )

//   def env4 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data(envs = Map("k1" -> "v1")))
//         _          <- mockSystem.clearEnv("k1")
//         env        <- mockSystem.env("k1")
//       } yield env must_=== None
//     )

//   def prop1 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data(properties = Map("k1" -> "v1")))
//         prop       <- mockSystem.property("k1")
//       } yield prop must_=== Option("v1")
//     )

//   def prop2 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data())
//         prop       <- mockSystem.property("k1")
//       } yield prop must_=== Option.empty
//     )

//   def prop3 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data())
//         _          <- mockSystem.putProperty("k1", "v1")
//         prop       <- mockSystem.property("k1")
//       } yield prop must_=== Option("v1")
//     )

//   def prop4 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data(properties = Map("k1" -> "v1")))
//         _          <- mockSystem.clearProperty("k1")
//         prop       <- mockSystem.property(("k1"))
//       } yield prop must_=== None
//     )

//   def lineSep1 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data(lineSeparator = ","))
//         lineSep    <- mockSystem.lineSeparator
//       } yield lineSep must_=== ","
//     )

//   def lineSep2 =
//     unsafeRun(
//       for {
//         mockSystem <- MockSystem.makeMock(Data())
//         _          <- mockSystem.setLineSeparator(",")
//         lineSep    <- mockSystem.lineSeparator
//       } yield lineSep must_=== ","
//     )
// }
