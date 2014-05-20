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
 * threescale.  We handle locally (for speed, robustness and price)
 */
case class WhitelistApp(appKey: String, referer: String*) extends WhitelistChecker {
  private val refererSet = Set(referer: _*)

  def authorize(request: AuthRequest): Option[Boolean] = {
    for {
      referer <- request.referer
    } yield {
      refererSet.contains(referer)
    }
  }
}

/**
 * A special purpose whitelister that always says yes
 */
case class WhitelistOkay(appKey: String) extends WhitelistChecker {

  def authorize(request: AuthRequest): Option[Boolean] = {
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

  def localApproval(reason: String) = {
    val payload = <root>
                    <authorized>true</authorized>
                    <reason>Local approval - { reason }</reason>
                    <plan>Simulated</plan>
                  </root>.toString

    new AuthorizeResponse(200, payload)
  }

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
    }).getOrElse(false)

    if (handleLocal) {
      localApproval(s"local whitelist referer=$request.referer")
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

