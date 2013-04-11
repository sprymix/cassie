import java.io.{InputStreamReader, FileInputStream, File}
import java.util.Properties

import scala.collection.JavaConverters._
import scala.util.matching.Regex

import sbt._
import sbt.Keys._
import sbt.Project.Initialize

import com.github.bigtoast.sbtthrift.ThriftPlugin


object CassieBuild extends Build {

  val defaultPublishTo = SettingKey[File]("default-publish-to")

  lazy val root = Project(
    id = "cassie",
    base = file("."),
    settings = rootSettings,
    aggregate = Seq(core, hadoop, serversets, stress)
  )

  lazy val core = Project(
    id = "cassie-core",
    base = file("cassie-core"),
    settings = defaultSettings ++ thriftSettings ++ Seq(
      libraryDependencies ++= Dependencies.core
    )
  )

  lazy val hadoop = Project(
    id = "cassie-hadoop",
    base = file("cassie-hadoop"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.hadoop
    )
  ) dependsOn(core)

  lazy val serversets = Project(
    id = "cassie-serversets",
    base = file("cassie-serversets"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.serversets
    )
  ) dependsOn(core)

  lazy val stress = Project(
    id = "cassie-stress",
    base = file("cassie-stress"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.stress
    )
  ) dependsOn(core)


  // Settings

  loadBuildProperties("project/build-local.properties")

  lazy val buildSettings = Seq(
    organization := "com.twitter",
    version      := "0.25.0-SNAPSHOT",
    scalaVersion := "2.10.1",
    exportJars   := true,
    resolvers    ++= Dependencies.resolvers
  )

  lazy val publishSettings = Seq(
    publishTo    <<= cassiePublishTo
  )

  override lazy val settings =
    super.settings ++
    buildSettings ++
    publishSettings ++
    Seq(
      shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
    )

  lazy val thriftSettings =
    ThriftPlugin.thriftCompileSettings ++
    ThriftPlugin.thriftTestSettings

  lazy val baseSettings = Project.defaultSettings

  lazy val rootSettings = baseSettings ++ Seq(
    defaultPublishTo in ThisBuild <<= crossTarget / "repository"
  )

  lazy val defaultSettings = baseSettings ++ Seq(
    // compile options
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.6", "-deprecation",
                                     "-feature", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6",
                                    "-Xlint:deprecation")
  )

  def cassiePublishTo: Initialize[Option[Resolver]] = {
    (defaultPublishTo, version) { (default, version) =>
      cassiePublishRepository orElse
      Some(Resolver.file("Default Local Repository", default))
    }
  }

  def cassiePublishRepository: Option[Resolver] = {
    val sshRepo = new Regex("""^ssh://(?:(\w*)(?::([^@]*))?@)?([\w\.]+)(?::(\d+))?([/\w]*)$""")

    Option(System.getProperty("cassie.publish.repository", null)) map { url: String =>
      url match {
        case sshRepo(user, password, host, port, path) =>
          Resolver.ssh(name = "Configured Cassie Publish Repository (ssh)",
                       hostname = host, port = port.toInt, basePath = path) as
                       (user = user, password = password) withPermissions("0644")
        case _ =>
          // Assume Maven URL
          "Configured Cassie Publish Repository (maven)" at url
      }
    }
  }

  def loadBuildProperties(fileName: String) = {
    val file = new File(fileName)
    if (file.exists()) {
      println("Loading build properties from " + fileName + "")
      val in = new InputStreamReader(new FileInputStream(file), "UTF-8")
      val props = new Properties
      props.load(in)
      in.close()
      sys.props ++ props.asScala
    }
  }
}


object Dependencies {
  val resolvers = Seq(
    "Twitter's Repository" at "http://maven.twttr.com/"
  )

  val slf4jVersion = "1.6.6"
  val finagleVersion = "6.3.0"
  val utilVersion = "6.3.0"

  object Compile {
    val finagleCore   = "com.twitter" %% "finagle-core" % finagleVersion
    val finagleThrift = "com.twitter" %% "finagle-thrift" % finagleVersion
    val finagleSrvsets = "com.twitter" %% "finagle-serversets" % finagleVersion
    val finagleOstrich = "com.twitter" %% "finagle-ostrich4" % finagleVersion
    val finagleStress = "com.twitter" %% "finagle-stress" % finagleVersion

    val utilCore      = "com.twitter" %% "util" % utilVersion
    val utilLogging   = "com.twitter" %% "util-logging" % utilVersion

    val slf4jApi      = "org.slf4j" % "slf4j-api"   % slf4jVersion intransitive()
    val slf4jBindings = "org.slf4j" % "slf4j-jdk14" % slf4jVersion intransitive()
    val slf4jNop      = "org.slf4j" %  "slf4j-nop"  % slf4jVersion % "provided"

    val codecs        = "commons-codec" % "commons-codec" % "1.5"

    val apacheHadoop  = "org.apache.hadoop" % "hadoop-core" % "0.20.2"

    object Test {
      val scalaTest =      "org.scalatest"          %% "scalatest"        % "1.9.1"  % "test"
      val mockito =        "org.mockito"             % "mockito-all"      % "1.8.5"  % "test"
      val junitInterface = "com.novocode"            % "junit-interface"  % "0.9"    % "test->default"
      val scalaCheck =     "org.scalacheck"         %% "scalacheck"       % "1.10.1" % "test"
    }
  }

  import Compile._

  val core = Seq(
    finagleCore, finagleThrift, utilCore, utilLogging, codecs,
    Test.scalaTest, Test.mockito, Test.junitInterface, Test.scalaCheck, slf4jApi, slf4jBindings
  )

  val hadoop = Seq(
    utilCore, apacheHadoop,
    Test.scalaTest
  )

  val serversets = Seq(
    finagleSrvsets
  )

  val stress = Seq(
    finagleOstrich, finagleStress
  )
}
