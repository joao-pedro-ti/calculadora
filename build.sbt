ThisBuild / scalaVersion := "2.13.14"
ThisBuild / version      := "1.0.0"

// Front-end: Scala.js + Laminar
lazy val frontend = (project in file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "calculadora-frontend",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
    libraryDependencies += "com.raquo" %%% "laminar" % "17.0.0"
  )

// Backend: Cask (HTTP) + driver oficial do MongoDB
lazy val backend = (project in file("backend"))
  .settings(
    name := "calculadora-backend",
    libraryDependencies ++= Seq(
      "com.lihaoyi"     %% "cask"                % "0.9.2",
      "org.mongodb.scala" %% "mongo-scala-driver" % "5.1.0",
      "org.slf4j"        % "slf4j-nop"           % "2.0.13"
    ),
    // Compila o front-end (JS otimizado) e embute dentro do backend
    Compile / resourceGenerators += Def.task {
      (frontend / Compile / fullLinkJS).value
      val dir = (frontend / Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
      val out = (Compile / resourceManaged).value / "public" / "main.js"
      IO.copyFile(dir / "main.js", out)
      Seq(out)
    }.taskValue,
    assembly / mainClass := Some("calc.Main"),
    assembly / assemblyJarName := "calculadora.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", "versions", _*) => MergeStrategy.first
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "reference.conf"                     => MergeStrategy.concat
      case _                                    => MergeStrategy.first
    }
  )

lazy val root = (project in file("."))
  .aggregate(frontend, backend)
  .settings(publish / skip := true)
