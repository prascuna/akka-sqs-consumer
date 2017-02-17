name := """akka-sqs-consumer"""

version := "1.0"


lazy val commonSettings = Seq(
  organization := "akka-sqs-consumer",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.12.1", "2.11.8"),
  libraryDependencies := Seq(
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "org.mockito" % "mockito-core" % "2.7.1" % "test"
  )
)


lazy val akkaSqsConsumer = (project in file("akka-sqs-consumer"))
  .settings(commonSettings: _*)
  .settings {
    name := "akka-sqs-consumer"
    libraryDependencies ++= {
      val awsJavaSdkVersion = "1.11.86"
      val akkaVersion = "2.4.17"
      Seq(
        "com.amazonaws" % "aws-java-sdk-sqs" % awsJavaSdkVersion,
        "com.amazonaws" % "aws-java-sdk-core" % awsJavaSdkVersion,
        "com.typesafe.akka" %% "akka-actor" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
      )
    }
  }

lazy val akkaSqsRedis = (project in file("akka-sqs-redis"))
  .dependsOn(akkaSqsConsumer)
  .settings(commonSettings: _*)
  .settings {
    name := "akka-sqs-redis"
    libraryDependencies ++= Seq(
      "net.debasishg" %% "redisclient" % "3.3"
    )
  }

lazy val akkaSqsExample = (project in file("akka-sqs-example"))
  .dependsOn(akkaSqsConsumer, akkaSqsRedis)
  .settings(commonSettings)
  .settings{
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "ch.qos.logback" % "logback-classic" % "1.2.1",
      "org.slf4j" % "jcl-over-slf4j" % "1.7.23"
    )
  }

lazy val root = (project in file("."))
  .aggregate(akkaSqsConsumer, akkaSqsRedis, akkaSqsExample)
  .settings(commonSettings: _*)
  .settings {
    publishArtifact := false
  }