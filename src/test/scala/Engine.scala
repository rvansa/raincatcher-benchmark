import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder
import com.feedhenry.raincatcher.DemoSimulation

object Engine extends App {
	val simulationClass = classOf[DemoSimulation].getName
	val props = new GatlingPropertiesBuilder()
		.dataDirectory(IDEPathHelper.directories.data.toString)
		.resultsDirectory(IDEPathHelper.directories.results.toString)
		.bodiesDirectory(IDEPathHelper.directories.bodies.toString)
		.binariesDirectory(IDEPathHelper.directories.binaries.toString)
  	.simulationClass(simulationClass)
		.runDescription("")

	Gatling.fromMap(props.build)
}
