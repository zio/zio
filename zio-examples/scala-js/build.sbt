scalaVersion := "3.3.8"
publish / skip := true

enablePlugins(ScalaJSPlugin)

scalaJSUseMainModuleInitializer := true
scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule))
Compile / fastLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "app"
Compile / fullLinkJS / scalaJSLinkerOutputDirectory := baseDirectory.value / "app"

libraryDependencies ++= Seq(
  "dev.zio"      %%% "zio"          % "2.1.25",
  "dev.zio"      %%% "zio-json"     % "0.7.38",
  "org.scala-js" %%% "scalajs-dom"  % "2.8.1",
  "dev.zio"      %%% "zio-test"     % "2.1.25" % Test,
  "dev.zio"      %%% "zio-test-sbt" % "2.1.25" % Test
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
