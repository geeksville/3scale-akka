package com.geeksville.threescale

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import threescale.v3.api._
import scala.collection._
import scala.collection.JavaConverters._
import scala.concurrent._
import scala.concurrent.duration._
import threescale.v3.api.impl.ServiceApiDriver
import scala.xml._

/**
 * @param referer The hostname that is actually making the request
 */
case class AuthRequest(userKey: String, serviceId: String, referer: Option[String], metrics: Map[String, String] = Map())

trait WhitelistChecker {
  def appKey: String

  /**
   * Return success, failure or I don't know
   */
  def authorize(request: AuthRequest): Option[Boolean]
}

/**
 * If an appkey is listed in a whitelist and the referer matches, then we don't even send the request to
 * threescale.  We handle locally (for speed, robustness and price).  If the referrer doesn't match we ignore
 * the request (so it can go to threescale)
 */
case class WhitelistPossibly(appKey: String, referers: String*) extends WhitelistChecker {

  def authorize(request: AuthRequest): Option[Boolean] = {
    for {
      referer <- request.referer
    } yield {
      referers.find(referer.startsWith).isDefined
    }
  }
}

/**
 * If an appkey is listed in a whitelist and the referer matches, then we don't even send the request to
 * threescale.  We handle locally (for speed, robustness and price).  If the referrer doesn't match we disallow the request.
 */
case class WhitelistStrict(appKey: String, referers: String*) extends WhitelistChecker {

  def authorize(request: AuthRequest): Option[Boolean] = {

    val referer = request.referer.getOrElse("invalid")
    val r = referers.find(referer.startsWith).isDefined
    if (!r)
      println(s"*** Using whitelist strict with $r, referrer=$referer")
    Some(r)
  }
}

/**
 * A special purpose whitelister that always says yes
 */
case class WhitelistOkay(appKey: String) extends WhitelistChecker {

  def authorize(request: AuthRequest): Option[Boolean] = {
    val referer = request.referer.getOrElse("invalid")
    println(s"*** Using whitelist okay with referrer $referer")

    Some(true)
  }
}

/**
 * A simple (non Actor) scala style wrapper for the threescale API
 * If a provider key is not supplied we will only _simulate_ the 3scale API
 */
class ThreeAPI(providerKey: Option[String], whitelistIn: Seq[WhitelistChecker] = Seq()) {
  val whitelist = Map(whitelistIn.map { r =>
    r.appKey -> r
  }: _*)

  val serviceApi = providerKey.map(new ServiceApiDriver(_))

  private def makeResponse(authorized: Boolean, reason: String) = {
    val payload = <root>
                    <authorized>{ authorized }</authorized>
                    <reason>{ reason }</reason>
                    <plan>Simulated</plan>
                  </root>.toString

    new AuthorizeResponse(200, payload)
  }

  private def localApproval(reason: String) = makeResponse(true, reason)
  private def localDenial(reason: String) = makeResponse(false, reason)

  private val AppRegex = "(.*)\\.(.*)".r

  /**
   * Can the client app call this API?
   */
  def authorize(request: AuthRequest): AuthorizeResponse = {
    val params = new ParameterMap()

    val handleLocal = (for {
      whitelist <- whitelist.get(request.userKey)
    } yield {
      val r = whitelist.authorize(request)
      //println(s"*** Can local shortcut for $referer yields $r")
      r.getOrElse(false)
    })

    if (handleLocal.isDefined) {
      if (handleLocal.get)
        localApproval(s"local whitelist referer=$request.referer")
      else
        localDenial(s"Invalid key for your host, are you using your API key?")
    } else {
      // If the name contains a dot, we assume we are using appid.appkey convention.  Otherwise just a simple user ide

      request.userKey match {
        case AppRegex(appId, appKey) =>
          params.add("app_id", appId)
          params.add("app_key", appKey)
        case x @ _ =>
          params.add("user_key", x)
      }

      params.add("service_id", request.serviceId)

      if (!request.metrics.isEmpty) {
        val usage = new ParameterMap()
        request.metrics.foreach {
          case (k, v) =>
            usage.add(k, v)
        }
        params.add("usage", usage)
      }

      serviceApi match {
        case Some(s) => s.authrep(params)
        case None => localApproval("no 3scale api key provided")
      }
    }
  }
}

