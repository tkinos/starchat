package com.getjenny.starchat.services

/**
  * Created by Angelo Leto <angelo@getjenny.com> on 22/11/17.
  */

import java.io._

import akka.event.{Logging, LoggingAdapter}
import com.getjenny.starchat.SCActorSystem
import com.getjenny.starchat.entities.{IndexManagementResponse, _}
import com.getjenny.starchat.services.esclient.SystemIndexManagementElasticClient
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.indices.create.{CreateIndexRequest, CreateIndexResponse}
import org.elasticsearch.action.admin.indices.delete.{DeleteIndexRequest, DeleteIndexResponse}
import org.elasticsearch.action.admin.indices.mapping.get.{GetMappingsRequest, GetMappingsResponse}
import org.elasticsearch.action.admin.indices.mapping.put.{PutMappingRequest, PutMappingResponse}
import org.elasticsearch.client.{RequestOptions, RestHighLevelClient}
import org.elasticsearch.common.settings._
import org.elasticsearch.common.xcontent.XContentType
import scalaz.Scalaz._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

case class SystemIndexManagementServiceException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

/**
  * Implements functions, eventually used by IndexManagementResource, for ES index management
  */
object SystemIndexManagementService extends AbstractDataService {
  val elasticClient: SystemIndexManagementElasticClient.type = SystemIndexManagementElasticClient
  private[this] val log: LoggingAdapter = Logging(SCActorSystem.system, this.getClass.getCanonicalName)

  private[this] val analyzerJsonPath: String = "/index_management/json_index_spec/system/analyzer.json"
  private[this] val analyzerJsonIs: Option[InputStream] = Option{getClass.getResourceAsStream(analyzerJsonPath)}
  private[this] val analyzerJson: String = analyzerJsonIs match {
    case Some(stream) => Source.fromInputStream(stream, "utf-8").mkString
    case _ =>
      val message = "Check the file: (" + analyzerJsonPath + ")"
      throw new FileNotFoundException(message)
  }

  private[this] val schemaFiles: List[JsonMappingAnalyzersIndexFiles] = List[JsonMappingAnalyzersIndexFiles](
    JsonMappingAnalyzersIndexFiles(path = "/index_management/json_index_spec/system/user.json",
      updatePath = "/index_management/json_index_spec/system/update/user.json",
      indexSuffix = elasticClient.userIndexSuffix),
    JsonMappingAnalyzersIndexFiles(path = "/index_management/json_index_spec/system/refresh_decisiontable.json",
      updatePath = "/index_management/json_index_spec/system/update/refresh_decisiontable.json",
      indexSuffix = elasticClient.systemRefreshDtIndexSuffix)
  )

  def create(indexSuffix: Option[String] = None) : Future[IndexManagementResponse] = Future {
    val client: RestHighLevelClient = elasticClient.client

    val operationsMessage: List[String] = schemaFiles.filter(item => {
      indexSuffix match {
        case Some(t) => t === item.indexSuffix
        case _ => true
      }
    }).map(item => {
      val jsonInStream: Option[InputStream] = Option {getClass.getResourceAsStream(item.path)}

      val schemaJson = jsonInStream match {
        case Some(stream) => Source.fromInputStream(stream, "utf-8").mkString
        case _ =>
          val message = "Check the file: (" + item.path + ")"
          throw new FileNotFoundException(message)
      }

      val fullIndexName = elasticClient.indexName + "." + item.indexSuffix

      val createIndexReq = new CreateIndexRequest(fullIndexName)
        .settings(Settings.builder().loadFromSource(analyzerJson, XContentType.JSON))
        .source(schemaJson, XContentType.JSON)

      val createIndexRes: CreateIndexResponse = client.indices.create(createIndexReq, RequestOptions.DEFAULT)

      item.indexSuffix + "(" + fullIndexName + ", " + createIndexRes.isAcknowledged.toString + ")"
    })

    val message = "IndexCreation: " + operationsMessage.mkString(" ")

    IndexManagementResponse(message)
  }

