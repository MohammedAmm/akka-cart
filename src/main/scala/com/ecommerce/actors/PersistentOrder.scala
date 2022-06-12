package com.ecommerce.actors

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.Effect
import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import scala.util.{Success, Try}


object  PersistentOrder {

    import Command._
    import Response._
    sealed trait Command

    object Command {
        case class CreateOrder(totalAmount: Double, replyTo: ActorRef[Response]) extends Command
        //Product should be a seperate subdomain, and it would be better if we have a product catalog and 
        //The product be the core domain
        case class AddItem(id: String, price: Double, replyTo: ActorRef[Response]) extends Command
        case class GetOrder(id: String, replyTo: ActorRef[Response]) extends Command
        //Should be another generic subdomain
        case class Checkout(id: String, replyTo: ActorRef[Response]) extends Command
    }
    

    trait Event
    case class OrderCreated(order: Order) extends Event
    case class ItemAdded(price: Double) extends Event

    case class Order(id: String, totalAmount: Double)

    sealed trait Response
    object Response{
        case class OrderCreatedResponse(id: String) extends Response
        case class OrderItemAddedResponse(maybeOrder: Try[Order]) extends Response
        case class GetOrderResponse(maybeOrder: Option[Order]) extends Response
        case class CheckoutResponse(maybeOrder: Option[Order]) extends Response
    }

    val commandHandler: (Order, Command) => Effect[Event, Order] = (state, command) =>
        command match {
            case CreateOrder(totalAmount, replyTo) => 
                val id = state.id
                Effect
                    .persist(OrderCreated(Order(id, totalAmount)))
                    .thenReply(replyTo)(_=>OrderCreatedResponse(id))
            
            case AddItem(_, price, replyTo) => 
                Effect
                    .persist(ItemAdded(price))
                    .thenReply(replyTo)(newState => OrderItemAddedResponse(Success(newState)))
            case GetOrder(_, replyTo) => 
                Effect.reply(replyTo)(GetOrderResponse(Some(state)))
            case Checkout(_, replyTo) => 
                Effect.reply(replyTo)(CheckoutResponse(Some(state)))
        }
        

    val eventHandler: (Order, Event) => Order =  (state, event) =>
        event match {
            case OrderCreated(order) => 
                order
            case ItemAdded(price) => 
                state.copy(totalAmount = state.totalAmount + price)
        }

    def apply(id: String): Behavior[Command] = 
        EventSourcedBehavior[Command, Event, Order](
            persistenceId = PersistenceId.ofUniqueId(id),
            emptyState = Order(id, 0.0),
            commandHandler = commandHandler,
            eventHandler = eventHandler
        )
}