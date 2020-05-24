package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChildActors extends App {

  // Actors can create other actors

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }
  class Parent extends Actor {
    import Parent._
    def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} create child")
        // create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    private def withChild(childRef: ActorRef): Receive = {
      case TellChild(message) => childRef forward message
    }
  }

  class Child extends Actor {
    def receive: Receive = {
      case message => println(s"${self.path} I got: $message")
    }
  }

  import Parent._

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("hey Kid!")

  // actor hierarchies
  // parent -> child -> grandChild
  //        -> child2 ->

  /*
    Guardian actors (top-level)
    - /system = system guardian
    - /user = user-level guardian
    - / = the root guardian
   */

  /**
    * Actor selection
    */

  val childSelection = system.actorSelection("/user/parent/child2")
  childSelection ! "I found you"

  /**
    * Danger!
    *
    * NEVER PASS ACTOR MUTABLE STATE, TO THE `THIS` REFERENCE, TO CHILD ACTORS.
    *
    * NEVER IN YOUR LIVE.
    */

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._
    var funds = 0
    def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this) // !
      case Deposit(amount) => deposit(amount)
      case Withdraw(amount) => withdraw(amount)
    }

    def deposit(amount: Int): Unit = {
      println(s"${self.path} depositing $amount on top of $funds")
      funds += amount
    }
    def withdraw(amount: Int): Unit = {
      println(s"${self.path} withdrawing $amount on top of $funds")
      funds -= amount
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount)
    case object CheckStatus
  }
  class CreditCard extends Actor {
    import CreditCard._
    def receive: Receive = {
      case AttachToAccount(account) => context.become(attachTo(account))
    }
    private def attachTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} your message has been processed.")
        account.withdraw(1) // because I can
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val backAccount = system.actorOf(Props[NaiveBankAccount], "account")
  backAccount ! InitializeAccount
  backAccount ! Deposit(100)


  Thread.sleep(500)
  val creditCard = system.actorSelection("/user/account/card")
  creditCard ! CheckStatus

  // WRONG!!!!

}