  def remove(indexSuffix: Option[String] = None) : Future[IndexManagementResponse] = Future {
    val client: RestHighLevelClient = elasticClient.client

    if (! elasticClient.enableDeleteSystemIndex) {
      val message: String = "operation is not allowed, contact system administrator"
      throw SystemIndexManagementServiceException(message)
    }

    val operationsMessage: List[String] = schemaFiles.filter(item => {
      indexSuffix match {
        case Some(t) => t === item.indexSuffix
        case _ => true
      }
    }).map(item => {
      val fullIndexName = elasticClient.indexName + "." + item.indexSuffix

      val deleteIndexReq = new DeleteIndexRequest(fullIndexName)

      val deleteIndexRes: DeleteIndexResponse = client.indices.delete(deleteIndexReq, RequestOptions.DEFAULT)

      item.indexSuffix + "(" + fullIndexName + ", " + deleteIndexRes.isAcknowledged.toString + ")"

    })

    val message = "IndexDeletion: " + operationsMessage.mkString(" ")

    IndexManagementResponse(message)
  }

  def check(indexSuffix: Option[String] = None) : Future[IndexManagementResponse] = Future {
    val client: RestHighLevelClient = elasticClient.client

    val operationsMessage: List[String] = schemaFiles.filter(item => {
      indexSuffix match {
        case Some(t) => t === item.indexSuffix
        case _ => true
      }
    }).map(item => {
      val fullIndexName = elasticClient.indexName + "." + item.indexSuffix
      val getMappingsReq: GetMappingsRequest = new GetMappingsRequest()
        .indices(fullIndexName)

      val getMappingsRes: GetMappingsResponse = client.indices.getMapping(getMappingsReq, RequestOptions.DEFAULT)

      val check = getMappingsRes.mappings.containsKey(fullIndexName)
      item.indexSuffix + "(" + fullIndexName + ", " + check + ")"
    })

    val message = "IndexCheck: " + operationsMessage.mkString(" ")

    IndexManagementResponse(message)
  }

  def update(indexSuffix: Option[String] = None) : Future[IndexManagementResponse] = Future {
    val client: RestHighLevelClient = elasticClient.client

    val operationsMessage: List[String] = schemaFiles.filter(item => {
      indexSuffix match {
        case Some(t) => t === item.indexSuffix
        case _ => true
      }
    }).map(item => {
      val jsonInStream: Option[InputStream] = Option{getClass.getResourceAsStream(item.updatePath)}
      val schemaJson: String = jsonInStream match {
        case Some(stream) => Source.fromInputStream(stream, "utf-8").mkString
        case _ =>
          val message = "Check the file: (" + item.path + ")"
          throw new FileNotFoundException(message)
      }

      val fullIndexName = elasticClient.indexName + "." + item.indexSuffix

      val putMappingReq = new PutMappingRequest().indices(fullIndexName)
        .`type`(item.indexSuffix)
        .source(schemaJson, XContentType.JSON)

      val putMappingRes: PutMappingResponse = client.indices
        .putMapping(putMappingReq, RequestOptions.DEFAULT)

      item.indexSuffix + "(" + fullIndexName + ", " + putMappingRes.isAcknowledged.toString + ")"
    })

    val message = "IndexUpdate: " + operationsMessage.mkString(" ")

    IndexManagementResponse(message)
  }

  def refresh(indexSuffix: Option[String] = None) : Future[Option[RefreshIndexResults]] = Future {
    val operationsResults: List[RefreshIndexResult] = schemaFiles.filter(item => {
      indexSuffix match {
        case Some(t) => t === item.indexSuffix
        case _ => true
      }
    }).map(item => {
      val fullIndexName = elasticClient.indexName + "." + item.indexSuffix
      val refreshIndexRes: RefreshIndexResult = elasticClient.refresh(fullIndexName)
      if (refreshIndexRes.failed_shards_n > 0) {
        val indexRefreshMessage = item.indexSuffix + "(" + fullIndexName + ", " + refreshIndexRes.failed_shards_n + ")"
        throw SystemIndexManagementServiceException(indexRefreshMessage)
      }

      refreshIndexRes
    })

    Option { RefreshIndexResults(results = operationsResults) }
  }

  def indices: Future[List[String]] = Future {
    val clusterHealthReq = new ClusterHealthRequest()
    val clusterHealthRes = elasticClient.client.cluster.health(clusterHealthReq, RequestOptions.DEFAULT)
    clusterHealthRes.getIndices.asScala.map{ case(k, v) => k }.toList
  }


}
