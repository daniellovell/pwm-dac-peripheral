name := "pwmdac"
organization := "edu.berkeley.cs"
version := "0.0.1"

scalaVersion := "2.12.12"

scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11", "-language:reflectiveCalls")
libraryDependencies ++= Seq("edu.berkeley.cs" %% "chisel3" % "3.4.1" ,
                            "edu.berkeley.cs" %% "rocketchip" % "1.2.+",
                            "edu.berkeley.cs" %% "chiseltest" % "0.3-SNAPSHOT",
                            "edu.berkeley.cs" %% "testchipip" % "1.0-031419-SNAPSHOT",
                            "org.scalatest" %% "scalatest" % "3.2.+" % "test",
                            "edu.berkeley.cs" %% "dsptools" % "1.4.2",
                            "org.scalanlp" %% "breeze-viz" % "1.1")
resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal)