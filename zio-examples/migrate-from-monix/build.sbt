scalaVersion   := "3.9.0"
publish / skip := true

libraryDependencies ++= Seq(
  "dev.zio"  %% "zio"         % "2.1.26",
  "dev.zio"  %% "zio-streams" % "2.1.26",
  "dev.zio"  %% "zio-test"    % "2.1.26",
  "io.monix" %% "monix"       % "3.4.1"
)
