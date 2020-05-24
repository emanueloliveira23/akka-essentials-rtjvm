package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import part2actors.ActorCapabilities.Person.LiveTheLife

object ActorCapabilities extends App {

  class SimpleActor extends Actor {
//    context.system
//    context.self

    override def receive: Receive = {
      case "Hi!" => context.sender() ! "Hello, there!" // replying to a message
      case message: String =>
        println(s"[$self] I have received $message")
      case number: Int =>
        println(s"[simple actor] I have received a number: $number")
      case SpecialMessage(content) =>
        println(s"[simple actor] I have received a special message: $content")
      case SendMessageToYourself(content) =>
        self ! content
      case SayHiTo(ref) =>
        ref ! "Hi!" // alice is being passed as the sender
      case WirelessPhoneMessage(content, ref) =>
        ref forward (content + "s") // I keep th original sender of the WPM
    }
  }

  val system = ActorSystem("ActorCapabilitiesDemo")

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - message can be of any type
  // a) messages must be IMMUTABLE
  // b) messages must be SERIALIZABLE
  // in practice use case class and case objects

  simpleActor ! 42 // who is the sender?

  // 2 - actors have information about their context and about themselves
  // context.self === `this` in OO

  case class SpecialMessage(content: String)
  simpleActor ! SpecialMessage("some special content")

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I'm an actor and I'm proud of it")

  // 3 - actors can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // 4 - dead letters
  alice ! "Hi!" // reply to me

  // 5 - forwarding messages
  // D -> A -> B
  // forwarding = sending a message with the ORIGINAL sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi!", bob) // no sender

  /**
    * Exercises
    *
    * 1. a Counter actor
    *   - Increment
    *   - Decrement
    *   - Print
    *
    * 2. a Bank account as an actor
    *   receives
    *   - Deposit an amount
    *   - Withdraw an amount
    *   - Statement with
    *   replies
    *   - Success
    *   - Failure
    *
    *  interact with some other kind of actor
    */

  // 1 - Counter

  // DOMAIN of the counter
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends Actor {
    import Counter._
    var count = 0
    override def receive: Receive = {
      case Increment => count += 1
      case Decrement => count -= 1
      case Print => println(s"[counter] count: $count")
    }
  }

  import Counter._

  val counter = system.actorOf(Props[Counter], "counter")

  for (_ <- 1 to 5) counter ! Increment
  counter ! Print
  for (_ <- 1 to 3) counter ! Decrement
  counter ! Print


  // 2 - Bank Account

  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement
    case class TransactionSuccess(message: String)
    case class TransactionFailure(reason: String)
  }

  class BankAccount extends Actor {
    import BankAccount._
    var funds = 0
    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure(s"invalid deposit amount: $amount")
        else {
          funds += amount
          sender() ! TransactionSuccess(s"successfully deposint $amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure(s"invalid deposit amount: $amount")
        else if (amount > funds) sender() ! TransactionFailure("insufficient funds")
        else {
          funds -= amount
          sender() ! TransactionSuccess(s"successfully withdrew $amount")
        }
      case Statement =>
        sender() ! s"Your balance is $funds"
    }
  }

  object Person {
    case class LiveTheLife(account: ActorRef)
  }

  class Person extends Actor {
    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(90000)
        account ! Withdraw(500)
        account ! Statement
      case message => println(message)
    }
  }

  val account = system.actorOf(Props[BankAccount], "bankAccount")
  val person = system.actorOf(Props[Person], "billionaire")

  person ! LiveTheLife(account)
}
