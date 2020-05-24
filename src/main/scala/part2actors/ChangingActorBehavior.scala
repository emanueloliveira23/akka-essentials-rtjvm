package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehavior extends App {

  object FussyKid {
    case object KidAccept
    case object KidReject
    val Happy = "happy"
    val Sad = "sad"
  }
  class FussyKid extends Actor {
    import FussyKid._
    import Mom._
    private var state = Happy
    def receive: Receive = {
      case Food(Vegetable) => state = Sad
      case Food(Chocolate) => state = Happy
      case Ask(_) =>
        if (state == Happy) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(Vegetable) => context.become(sadReceive, false)
      case Food(Chocolate) =>
      case Ask(_) => sender() ! KidAccept
    }

    def sadReceive: Receive = {
      case Food(Vegetable) => context.become(sadReceive, false)
      case Food(Chocolate) => context.unbecome()
      case Ask(_) => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kid: ActorRef)
    case class Food(food: String)
    case class Ask(message: String) // do u want to play?
    val Vegetable = "veggies"
    val Chocolate = "chocolate"
  }
  class Mom extends Actor {
    import Mom._
    import FussyKid._
    def receive: Receive = {
      case MomStart(kid) =>
        kid ! Food(Vegetable)
        kid ! Food(Vegetable)
        kid ! Food(Chocolate)
        kid ! Ask("Do you want to play?")
      case KidAccept => println("Yay, my kid is happy!")
      case KidReject => println("My kid is sad, but as he's healthy!")
    }
  }

  val system = ActorSystem("changingActorBehaviorDemo")

  val fussyKid = system.actorOf(Props[FussyKid])
  val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  val mom = system.actorOf(Props[Mom])

  import Mom._

  mom ! MomStart(statelessFussyKid)

  /**
    * Exercise 1 - Recreate the Counter Actor with context.become and NO MUTABLE STATE
    *     Trick: methods receives can take parameters
    */

  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends Actor {
    import Counter._

    def receive: Receive = countReceive(0)

    private def countReceive(count: Int): Receive = {
      case Increment => context.become(countReceive(count + 1))
      case Decrement => context.become(countReceive(count - 1))
      case Print => println(s"count: $count")
    }

  }

  import Counter._

  val counter = system.actorOf(Props[Counter], "counter")

  for (_ <- 1 to 5) counter ! Increment
  counter ! Print
  for (_ <- 1 to 3) counter ! Decrement
  counter ! Print

  /**
    * Exercise 2 - simplified voting system
    */
  object Citizen {
    case class Vote(candidate: String)
    case object VoteStatusRequest
    case class VoteStatusReply(candidate: Option[String])
  }
  class Citizen extends Actor {
    import Citizen._
    def receive: Receive = unvotedReceive
    private def unvotedReceive: Receive = {
      case Vote(candidate) => context.become(votedReceive(candidate))
      case VoteStatusRequest => VoteStatusReply(None)
    }
    private def votedReceive(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }

  object VoteAggregator {
    case class AggregateVotes(citizens: Set[ActorRef])
    case class State(stats: Map[String, Int], stillWaiting: Set[ActorRef])
  }
  class VoteAggregator extends Actor {
    import VoteAggregator._
    import Citizen._

    def receive: Receive = startReceive

    def startReceive: Receive = {
      case AggregateVotes(citizens: Set[ActorRef]) =>
        context.become( aggregateReceive(State(Map.empty, citizens)) )
        citizens.foreach(_ ! VoteStatusRequest)
    }

    def aggregateReceive(state: State): Receive = {
      case VoteStatusReply(None) =>
        sender() ! VoteStatusRequest
      case VoteStatusReply(Some(candidate)) =>
        val nextState = state.copy(
          stats = state.stats.updated( candidate, state.stats.getOrElse(candidate, 0) + 1 ),
          stillWaiting = state.stillWaiting - sender()
        )
        if (nextState.stillWaiting.nonEmpty) {
          context.become(aggregateReceive(nextState))
        } else {
          println("Election result:")
          for ((c, v) <- nextState.stats) println(s"$c: $v")
        }
    }
  }

  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  import Citizen._
  import VoteAggregator._

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))

  /*
   Print status of votes
   Martin -> 1
   Jonas -> 1
   Roland -> 2
    */

}
