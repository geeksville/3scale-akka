package com.geeksville.threescale

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import threescale.v3.api._
import scala.collection._
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration._

/**
 * An async threescale client.
 * FIXME - add caching of the results (use EHCache or similar and key based on service-id and apikey)
 *
 * Usage, keep a singleton of this actor, then send it AuthRequests.  It will reply with AuthorizeResponse
 */
class ThreeActor(providerKey: Option[String], whitelistIn: Seq[WhitelistApp] = Seq()) extends Actor with ActorLogging {
  private val api = new ThreeAPI(providerKey, whitelistIn)

  if (!providerKey.isDefined)
    log.error("WARNING: Simulating 3scale because no provider key was specified")

  private def ask3scale(req: AuthRequest) = blocking {
    val resp = try {
      api.authorize(req)
    } catch {
      case ex: ServerError =>
        new AuthorizeResponse(300, "API validator unreachable")
    }

    // If the server wasn't sure, don't cache anything

    // If the server said the user was bad, don't cache that either, because
    // that is a very uncommon case and if the user adds money to their acct
    // we want things to start working right away.

    // FIXME - handle server outages by warning sysadmin and just allowing the API call to proceed
    resp
  }

  override def receive = {
    case req: AuthRequest =>
      sender ! ask3scale(req)
  }

}
