package pds

import akka.actor._
import akka.event.Logging
import akka.event.LoggingReceive

/**
  * Enum of clock machine actions
  * contains Low, High, X
  */
object ClockAction extends Enumeration {
	type ClockAction = Value
	val NextTick, WaitForTocks, End = Value
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
	private var waitingForTock: List[ActorRef] = List()

	/**
	  * Receives accepts following messages (otherwise logs a warning)
	  * AfterDelay
	  * Start
	  * Register
	  * AddWorkitem
	  * Tock
	  */
	def receive = LoggingReceive {
		case Start =>
			initiator = sender
			advance(NextTick)

		case AddWorkItem(item) => agenda = item :: agenda

		case Tock =>
			simulantReplied(sender) match {
				case List() => advance(NextTick)
				case _ =>
			}

		case Register => simulants = sender :: simulants

		case AfterDelay(time: Int, change: SignalChanged, observer: ActorRef) => observer ! change

		case msg => log.warning(this + " received unknown message: " + msg)
	}

	override def hashCode: Int =
		41 * (41 * (41 * (41 + agenda.hashCode) + simulants.hashCode) + currentTime) + initiator.hashCode

	override def toString: String = {
		return "Clock at " + currentTime
	}

	/**
	  * registers the simulant, which sends a tock
	  * @param simulant
	  * @return list of simulants, which still need to send a tock
	  */
	private def simulantReplied(simulant: ActorRef): List[ActorRef] = {
		waitingForTock = waitingForTock.diff(List(simulant))
		waitingForTock
	}

	/**
	  * Drives statemachine of the clock
	  * 1) Proceeds to the next time
	  * 2) Sends WorkItems for the currentTime
	  * 3) Sends Ticks to all simulants
	  * 4) Waits for Tocks, when all simulants have replied proceed
	  *
	  * @param action of the current time
	  * @return next action
	  */
	private def advance(action: ClockAction): ClockAction = {

		def endSimulation(): ClockAction = {
			initiator ! StoppedAt(currentTime)
			End
		}

		def notifySimulants: Unit = {
			waitingForTock = simulants
			simulants.foreach(_ ! Tick)
		}

		action match {
			case NextTick =>
				currentTime += 1
				if (agenda.isEmpty) return endSimulation
				val currentWorkItems = agenda.filter(item => item.time == currentTime)
				currentWorkItems.foreach(item => item.target ! item.msg)
				agenda = agenda.diff(currentWorkItems)
				notifySimulants
				WaitForTocks
		}
	}
}

case object Start
case class StoppedAt(time: Int)
case class AddWorkItem(item: WorkItem)
case class WorkItem(time: Int, msg: Any, target: ActorRef)
case object Register