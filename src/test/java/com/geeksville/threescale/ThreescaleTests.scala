package com.geeksville.threescale

import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter

class ThreescaleTests extends FunSuite {

  test("errors") {
    ThreeActor.makeErrorResponse("testing")
  }

}