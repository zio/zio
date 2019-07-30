// package zio.test.mock

// import java.util.concurrent.TimeUnit

// import zio._
// import zio.duration._

// class EnvironmentSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

//   def is = "EnvironmentSpec".title ^ s2"""
//       Clock:
//         returns time when it is set                     $currentTime
//       Console:
//         writes line to output                           $putStrLn
//         reads line from input                           $getStrLn
//       Random:
//         returns next integer when data is fed           $nextInt
//       System:
//         returns an environment variable when it is set  $env
//         returns a property when it is set               $property
//         returns the line separator when it is set       $lineSeparator
//   """

//   def withEnvironment[E, A](zio: ZIO[MockEnvironment, E, A]): A =
//     unsafeRun(mockEnvironmentManaged.use[Any, E, A](r => zio.provide(r)))

//   def currentTime =
//     withEnvironment {
//       for {
//         _    <- MockClock.setTime(1.millis)
//         time <- clock.currentTime(TimeUnit.MILLISECONDS)
//       } yield time must_=== 1L
//     }

//   def putStrLn =
//     withEnvironment {
//       for {
//         _      <- console.putStrLn("First line")
//         _      <- console.putStrLn("Second line")
//         output <- MockConsole.output
//       } yield output must_=== Vector("First line\n", "Second line\n")
//     }

//   def getStrLn =
//     withEnvironment {
//       for {
//         _      <- MockConsole.feedLines("Input 1", "Input 2")
//         input1 <- console.getStrLn
//         input2 <- console.getStrLn
//       } yield (input1 must_=== "Input 1") and (input2 must_=== "Input 2")
//     }

//   def nextInt =
//     withEnvironment {
//       for {
//         _ <- MockRandom.feedInts(6)
//         n <- random.nextInt
//       } yield n must_=== 6
//     }

//   def env =
//     withEnvironment {
//       for {
//         _   <- MockSystem.putEnv("k1", "v1")
//         env <- system.env("k1")
//       } yield env must_=== Some("v1")
//     }

//   def property =
//     withEnvironment {
//       for {
//         _   <- MockSystem.putProperty("k1", "v1")
//         env <- system.property("k1")
//       } yield env must_=== Some("v1")
//     }

//   def lineSeparator =
//     withEnvironment {
//       for {
//         _       <- MockSystem.setLineSeparator(",")
//         lineSep <- system.lineSeparator
//       } yield lineSep must_=== ","
//     }
// }
