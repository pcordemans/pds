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

	test("start clock without an Agenda immediately stops") {
		cl ! Start
		expectMsg(StoppedAt(1))
	}

	test("start clock with Agenda, proceeds until all AgendaItems are processed") {
		cl ! AddWorkItem(WorkItem(1, TestMsg, self))
		cl ! Start
		expectMsg(TestMsg)
	}

	test("add observer to wire") {
		val w = system.actorOf(Wire.props("a", High, cl))
		w ! AddObserver(testActor)
		w ! SetSignal(Low)
		expectMsg(SignalChanged(w, Low))
	}

	ignore("initial logic level is propagated when simulation is started") {
		val w = system.actorOf(Wire.props("a", High, cl))
		w ! AddObserver(testActor)
		cl ! Start
		expectMsg(SignalChanged(w, High))
		expectMsg(StoppedAt(2))
	}

	ignore("upon receiving an AfterDelay message, schedule a new work item") {
		val w = system.actorOf(Wire.props("a", High, cl))
		cl ! Start
		w ! SetSignal(Low)
		expectMsg(StoppedAt(2))
	}
}

case object TestMsg