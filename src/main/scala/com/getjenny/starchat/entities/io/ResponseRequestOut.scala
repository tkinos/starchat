package com.getjenny.starchat.entities.io

import spray.json.JsObject

import scala.collection.immutable.Map

case class ResponseRequestOut(conversationId: String,
                              state: String,
                              traversedStates: Vector[String],
                              maxStateCount: Int,
                              analyzer: String,
                              bubble: String,
                              action: String,
                              data: Map[String, String],
                              actionInput: Seq[JsObject],
                              stateData: Map[String, String],
                              successValue: String,
                              failureValue: String,
                              score: Double,
                              actionResult: Option[DtActionResult] = None
                             )
