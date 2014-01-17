package pds

import akka.actor._
import LogicLevel._

object AndGate {
	def props(name: String, in1: ActorRef, in2: ActorRef, out: ActorRef, clock: ActorRef): Props = {
		Props(classOf[AndGate], name, in1, in2, out, clock)
	}
}

class AndGate(name: String, in1: ActorRef, in2: ActorRef, out: ActorRef, clk: ActorRef) extends Simulant {
	val clock = clk
	var lvl_in1, lvl_in2, lvl_out: LogicLevel = X
	val delay = 3

	clock ! Register
	observeInputs

	/**
	  * accepts following messages (otherwise logs a warning)
	  * Start
	  * SignalChanged
	  */
	override def receive = super.receive orElse {
		case Start =>
		case (SignalChanged(wire, lvl), time) =>
			val new_lvl = update(wire, lvl)
			if (new_lvl != lvl_out) {
				lvl_out = new_lvl
				clk ! AddWorkItem(WorkItem(delay, SetSignal(new_lvl), out))
			}

		case msg => log.warning(this + " received unknown message: " + msg)
	}

	override def toString: String = {
		"AND Gate " + name
	}

	override def hashCode: Int = {
		41 * (41 * (41 * (41 * (41 + clk.hashCode) + name.hashCode) + in1.hashCode) + out.hashCode) + in2.hashCode
	}

	private def update(wire: ActorRef, lvl: LogicLevel): LogicLevel = {
		wire.equals(in1) match {
			case true => lvl_in1 = lvl
			case false => lvl_in2 = lvl
		}

		if (lvl_in1 == X || lvl_in2 == X) X
		else if (lvl_in1 == Low || lvl_in2 == Low) Low
		else High
	}

	private def observeInputs: Unit = {
		in1 ! AddObserver(self)
		in2 ! AddObserver(self)
	}
}