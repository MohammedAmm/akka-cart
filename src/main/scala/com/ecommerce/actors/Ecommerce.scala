package com.ecommerce.actors

import akka.NotUsed
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.Failure


object Ecommerce {

  // commands = messages
  import PersistentOrder.Command._
  import PersistentOrder.Response._
  import PersistentOrder.Command

  // events
  sealed trait Event
  case class OrderCreated(id: String) extends Event

  // state
  case class State(accounts: Map[String, ActorRef[Command]])

  // command handler
  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) =>
    command match {
      case createCommand @ CreateOrder(_, _) =>
        val id = UUID.randomUUID().toString
        val newOrder = context.spawn(PersistentOrder(id), id)
        Effect
          .persist(OrderCreated(id))
          .thenReply(newOrder)(_ => createCommand)
      case addCommand @ AddItem(id, _, replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(addCommand)
          case None =>
            Effect.reply(replyTo)(OrderItemAddedResponse(Failure(new RuntimeException("Order cannot be found"))))
        }
      case getCmd @ GetOrder(id, replyTo) =>
        state.accounts.get(id) match {
          case Some(account) =>
            Effect.reply(account)(getCmd)
          case None =>
            Effect.reply(replyTo)(GetOrderResponse(None)) // failed search
        }
    }

  // event handler
  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) =>
    event match {
      case OrderCreated(id) =>
        val account = context.child(id) // exists after command handler,
          .getOrElse(context.spawn(PersistentOrder(id), id)) // does NOT exist in the recovery mode, so needs to be created
          .asInstanceOf[ActorRef[Command]]
        state.copy(state.accounts + (id -> account))
    }

  // behavior
  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("ecommerce"),
      emptyState = State(Map()),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
}
