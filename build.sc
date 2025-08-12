import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.1`
import $ivy.`com.github.lolgab::mill-mima::0.1.1`

import com.github.lolgab.mill.mima.Mima
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.scalalib._
import mill.scalalib.publish._

import java.util.Locale

import scala.util.Properties

trait WindowsAnsiPublishModule extends WindowsAnsiJavaModule with PublishModule with Mima {
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "io.github.alexarchambault.native-terminal",
    url = "https://github.com/alexarchambault/native-terminal",
    licenses = Seq(
      License.`Apache-2.0`,
      License.`GPL-2.0-with-classpath-exception`
    ),
    versionControl = VersionControl.github("alexarchambault", "native-terminal"),
    developers = Seq(
      Developer("alexarchambault", "Alex Archambault", "https://github.com/alexarchambault")
    )
  )
  def publishVersion = T {
    val value = VcsVersion.vcsState().format()
    if (value.contains("-")) {
      val value0 = value.takeWhile(_ != '-')
      val lastDotIdx = value0.lastIndexOf('.')
      if (lastDotIdx < 0) value0 + "-SNAPSHOT"
      else
        value0.drop(lastDotIdx + 1).toIntOption match {
          case Some(lastNum) =>
            val prefix = value0.take(lastDotIdx)
            s"$prefix.${lastNum + 1}-SNAPSHOT"
          case None =>
            value0 + "-SNAPSHOT"
        }
    }
    else value
  }

  def mimaPreviousVersions: T[Seq[String]] = T.input {
    val current = os.proc("git", "describe", "--tags", "--match", "v*")
      .call()
      .out.trim()
    os.proc("git", "tag", "-l")
      .call()
      .out.lines()
      .filter(_ != current)
      .filter(_.startsWith("v"))
      .filter(!_.contains("-"))
      .map(_.stripPrefix("v"))
      .map(coursier.core.Version(_))
      .sorted
      .map(_.repr)
  }
}

trait WindowsAnsiJavaModule extends JavaModule {
  def jvmRelease: T[String] = Task.Input("8")
  private def isArm64 =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("aarch64" | "arm64") => true
      case _                         => false
    }
  def javacSystemJvmId = T {
    if (Properties.isMac && isArm64) s"zulu:${jvmRelease()}"
    else if (Properties.isWin && isArm64)
      if (jvmRelease() == "8") "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u442-b06/OpenJDK8U-jdk_x64_windows_hotspot_8u442b06.zip"
      else s"liberica:${jvmRelease()}"
    else s"adoptium:${jvmRelease()}"
  }
  def javacSystemJvm = T.source {
    val javaHome = os.Path(coursierapi.JvmManager.create().get(javacSystemJvmId()), os.pwd)
    PathRef(javaHome, quick = true)
  }
  // adds options equivalent to --release ${jvmRelease()} + allowing access to unsupported JDK APIs
  // (no more straightforward options to achieve that AFAIK)
  def maybeJdkReleaseJavacOpt = T {
    val javaHome   = javacSystemJvm().path
    val rtJar      = javaHome / "jre/lib/rt.jar"
    val hasModules = os.isDir(javaHome / "jmods")
    val hasRtJar   = os.isFile(rtJar)
    assert(hasModules || hasRtJar)
    if (hasModules)
      Seq("--release", jvmRelease()) // FIXME javacSystemJvm is unused here
    else
      Seq("-source", jvmRelease(), "-target", jvmRelease(), "-bootclasspath", rtJar.toString)
  }
  def javacOptions = T {
    super.javacOptions() ++ maybeJdkReleaseJavacOpt()
  }
}

object native extends JavaModule with WindowsAnsiPublishModule {
  def artifactName = "native-terminal"
  def ivyDeps = Agg(
    ivy"io.github.alexarchambault:is-terminal:0.1.2",
    ivy"org.jline:jline-native:3.30.5"
  )

  def jdk22ClassesResources = T {
    val destDir = T.dest / "META-INF/versions/22"
    os.makeDir.all(destDir)
    for (elem <- os.list(jdk22.compile().classes.path))
      os.copy(elem, destDir / elem.last)
    PathRef(T.dest)
  }

  def resources = T {
    T.sources(Seq(PathRef(millSourcePath / "resources")) ++ Seq(jdk22ClassesResources()))
  }
  def manifest = T {
    super.manifest().add("Multi-Release" -> "true")
  }

  object jdk22 extends WindowsAnsiJavaModule {
    def jvmRelease: T[String] = Task.Input("22")
    def moduleDeps = Seq(native)
  }
}

object `native-no-ffm` extends JavaModule with WindowsAnsiPublishModule {
  def artifactName = Task {
    native.artifactName() + "-no-ffm"
  }
  def ivyDeps = native.ivyDeps

  def sources = native.sources
}

object `native-graalvm` extends JavaModule with WindowsAnsiPublishModule {
  def moduleDeps = Seq(native)
  def artifactName = "native-terminal-graalvm"
  def pomSettings = super.pomSettings().copy(
    licenses = Seq(License.`GPL-2.0-with-classpath-exception`)
  )
  def compileIvyDeps = Agg(
    ivy"org.graalvm.nativeimage:svm:22.0.0.2"
  )
  def mimaPreviousVersions = {
    val publishedSince = coursier.core.Version("0.0.4")
    super.mimaPreviousVersions().dropWhile { v =>
      coursier.core.Version(v) < publishedSince
    }
  }
}

object fallbacks extends JavaModule with WindowsAnsiPublishModule {
  def artifactName = "native-terminal-fallbacks"
  def mimaPreviousVersions = {
    val publishedSince = coursier.core.Version("0.0.2")
    super.mimaPreviousVersions().dropWhile { v =>
      coursier.core.Version(v) < publishedSince
    }
  }
}
