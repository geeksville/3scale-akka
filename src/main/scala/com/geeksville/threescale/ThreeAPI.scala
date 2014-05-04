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

case class AuthRequest(userKey: String, serviceId: String, metrics: Map[String, String] = Map())

/**
 * A simple (non Actor) scala style wrapper for the threescale API
 * If a provider key is not supplied we will only _simulate_ the 3scale API
 */
class ThreeAPI(providerKey: Option[String]) {
  val serviceApi = providerKey.map(new ServiceApiDriver(_))

  val simulatedApproval =
    <root>
      <authorized>true</authorized>
      <reason>Simulated approval - no 3scale api key provided</reason>
      <plan>Simulated</plan>
    </root>.toString

  private val AppRegex = "(.*)\\.(.*)".r

  /**
   * Can the client app call this API?
   */
  def authorize(request: AuthRequest): AuthorizeResponse = {
    val params = new ParameterMap()
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
      case None => new AuthorizeResponse(200, simulatedApproval)
    }
  }
}

