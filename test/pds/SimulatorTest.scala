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
	var testCount = 0

	before {
		testCount += 1
		cl = system.actorOf(Clock.props, "clock_" + testCount)
	}

	override def afterAll {
		system.shutdown()
	}

	private def ticktock: Unit = {
		expectMsg(Tick)
		cl ! Tock
	}

	private def start: Unit = {
		cl ! Start(1)
		expectMsg(Start)
	}

	test("start clock without an Agenda immediately stops") {
		cl ! Register
		start
		ticktock
		expectMsg(StoppedAt(1))
	}

	test("send tick to simulant with no currentItems, simulant returns tock") {
		val w = system.actorOf(Wire.props("a", High, cl), "w1")
		w ! Tick
		expectMsg(Tock)
	}

	test("start clock with Agenda, end processing when no AgendaItems are left") {
		cl ! Register
		cl ! AddWorkItem(WorkItem(1, TestMsg, self))
		start
		ticktock
		expectMsg(TestMsg)
		ticktock
		expectMsg(StoppedAt(2))
	}

	test("add work items with a longer delay") {
		cl ! Register
		cl ! AddWorkItem(WorkItem(3, TestMsg, self))
		start

		for (i <- 1 to 3) ticktock

		expectMsg(TestMsg)

		ticktock

		expectMsg(StoppedAt(4))
	}

	test("initial logic level is propagated when simulation is started") {
		val w = system.actorOf(Wire.props("a", High, cl))
		w ! AddObserver(testActor)
		cl ! Start(1)
		expectMsg(SignalChanged(w, High))
		expectMsg(StoppedAt(2))
	}

	ignore("add observer to wire") {
		val w = system.actorOf(Wire.props("a", High, cl), "w2")
		w ! AddObserver(testActor)
		w ! SetSignal(Low)
		cl ! Start(1)
		expectMsg(SignalChanged(w, Low))
	}

}

case object TestMsg