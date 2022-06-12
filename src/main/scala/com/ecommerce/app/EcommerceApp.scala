package com.ecommerce.app

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.ecommerce.actors.Ecommerce
import com.ecommerce.actors.PersistentOrder.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.ecommerce.http.EcommerceRouter

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

object EcommerceApp {

  def startHttpServer(ecommerce: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router = new EcommerceRouter(ecommerce)
    val routes = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)
    httpBindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        system.log.error(s"Failed to bind HTTP server, because: $ex")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    trait RootCommand
    case class RetrieveEcommerceActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
      val ecommerceActor = context.spawn(Ecommerce(), "ecommerce")
      Behaviors.receiveMessage {
        case RetrieveEcommerceActor(replyTo) =>
          replyTo ! ecommerceActor
          Behaviors.same
      }
    }

    implicit val system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "EcommerceSystem")
    implicit val timeout: Timeout = Timeout(5.seconds)
    implicit val ec: ExecutionContext = system.executionContext

    val ecommerceActorFuture: Future[ActorRef[Command]] = system.ask(replyTo => RetrieveEcommerceActor(replyTo))
    ecommerceActorFuture.foreach(startHttpServer)
  }
}
