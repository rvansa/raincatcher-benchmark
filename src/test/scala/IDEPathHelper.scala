import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import com.feedhenry.raincatcher.Options
import io.gatling.commons.util.PathHelper._

class Directories(
									 val data: Path,
									 val bodies: Path,
									 val binaries: Path,
									 val results: Path
								 )

object IDEPathHelper {
	val uri = getClass.getClassLoader.getResource("gatling.conf").toURI
	val directories: Directories = if (uri.getScheme.startsWith("jar")) {
		val mainDir: Path = if (Options.testDir != null) {
			val dir = Paths.get(Options.testDir);
			if (dir.exists) {
				if (!dir.isDirectory) {
					throw new IllegalArgumentException(Options.testDir + " is not a directory")
				}
				dir
			} else {
				Files.createDirectory(dir)
			}
		} else {
			Files.createTempDirectory("gatling-")
		}
		Console.println("Using " + mainDir + " as gatling directory")
		// unpack gatling.conf
		Files.copy(getClass.getResourceAsStream("gatling.conf"), mainDir.resolve("gatling.conf"), StandardCopyOption.REPLACE_EXISTING)
		// using createDirectories to ignore existing
		val directories = new Directories(
			Files.createDirectories(mainDir.resolve("data")),
			Files.createDirectories(mainDir.resolve("bodies")),
			Files.createDirectories(mainDir.resolve("binaries")),
			Files.createDirectories(mainDir.resolve("results")))
		val simulationFile: String = Engine.simulationClass.replace('.', '/') + ".class"
		// unpack simulation
		val targetFile: Path = mainDir.resolve("binaries").resolve(simulationFile)
		Files.createDirectories(targetFile.getParent)
		Files.copy(getClass.getResourceAsStream(simulationFile), targetFile, StandardCopyOption.REPLACE_EXISTING)
		directories
	} else {
		val projectRootDir = RichPath(uri).ancestor(3)
		val mavenResourcesDirectory = projectRootDir / "src" / "test" / "resources"
		val mavenTargetDirectory = projectRootDir / "target"

		new Directories(
			mavenResourcesDirectory / "data",
			mavenResourcesDirectory / "bodies",
			mavenTargetDirectory / "test-classes",
			mavenTargetDirectory / "results")
	}
}
