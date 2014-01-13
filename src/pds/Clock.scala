package pds

import akka.actor._
import akka.event.Logging

/**
  * Enum of clock machine actions
  * contains Low, High, X
  */
object ClockAction extends Enumeration {
	type ClockAction = Value
	val WaitForTocks, End = Value
}
import ClockAction._

/**
  * Prop factory for the clock
  */
object Clock {
	def props: Props = {
		new Props(classOf[Clock])
	}
}

/**
  * Clock determines the ordering of events
  */
class Clock extends Actor {
	val log = Logging(context.system, this)
	private var currentTime = 0
	private var initiator: ActorRef = _
	private var agenda: List[WorkItem] = List()
	private var simulants: List[ActorRef] = List()

	/**
	  * Receives accepts following messages (otherwise logs a warning)
	  * AfterDelay
	  * Start
	  * Register
	  * AddWorkitem
	  */
	def receive = {
		case Start =>
			initiator = sender
			advance

		case AddWorkItem(item) => agenda = item :: agenda

		case Register => simulants = sender :: simulants

		case AfterDelay(time: Int, change: SignalChanged, observer: ActorRef) => observer ! change

		case msg => log.warning(this + " received unknown message: " + msg)
	}

	override def hashCode: Int =
		41 * (41 * (41 * (41 + agenda.hashCode) + simulants.hashCode) + currentTime) + initiator.hashCode

	override def toString: String = {
		return "Clock at " + currentTime
	}

	private def advance: ClockAction = {

		def endSimulation(): ClockAction = {
			initiator ! StoppedAt(currentTime)
			End
		}
		def process(items: List[WorkItem]): List[WorkItem] = {
			if (items == List()) return items
			else {
				items.head.target ! items.head.msg
				process(items.tail)
			}
		}
		currentTime += 1
		if (agenda.isEmpty) return endSimulation
		process(agenda.filter(item => item.time == currentTime))
		WaitForTocks
	}
}

case object Start
case class StoppedAt(time: Int)
case class AddWorkItem(item: WorkItem)
case class WorkItem(time: Int, msg: Any, target: ActorRef)
case object Register