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
		"""akka.loglevel = DEBUG
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

	test("start clock without an Agenda immediately stops") {
		cl ! Start
		expectMsg(StoppedAt(1))
	}

	test("start clock with Agenda, proceeds until all AgendaItems are processed") {
		cl ! AddWorkItem(WorkItem(1, TestMsg, self))
		cl ! Start
		expectMsg(TestMsg)
	}

	test("send tick to simulant with no currentItems, simulant returns tock") {
		val w = system.actorOf(Wire.props("a", High, cl), "w1")
		w ! Tick
		expectMsg(Tock)
	}

	test("start clock with Agenda, end processing when no AgendaItems are left") {
		cl ! Register
		cl ! AddWorkItem(WorkItem(1, TestMsg, self))
		cl ! Start
		expectMsg(TestMsg)
		expectMsg(Tick)
		cl ! Tock
		expectMsg(StoppedAt(2))
	}

	test("add work items with a longer delay") {
		cl ! Register
		cl ! AddWorkItem(WorkItem(3, TestMsg, self))
		cl ! Start

		expectMsg(Tick)
		cl ! Tock
		expectMsg(Tick)
		cl ! Tock

		expectMsg(TestMsg)

		expectMsg(Tick)
		cl ! Tock

		expectMsg(StoppedAt(4))
	}

	ignore("add observer to wire") {
		val w = system.actorOf(Wire.props("a", High, cl), "w2")
		w ! AddObserver(testActor)
		w ! SetSignal(Low)
		cl ! Start
		expectMsg(SignalChanged(w, Low))
	}

	ignore("initial logic level is propagated when simulation is started") {
		val w = system.actorOf(Wire.props("a", High, cl))
		w ! AddObserver(testActor)
		cl ! Start
		expectMsg(SignalChanged(w, High))
		expectMsg(StoppedAt(2))
	}
}

case object TestMsg