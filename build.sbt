name := "3scale-akka"

version := "1.0.1"

scalaVersion := "2.10.3"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT"

libraryDependencies += "xom" % "xom" % "1.2.5"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.0" % "test"