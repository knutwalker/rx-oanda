package rx.oanda

import org.scalatest.{ Matchers, FlatSpec }

final class BerndTest extends FlatSpec with Matchers {

  import OandaConn._

  behavior of "Bernd"

  it must "compile" in {
    OandaEnv.SandboxEnvironment.headers should be (unixTime :: gzipEncoding :: Nil)
    OandaEnv.TradePracticeEnvironment.headers("blubb")
    OandaEnv.TradeEnvironment.headers("bla")
  }

  it must "not compile" in {
    "OandaEnv.SandboxEnvironment.headers(\"adfds\")" shouldNot typeCheck
    "OandaEnv.TradePracticeEnvironment.headers" shouldNot typeCheck
    "OandaEnv.TradeEnvironment.headers" shouldNot typeCheck
  }

}
