package pds

import akka.actor._
import akka.event.Logging
import akka.event.LoggingReceive

/**
  * Enum of logic levels
  * contains Low, High, X
  */
object LogicLevel extends Enumeration {
	type LogicLevel = Value
	val Low, High, X = Value
}
import LogicLevel._

/**
  * Simulant is the basic trait for components
  */
trait Simulant extends Actor {
	val log = Logging(context.system, this)

	val clock: ActorRef
	var observers: List[ActorRef] = List()

	/**
	  * accepts following messages
	  * AddObserver
	  * Tick
	  */
	override def receive = LoggingReceive {
		case AddObserver(obs) => observers = obs :: observers
		case Tick(time) => sender ! Tock(time)
	}

}

/**
  * Prop factory for the Wire component
  */
object Wire {
	def props(name: String, init: LogicLevel, clock: ActorRef): Props = {
		Props(classOf[Wire], name, init, 1, clock)
	}
}

/**
  * Wire component represents the physical wire with a certain logic level
  * @param name reference
  * @param init logic value, when unspecified X
  * @param delay to propagate
  * @param clk driving the simulant
  */
class Wire(name: String, init: LogicLevel = X, delay: Int = 1, clk: ActorRef) extends Simulant {
	val clock = clk
	private var logiclevel: LogicLevel = init

	clock ! Register

	/**
	  * accepts following messages (otherwise logs a warning)
	  * SetSignal
	  * Start
	  */
	override def receive = super.receive orElse {
		case (SetSignal(lvl), time) =>
			if (lvl != logiclevel) {
				logiclevel = lvl
				signalObservers
			}
		case Start => signalObservers

		case msg => log.warning(this + " received unknown message: " + msg)
	}

	private def signalObservers(): Unit = {
		for (obs <- observers)
			clock ! AddWorkItem(WorkItem(delay, SignalChanged(self, logiclevel), obs))
	}

	override def hashCode: Int =
		41 * (41 + clk.hashCode) + logiclevel.hashCode

	override def toString(): String = {
		return "Wire " + name
	}
}

case class Tick(time: Int)
case class Tock(time: Int)
case class SignalChanged(simulant: ActorRef, newLevel: LogicLevel)
case class SetSignal(newLevel: LogicLevel)
case class AddObserver(observer: ActorRef)

