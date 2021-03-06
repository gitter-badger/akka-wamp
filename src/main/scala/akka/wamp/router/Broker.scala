package akka.wamp.router

import akka.actor._
import akka.wamp.Tpe._
import akka.wamp.Wamp._
import akka.wamp._
import akka.wamp.messages._

/**
  * A Broker routes events incoming from Publishers to Subscribers 
  * that are subscribed to respective Topics
  */
trait Broker extends Role { this: Router =>

  /**
    * Map of subscriptions. Each entry is for one topic only 
    * and it can have one or many subscribers
    */
  var subscriptions = Map.empty[Id, Subscription]
  
  /**
    * Set of publication IDs
    */
  var publications = Set.empty[Id]
  

  /**
    * Handle PUBLISH and EVENT messages
    */
  def handlePublications: Receive = {
    case Publish(requestId, topic, payload, options) =>
      ifSessionOpen { session =>
        val publisher = session.transport
        if (session.roles.contains("publisher")) {
          /**
            * By default, publications are unacknowledged, and the Broker will
            * not respond, whether the publication was successful indeed or not.
            * This behavior can be changed with the option
            *
            * "PUBLISH.Options.acknowledge|bool"
            */
          val ack = options.get("acknowledge") == Some(true)
          subscriptions.values.toList.filter(_.topic == topic) match {
            case Nil =>
              /**
                * Actually, no subscribers has subscribed to the given topic.
                * When the request for publication cannot be fulfilled by the Broker,
                * and "PUBLISH.Options.acknowledge == true", the Broker sends back an
                * "ERROR" message to the Publisher
                */
              if (ack) publisher ! Error(PUBLISH, requestId, Dict(), "wamp.error.no_such_topic")
              ()
            case subscription :: Nil =>

              /**
                * When a publication is successful and a Broker dispatches the event,
                * it determines a list of receivers for the event based on subscribers
                * for the topic published to and, possibly, other information in the event.
                *
                * Note that the publisher of an event will never receive the published 
                * event even if the publisher is also a subscriber of the topic published to.
                */
              val publicationId = nextId(scopes('global), excludes = publications)
              subscription.subscribers.filter(_ != publisher).foreach { subscriber =>
                publications += publicationId
                subscriber ! Event(subscription.id, publicationId, Dict(), payload)
              }
              if (ack) publisher ! Published(requestId, publicationId)
            case _ => throw new IllegalStateException()
          }
        }
        else {
          publisher ! Error(PUBLISH, requestId, Dict(), "akka.wamp.error.no_publisher_role")
        }
      }
  }

  /**
    * Handle SUBSCRIBE and UNSUBSCRIBE messages
    */
  def handleSubscriptions: Receive = {

    case Subscribe(requestId, topic, options) =>
      ifSessionOpen { session =>
        val subscriber = session.transport
        if (session.roles.contains("subscriber")) {
          subscriptions.values.toList.filter(_.topic == topic) match {
            case Nil => {
              /**
                * No subscribers has subscribed to the given topic yet.
                */
              val subscriptionId = nextId(scopes('router), excludes = subscriptions.keySet)
              subscriptions += (subscriptionId -> new Subscription(subscriptionId, Set(subscriber), topic))
              subscriber ! Subscribed(requestId, subscriptionId)
            }
            case subscription :: Nil => {
              if (!subscription.subscribers.contains(subscriber)) {
                /**
                  * In case of receiving a SUBSCRIBE message from a client to the 
                  * topic already subscribed by others, broker should update the 
                  * subscribers set of the existing subscription and answer with 
                  * SUBSCRIBED message, containing the existing subscription ID. 
                  */
                subscriptions += (subscription.id -> subscription.copy(subscribers = subscription.subscribers + subscriber))
              }
              else {
                /**
                  * In case of receiving a SUBSCRIBE message from the same subscriber 
                  * to already subscribed topic, broker should answer with 
                  * SUBSCRIBED message, containing the existing subscription ID.
                  */
              }
              subscriber ! Subscribed(requestId, subscription.id)
            }
            case _ => throw new IllegalStateException()
          }
        }
        else {
          subscriber ! Error(SUBSCRIBE, requestId, Dict(), "akka.wamp.error.no_subscriber_role")
        }

      }

    case Unsubscribe(requestId, subscriptionId) =>
      ifSessionOpen { session =>
        subscriptions.get(subscriptionId) match {
          case Some(subscription) =>
            unsubscribe(session.transport, subscription)
            session.transport ! Unsubscribed(requestId)
          case None =>
            session.transport ! Error(UNSUBSCRIBE, requestId, Dict(), "wamp.error.no_such_subscription")
        }
      }
  }


  def ifSessionOpen(fn: (Session) => Unit): Unit = {
    switchOn(sender())(
      whenSessionOpen = { session =>
        fn(session)
      },
      otherwise = { _ =>
        sender() ! Failure("Session was not open yet.")
      }
    )
  }


  def unsubscribe(client: ActorRef, subscription: Subscription) = {
    if (subscription.subscribers.contains(client)) {
      if (subscription.subscribers.size == 1) {
        subscriptions -= subscription.id
      } else {
        subscriptions += (subscription.id -> subscription.copy(subscribers = subscription.subscribers - client))
      }
    }
  }

}

