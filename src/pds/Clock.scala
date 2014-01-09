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
	/**
	  * Receives accepts following messages (otherwise logs a warning)
	  */
	def receive = {
		case AfterDelay(time: Int, change: SignalChanged, observer: ActorRef) => observer ! change
		case msg => log.warning("Received unknown message: " + msg)
	}
}