addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"                  % "0.14.5")
addSbtPlugin("com.eed3si9n"                      % "sbt-assembly"                  % "2.3.1")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"                 % "0.13.1")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"                    % "0.6.0")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"                % "1.11.2")
addSbtPlugin("com.github.cb372"                  % "sbt-explicit-dependencies"     % "0.3.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings"              % "3.0.2")
addSbtPlugin("com.typesafe"                      % "sbt-mima-plugin"               % "1.1.4")
addSbtPlugin("com.github.sbt"                    % "sbt-header"                    % "5.11.0")
addSbtPlugin("org.portable-scala"                % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala"                % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-js"                      % "sbt-scalajs"                   % "1.20.1")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"                      % "2.8.1")
addSbtPlugin("org.scala-native"                  % "sbt-scala-native"              % "0.5.9")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"                  % "2.5.6")
addSbtPlugin("pl.project13.scala"                % "sbt-jcstress"                  % "0.2.0")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"                       % "0.4.8")

addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.3")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.0.1"

addDependencyTreePlugin
