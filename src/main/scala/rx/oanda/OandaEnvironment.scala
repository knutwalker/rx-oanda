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
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.Materializer
import akka.stream.scaladsl._

import scala.annotation.implicitNotFound
import scala.util.Try


case class OandaEnvironment[A <: OandaEnvironment.Auth](
  name: String,
  apiEndpoint: String,
  streamEndpoint: String,
  token: Option[String] = None
)

object OandaEnvironment {

  sealed trait Auth
  sealed trait NoAuth extends Auth
  sealed trait WithAuth extends Auth

  @implicitNotFound("Nonono, ${T} requires to be ${A}")
  sealed trait requires[T <: Auth, A <: Auth] {
    def apply(x: OandaEnvironment[T]): OandaEnvironment[A]
  }

  object requires {

    implicit val requiresNoAuth: requires[NoAuth, NoAuth] =
      new requires[NoAuth, NoAuth] {
        def apply(x: OandaEnvironment[NoAuth]): OandaEnvironment[NoAuth] = x
      }
    implicit val requiresWithAuth: requires[WithAuth, WithAuth] =
      new requires[WithAuth, WithAuth] {
        def apply(x: OandaEnvironment[WithAuth]): OandaEnvironment[WithAuth] = x
      }
  }

  @implicitNotFound("No ConnectionFlow for ${A}")
  trait ApiFlow[A <: Auth] {

    def apply[T](endpoint: String)(implicit mat: Materializer, system: ActorSystem):
      Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]

  }

  object ApiFlow {

    implicit val AuthApiFlow: ApiFlow[WithAuth] = new ApiFlow[WithAuth] {
      def apply[T](endpoint: String)(implicit mat: Materializer, system: ActorSystem): Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool] =
        Http().cachedHostConnectionPoolTls[T](host = endpoint).log("auth-connection")
    }

    implicit val NoAuthApiFlow: ApiFlow[NoAuth] = new ApiFlow[NoAuth] {
      def apply[T](endpoint: String)(implicit mat: Materializer, system: ActorSystem): Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool] =
        Http().cachedHostConnectionPool[T](host = endpoint).log("no-auth-connection")
    }

  }

  val unixTime: HttpHeader = RawHeader("X-Accept-Datetime-Format", "UNIX")
  val gzipEncoding: HttpHeader = `Accept-Encoding`(HttpEncodings.gzip)

  implicit final class OandaEnvironmentOps[A <: Auth](private val env: OandaEnvironment[A]) extends AnyVal {

    def headers =
      unixTime :: gzipEncoding :: env.token.map(t ⇒ Authorization(OAuth2BearerToken(t))).toList

    def connectionFlow[T](endpoint: String)(implicit A: ApiFlow[A], mat: Materializer, sys: ActorSystem) = A.apply[T](endpoint)

  }


  val SandboxEnvironment =
    OandaEnvironment[NoAuth]("Sandbox", "api-sandbox.oanda.com", "stream-sandbox.oanda.com")

  def TradePracticeEnvironment(token: String) =
    OandaEnvironment[WithAuth]("fxTrade Practice", "api-fxpractice.oanda.com", "stream-fxpractice.oanda.com", Some(token))

  def TradeEnvironment(token: String) =
    OandaEnvironment[WithAuth]("fxTrade", "api-fxtrade.oanda.com", "stream-fxtrade.oanda.com", Some(token))

}

