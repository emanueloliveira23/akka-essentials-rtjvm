package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}

object ChildActorsExercise extends App {

  // Distributed Word counting

  object WordCounterMaster {
    case class Initialize(nChildren: Int)
    case class WordCountTask(text: String, sender: Option[ActorRef] = None)
    case class WordCountReply(count: Int, text: String, sender: Option[ActorRef] = None)
  }
  class WordCounterMaster extends Actor {
    import WordCounterMaster._
    def receive: Receive = {
      case Initialize(nChildren) =>
        context.children.foreach(_ ! PoisonPill) // cleaning before init
        for (i <- 0 to nChildren) context.actorOf(Props[WordCounterWorker], s"worker$i")
        context.become(withChildren(nChildren, 0))
    }
    def withChildren(nChildren: Int, taskOrd: Int): Receive = {
      case text: String =>
        val workerIndex = taskOrd % nChildren
        val workerPath = s"worker$workerIndex"
        val worker = context.actorSelection(workerPath)
        println(s"${self.path} forwarding task '$text' to $workerPath")
        worker ! WordCountTask(text, Some(sender()))
        context.become(withChildren(nChildren, taskOrd + 1))
      case WordCountReply(count, text, Some(originalSender)) =>
        println(s"${self.path} forwarding reply '$text'")
        originalSender ! WordCountReply(count, text, None)
    }
  }

  class WordCounterWorker extends Actor {
    import WordCounterMaster._
    def receive: Receive = {
        case WordCountTask(text, originSender) =>
        println(s"${self.path} receiving task '$text'")
        val count = text.split(" ").length
        sender() ! WordCountReply(count, text, originSender)
    }
  }

  object WordCounterRequester {
    case class Request(masterRef: ActorRef)
  }
  class WordCounterRequester extends Actor {
    import WordCounterRequester._
    import WordCounterMaster._
    def receive: Receive = {
      case Request(masterRef) =>
        masterRef ! Initialize(2)
        masterRef ! "I love akka"
        masterRef ! "What should I do?"
        masterRef ! "What's the meaning of life?"
      case WordCountReply(count, text, _) =>
        println(s"${self.path} receiving reply: '$text' has $count words ")
    }
  }

  /*
    create WordCounterMaster
    send Initialize(10) to wordCounterMaster
    send "Akka is awesome" to wordCounterMaster
      wcm will send a WordCountTask("...") to one of its children
        child replies with WordCountReply(3) to the master
      master replies with 3 to the sender.

    requester -> wcm -> wcw
    requester <- wcm <- wcw
   */
  // round robin logic
  // 1,2,3,4,5 and 7 task
  // 1,2,3,4,5,1,2

  import WordCounterRequester._

  val system = ActorSystem("ChildActorsExercise")
  val master = system.actorOf(Props[WordCounterMaster], "master")
  val requester = system.actorOf(Props[WordCounterRequester], "requester")
  requester ! Request(master)

}
