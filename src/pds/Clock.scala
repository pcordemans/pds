package pds

import akka.actor._
import akka.event.Logging

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
	var currentTime = 0
	var initiator: ActorRef = _

	/**
	  * Receives accepts following messages (otherwise logs a warning)
	  * AfterDelay
	  * Start
	  */
	def receive = {
		case Start =>
			initiator = sender
			advance
		case AfterDelay(time: Int, change: SignalChanged, observer: ActorRef) => observer ! change
		case msg => log.warning("Received unknown message: " + msg)
	}

	private def advance: Unit = {
		currentTime += 1
		initiator ! StoppedAt(currentTime)
	}
}

case object Start
case class StoppedAt(time: Int)