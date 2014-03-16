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

case class AuthRequest(apiKey: String, serviceId: String, metrics: Map[String, String] = Map())

/**
 * A simple (non Actor) scala style wrapper for the threescale API
 */
class ThreeAPI(providerKey: String) {
  val serviceApi = new ServiceApiDriver(providerKey)

  /**
   * Can the client app call this API?
   */
  def authorize(request: AuthRequest): AuthorizeResponse = {
    val params = new ParameterMap()
    params.add("user_key", request.apiKey)
    params.add("service_id", request.serviceId)

    if (!request.metrics.isEmpty) {
      val usage = new ParameterMap()
      request.metrics.foreach {
        case (k, v) =>
          usage.add(k, v)
      }
      params.add("usage", usage)
    }
    serviceApi.authrep(params)
  }
}

