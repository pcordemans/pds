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

	private def ticktock(time: Int): Unit = {
		expectMsg(Tick(time))
		cl ! Tock(time)
	}

	private def start: Unit = {
		cl ! Start(1)
		expectMsg(Start)
	}

	test("start clock without an Agenda immediately stops") {
		cl ! Register
		start
		ticktock(0)
		expectMsg(StoppedAt(1))
	}

	test("send tick to simulant with no currentItems, simulant returns tock") {
		val w = system.actorOf(Wire.props("a", High, cl), "w1")
		w ! Tick(0)
		expectMsg(Tock(0))
	}

	test("start clock with Agenda, end processing when no AgendaItems are left") {
		cl ! Register
		cl ! AddWorkItem(WorkItem(1, TestMsg, self))
		start
		ticktock(0)
		expectMsg(TestMsg)
		ticktock(1)
		expectMsg(StoppedAt(2))
	}

	test("add work items with a longer delay") {
		cl ! Register
		cl ! AddWorkItem(WorkItem(3, TestMsg, self))
		start

		for (i <- 0 to 2) ticktock(i)

		expectMsg(TestMsg)

		ticktock(3)

		expectMsg(StoppedAt(4))
	}

	test("initial logic level is propagated when simulation is started") {
		val w = system.actorOf(Wire.props("a", High, cl))
		w ! AddObserver(testActor)
		cl ! Start(1)
		expectMsg(SignalChanged(w, High))
		expectMsg(StoppedAt(2))
	}

	test("registration of components is possible after the start message has been sent") {
		cl ! Start(1)

		cl ! Register

		expectMsg(Start)

		ticktock(0)
		expectMsg(StoppedAt(1))
	}

	test("introduce a new signalchanged in the simulation") {
		val w = system.actorOf(Wire.props("a", High, cl), "w2")
		w ! AddObserver(testActor)
		cl ! Start(2)
		cl ! Register
		expectMsg(Start)
		ticktock(0)
		expectMsg(SignalChanged(w, High))

		cl ! AddWorkItem(WorkItem(3, SetSignal(Low), w))

		for (i <- 1 to 4) ticktock(i)

		expectMsg(SignalChanged(w, Low))

		ticktock(5)
		expectMsg(StoppedAt(6))
	}

	test("AND gate propagates signal") {
		val in1 = system.actorOf(Wire.props("in1", Low, cl), "in1")
		val in2 = system.actorOf(Wire.props("in1", High, cl), "in2")
		val out = system.actorOf(Wire.props("out", X, cl), "out")
		out ! AddObserver(testActor)
		val andgate = system.actorOf(AndGate.props("and", in1, in2, out, cl))
		cl ! Start(4)
		expectMsg(SignalChanged(out, X))
		expectMsg(SignalChanged(out, Low))
		expectMsg(StoppedAt(6))
	}
}

case object TestMsg