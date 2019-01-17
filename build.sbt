
name := "spotifydemo"
 
version := "1.0" 
      
lazy val `spotifydemo` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
      
scalaVersion := "2.12.2"

libraryDependencies ++= Seq( ehcache ,specs2 % Test , guice )
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
libraryDependencies += "com.typesafe.play" %% "anorm" % "2.6.0-M1"
libraryDependencies ++= Seq(
  guice,
  ws,
  jdbc,
  "org.postgresql" % "postgresql" % "42.2.2",
  "com.typesafe.play" %% "play-json" % "2.6.0")

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

      