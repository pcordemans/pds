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
	val NextTick, WaitForTocks, WaitForRegistration, End = Value
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
	private var currentTime: Int = 0
	private var initiator: ActorRef = _
	private var agenda: List[WorkItem] = List()
	private var simulants: List[ActorRef] = List()
	private var waitingForTock: List[ActorRef] = List()
	private var numberOfComponents: Int = 0

	/**
	  * Receives accepts following messages (otherwise logs a warning)
	  * AfterDelay
	  * Start
	  * Register
	  * AddWorkitem
	  * Tock
	  */
	def receive = LoggingReceive {
		case Start(expectedNumber) =>
			initiator = sender
			numberOfComponents = expectedNumber
			advance(start)

		case AddWorkItem(item) =>
			val scheduledItem = WorkItem(currentTime + item.time, item.msg, item.target)
			agenda = scheduledItem :: agenda

		case Tock =>
			simulantReplied(sender) match {
				case List() => advance(NextTick)
				case _ =>
			}

		case Register =>
			simulants = sender :: simulants
			advance(start)

		case msg => log.warning(this + " received unknown message: " + msg)
	}

	override def hashCode: Int =
		41 * (41 * (41 * (41 + agenda.hashCode) + simulants.hashCode) + currentTime) + initiator.hashCode

	override def toString: String = {
		return "Clock at " + currentTime
	}

	/**
	  * starts the simulation when the expected number of components has been reached
	  * @return WaitForRegistration / NextTick
	  */
	private def start: ClockAction = {
		if (numberOfComponents == simulants.size) {
			simulants.foreach(_ ! Start)
			notifySimulants
		} else WaitForRegistration
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
	  * sends ticks to each registered simulant
	  */
	private def notifySimulants: ClockAction = {
		waitingForTock = simulants
		simulants.foreach(_ ! Tick)
		WaitForTocks
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

		action match {
			case NextTick =>
				currentTime += 1
				if (agenda.isEmpty) return endSimulation
				val currentWorkItems = agenda.filter(item => item.time == currentTime)
				currentWorkItems.foreach(item => item.target ! item.msg)
				agenda = agenda.diff(currentWorkItems)
				notifySimulants
			case WaitForRegistration => WaitForRegistration
			case WaitForTocks => WaitForTocks
		}
	}
}

case class Start(numberOfComponents: Int = 0)
case class StoppedAt(time: Int)
case class AddWorkItem(item: WorkItem)
case class WorkItem(time: Int, msg: Any, target: ActorRef)
case object Register