package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {

  // part 1 - actor system
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  // part 2 - create actors
  // word count actor

  class WordCountActor extends Actor {
    // internal data
    var totalWords = 0

    // behavior
    def receive: Receive = {
      case message: String =>
        println(s"[word counter] I have received: $message")
        totalWords += message.split(" ").length
      case msg => println(s"[word counter] I cannot understand $msg")
    }
  }

  // part 3 - instantiate out actor
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")

  // part 4 - communicate
  wordCounter ! "I am learning Akka and it's pretty dam cool!" // tell
  anotherWordCounter ! "A different message"
  // asynchronous

  object Person {
    def props(name: String) = Props(new Person(name))
  }

  class Person(name: String) extends Actor {
    def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name!")
    }
  }

  val person = actorSystem.actorOf(Person.props("Bob"))
  person ! "hi"

}
