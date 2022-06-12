package com.ecommerce.http

import akka.http.scaladsl.server.Directives._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import com.ecommerce.actors.PersistentOrder.Command
import com.ecommerce.actors.PersistentOrder.Command._
import com.ecommerce.actors.PersistentOrder.Response
import com.ecommerce.actors.PersistentOrder.Response._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import Validation._
import akka.http.scaladsl.server.Route
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._

case class OrderCreationRequest() {
  def toCommand(replyTo: ActorRef[Response]): Command = CreateOrder(0.0, replyTo)
}

object OrderCreationRequest {}


case class OrderAddItemRequest( price: Double) {
  def toCommand(id: String, replyTo: ActorRef[Response]): Command = AddItem(id, price, replyTo)
}

object OrderAddItemRequest {
  implicit val validator: Validator[OrderAddItemRequest] = new Validator[OrderAddItemRequest] {
    override def validate(request: OrderAddItemRequest): ValidationResult[OrderAddItemRequest] = {
      val amountValidation = validateMinimum(request.price, 0.1, "price")

      (amountValidation).map(OrderAddItemRequest.apply)
    }
  }
}

case class FailureResponse(reason: String)

class EcommerceRouter(ecommerce: ActorRef[Command])(implicit system: ActorSystem[_]) {
  implicit val timeout: Timeout = Timeout(5.seconds)

  def createOrder(request: OrderCreationRequest): Future[Response] =
    ecommerce.ask(replyTo => request.toCommand(replyTo))

  def getOrder(id: String): Future[Response] =
    ecommerce.ask(replyTo => GetOrder(id, replyTo))

  def addItemToOrder(id: String, request: OrderAddItemRequest): Future[Response] =
    ecommerce.ask(replyTo => request.toCommand(id, replyTo))
  
  def checkout(id: String): Future[Response] =
    ecommerce.ask(replyTo => Checkout(id, replyTo))

  def validateRequest[R: Validator](request: R)(routeIfValid: Route): Route =
    validateEntity(request) match {
      case Valid(_) =>
        routeIfValid
      case Invalid(failures) =>
        complete(StatusCodes.BadRequest, FailureResponse(failures.toList.map(_.errorMessage).mkString(", ")))
    }
  val routes =
    pathPrefix("orders") {
      pathEndOrSingleSlash {
        post {
          entity(as[OrderCreationRequest]) { request =>
              onSuccess(createOrder(request)) {
                case OrderCreatedResponse(id) =>
                  respondWithHeader(Location(s"/orders/$id")) {
                    complete(StatusCodes.Created)
                  }
              }
          }
        }
      } ~
        path(Segment) { id =>
          get {
            onSuccess(getOrder(id)) {
              case GetOrderResponse(Some(order)) =>
                complete(order) 
              case GetOrderResponse(None) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Order $id cannot be found."))
            }
          } ~
            post {
              entity(as[OrderAddItemRequest]) { request =>
                validateRequest(request) {
                  onSuccess(addItemToOrder(id, request)) {
                    case OrderItemAddedResponse(Success(order)) =>
                      complete(order)
                    case OrderItemAddedResponse(Failure(ex)) =>
                      complete(StatusCodes.BadRequest, FailureResponse(s"${ex.getMessage}"))
                  }
                }
              }
            }
      }
    }

}
