package pds

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import akka.actor._
import akka.testkit._
import com.typesafe.config._
import LogicLevel._

@RunWith(classOf[JUnitRunner])
class SimulatorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender with FunSuite with BeforeAndAfter with BeforeAndAfterAll {

	def this() = this(ActorSystem("TestSystem", ConfigFactory.parseString(
		"""akka.loglevel = WARNING
			akka.stdout-loglevel = INFO
			akka.actor.default-dispatcher.throughput = 1	
			akka.actor.debug.receive = on
			akka.actor.debug.lifecycle = off
			akka.actor.debug.event-stream = on
			""")))

	var cl: ActorRef = _

	before {
		cl = system.actorOf(Clock.props)
	}

	override def afterAll {
		system.shutdown()
	}

	test("start clock") {
		cl ! Start
		expectMsg(StoppedAt(1))
	}

	test("add simulant to clock") {
		val w = system.actorOf(Wire.props("a", High, cl))
		w ! AddObserver(testActor)
		w ! SetSignal(Low)
		expectMsg(SignalChanged(w, Low))
	}
}