/*
 * Copyright 2015 – 2016 Martin Seeler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rx.oanda

import akka.actor.ActorSystem
import akka.http.ConnectionPoolSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import scala.annotation.implicitNotFound
import scala.util.Try

case class OandaEnv[A <: OandaEnv.Auth](
  name: String,
  apiEndpoint: String,
  streamEndpoint: String
)
object OandaEnv {


  implicit final class OandaEnvOps[A <: Auth](private val env: OandaEnv[A]) extends AnyVal {

    def headers(implicit ev: A requires NoAuth) =
      OandaConn.headers(ev(env))

    def headers(token: String)(implicit ev: A requires WithAuth) =
      OandaConn.headers(token, ev(env))

  }


  val SandboxEnvironment =
    OandaEnv[NoAuth]("Sandbox", "api-sandbox.oanda.com", "stream-sandbox.oanda.com")

  val TradePracticeEnvironment =
    OandaEnv[WithAuth]("fxTrade Practice", "api-fxpractice.oanda.com", "stream-fxpractice.oanda.com")

  val TradeEnvironment =
    OandaEnv[WithAuth]("fxTrade", "api-fxtrade.oanda.com", "stream-fxtrade.oanda.com")


  sealed trait Auth
  sealed trait NoAuth extends Auth
  sealed trait WithAuth extends Auth


  @implicitNotFound("Nonono, ${T} requires to be ${A}")
  sealed trait requires[T <: Auth, A <: Auth] {
    def apply(x: OandaEnv[T]): OandaEnv[A]
  }
  object requires {

    implicit val requiresNoAuth: requires[NoAuth, NoAuth] =
      new requires[NoAuth, NoAuth] {
        def apply(x: OandaEnv[NoAuth]): OandaEnv[NoAuth] = x
      }
    implicit val requiresWithAuth: requires[WithAuth, WithAuth] =
      new requires[WithAuth, WithAuth] {
        def apply(x: OandaEnv[WithAuth]): OandaEnv[WithAuth] = x
      }
  }
}


object OandaConn {

  val unixTime: RawHeader = RawHeader("X-Accept-Datetime-Format", "UNIX")
  val gzipEncoding = `Accept-Encoding`(HttpEncodings.gzip)

  def headers(token: String, env: OandaEnv[OandaEnv.WithAuth]) =
    unixTime :: gzipEncoding :: Authorization(OAuth2BearerToken(token)) :: Nil

  def headers(env: OandaEnv[OandaEnv.NoAuth]) =
    unixTime :: gzipEncoding :: Nil
}

sealed trait OandaEnvironment {
  def name: String
  def apiEndpoint: String
  def streamEndpoint: String
  def authRequired: Boolean

  implicit val sys: ActorSystem
  implicit val mat: ActorMaterializer

  def headers: scala.collection.immutable.Seq[HttpHeader]

  val unixTime: RawHeader = RawHeader("X-Accept-Datetime-Format", "UNIX")
  val gzipEncoding = `Accept-Encoding`(HttpEncodings.gzip)

  val apiConnection: Flow[(HttpRequest, Any), (Try[HttpResponse], Any), HostConnectionPool]  =
    if (authRequired) {
      Http().cachedHostConnectionPoolTls[Any](host = apiEndpoint).log("api-connection")
    } else {
      Http().cachedHostConnectionPool[Any](host = apiEndpoint).log("api-connection")
    }

  val streamConnection: Flow[(HttpRequest, Any), (Try[HttpResponse], Any), HostConnectionPool]  =
    if (authRequired) {
      Http().cachedHostConnectionPoolTls[Any](host = streamEndpoint).log("stream-connection")
    } else {
      Http().cachedHostConnectionPool[Any](host = streamEndpoint).log("stream-connection")
    }
}

/**
  * An environment purely for testing; it is not as fast, stable and reliable as the other environments
  * (i.e. it can go down once in a while). Market data returned from this environment is simulated
  * (not real market data).
  */
case class SandboxEnvironment()(implicit val sys: ActorSystem, val mat: ActorMaterializer) extends OandaEnvironment {
  def name: String = "Sandbox"
  def apiEndpoint: String = "api-sandbox.oanda.com"
  def streamEndpoint: String = "stream-sandbox.oanda.com"
  def authRequired: Boolean = false

  def headers: scala.collection.immutable.Seq[HttpHeader] = unixTime :: gzipEncoding :: Nil
}

/**
  * A stable environment; recommended for testing with your fxTrade Practice
  * account and your personal access token.
  */
case class TradePracticeEnvironment(token: String)(implicit val sys: ActorSystem, val mat: ActorMaterializer) extends OandaEnvironment {
  def name: String = "fxTrade Practice"
  def apiEndpoint: String = "api-fxpractice.oanda.com"
  def streamEndpoint: String = "stream-fxpractice.oanda.com"
  def authRequired: Boolean = true

  def headers: scala.collection.immutable.Seq[HttpHeader] = unixTime :: gzipEncoding :: Authorization(OAuth2BearerToken(token)) :: Nil
}

/**
  * A stable environment; recommended for production-ready code to execute
  * with your fxTrade account and your personal access token.
  */
case class TradeEnvironment(token: String)(implicit val sys: ActorSystem, val mat: ActorMaterializer) extends OandaEnvironment {
  def name: String = "fxTrade"
  def apiEndpoint: String = "api-fxtrade.oanda.com"
  def streamEndpoint: String = "stream-fxtrade.oanda.com"
  def authRequired: Boolean = true

  def headers: scala.collection.immutable.Seq[HttpHeader] = unixTime :: gzipEncoding :: Authorization(OAuth2BearerToken(token)) :: Nil
}
