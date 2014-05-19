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

/**
 * If an appkey is listed in a whitelist and the referer matches, then we don't even send the request to
 * threescale.  We handle locally (for speed, robustness and price)
 */
case class WhitelistApp(appKey: String, referer: String*)

/**
 * A simple (non Actor) scala style wrapper for the threescale API
 * If a provider key is not supplied we will only _simulate_ the 3scale API
 */
class ThreeAPI(providerKey: Option[String], whitelistIn: Seq[WhitelistApp] = Seq()) {
  val whitelist = Map(whitelistIn.map { r =>
    r.appKey -> Set(r.referer: _*)
  }: _*)

  val serviceApi = providerKey.map(new ServiceApiDriver(_))

  def localApproval(reason: String) = {
    val payload = <root>
                    <authorized>true</authorized>
                    <reason>Local approval - {{rexason}}</reason>
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
      validReferers <- whitelist.get(request.userKey)
      referer <- request.referer
    } yield {
      val r = validReferers.contains(referer)
      //println(s"*** Can local shortcut for $referer yields $r")
      r
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

