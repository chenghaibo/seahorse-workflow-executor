/**
 * Copyright 2015, deepsense.io
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

package io.deepsense.deeplang

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.apache.spark.SparkContext
import org.apache.spark.sql.{DataFrame => SparkDataFrame, SQLContext}

import io.deepsense.commons.models.Id
import io.deepsense.commons.utils.Logging
import io.deepsense.deeplang.CustomOperationExecutor.Result
import io.deepsense.deeplang.doperables.dataframe.DataFrameBuilder
import io.deepsense.deeplang.inference.InferContext

case class CommonExecutionContext(
    sparkContext: SparkContext,
    sqlContext: SQLContext,
    inferContext: InferContext,
    fsClient: FileSystemClient,
    tenantId: String,
    innerWorkflowExecutor: InnerWorkflowExecutor,
    dataFrameStorage: DataFrameStorage,
    pythonExecutionProvider: PythonExecutionProvider) extends Logging {

  def createExecutionContext(workflowId: Id, nodeId: Id): ExecutionContext =
    ExecutionContext(
      sparkContext,
      sqlContext,
      inferContext,
      fsClient,
      tenantId,
      innerWorkflowExecutor,
      ContextualDataFrameStorage(dataFrameStorage, workflowId, nodeId),
      ContextualPythonCodeExecutor(
        pythonExecutionProvider.pythonCodeExecutor,
        pythonExecutionProvider.customOperationExecutor,
        workflowId,
        nodeId))
}

object CommonExecutionContext {

  def apply(context: ExecutionContext): CommonExecutionContext =
    CommonExecutionContext(
      context.sparkContext,
      context.sqlContext,
      context.inferContext,
      context.fsClient,
      context.tenantId,
      context.innerWorkflowExecutor,
      context.dataFrameStorage.dataFrameStorage,
      ContextualPythonExecutorWrapper(context.pythonCodeExecutor))
}

case class ContextualPythonExecutorWrapper(contextualExecutor: ContextualPythonCodeExecutor)
  extends PythonExecutionProvider {

  override def customOperationExecutor: CustomOperationExecutor =
    contextualExecutor.customOperationExecutor

  override def pythonCodeExecutor: PythonCodeExecutor =
    contextualExecutor.pythonCodeExecutor
}

/** Holds information needed by DOperations and DMethods during execution. */
case class ExecutionContext(
    sparkContext: SparkContext,
    sqlContext: SQLContext,
    inferContext: InferContext,
    fsClient: FileSystemClient,
    tenantId: String,
    innerWorkflowExecutor: InnerWorkflowExecutor,
    dataFrameStorage: ContextualDataFrameStorage,
    pythonCodeExecutor: ContextualPythonCodeExecutor) extends Logging {

  def dataFrameBuilder: DataFrameBuilder = inferContext.dataFrameBuilder
}

case class ContextualDataFrameStorage(
    dataFrameStorage: DataFrameStorage,
    workflowId: Id,
    nodeId: Id) {

  def setInputDataFrame(portNumber: Int, dataFrame: SparkDataFrame): Unit =
    dataFrameStorage.setInputDataFrame(workflowId, nodeId, portNumber, dataFrame)

  def getOutputDataFrame(portNumber: Int): Option[SparkDataFrame] =
    dataFrameStorage.getOutputDataFrame(workflowId, nodeId, portNumber)

  def setOutputDataFrame(portNumber: Int, dataFrame: SparkDataFrame): Unit =
    dataFrameStorage.setOutputDataFrame(workflowId, nodeId, portNumber, dataFrame)
}

case class ContextualPythonCodeExecutor(
    pythonCodeExecutor: PythonCodeExecutor,
    customOperationExecutor: CustomOperationExecutor,
    workflowId: Id,
    nodeId: Id) extends Logging {

  def isValid(code: String): Boolean = pythonCodeExecutor.isValid(code)

  def run(code: String): Result = {
    val result = customOperationExecutor.execute(workflowId, nodeId)
    pythonCodeExecutor.run(workflowId.toString, nodeId.toString, code)
    logger.debug("Waiting for python operation to finish")
    Await.result(result, Duration.Inf)
  }
}
