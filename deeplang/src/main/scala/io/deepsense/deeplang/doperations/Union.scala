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

package io.deepsense.deeplang.doperations

import scala.reflect.runtime.{universe => ru}

import org.apache.spark.sql.types.StructType

import io.deepsense.commons.utils.Version
import io.deepsense.deeplang.DOperation.Id
import io.deepsense.deeplang.doperables.dataframe.DataFrame
import io.deepsense.deeplang.doperations.exceptions.SchemaMismatchException
import io.deepsense.deeplang.inference.{InferContext, InferenceWarnings}
import io.deepsense.deeplang.params.Params
import io.deepsense.deeplang.{DKnowledge, DOperation2To1, ExecutionContext}

case class Union() extends DOperation2To1[DataFrame, DataFrame, DataFrame] with Params {

  override val id: Id = "90fed07b-d0a9-49fd-ae23-dd7000a1d8ad"
  override val name: String = "Union"
  override val description: String =
    "Creates a DataFrame containing all rows from both input DataFrames"

  override val since: Version = Version(0, 4, 0)

  val params = declareParams()

  override protected def _execute(
      context: ExecutionContext)(first: DataFrame, second: DataFrame): DataFrame = {

    checkSchemaMismatch(first.schema.get, second.schema.get)

    context.dataFrameBuilder.buildDataFrame(
      first.schema.get,
      first.sparkDataFrame.unionAll(second.sparkDataFrame).rdd)
  }

  override protected def _inferKnowledge
      (context: InferContext)
      (df1Knowledge: DKnowledge[DataFrame],
        df2Knowledge: DKnowledge[DataFrame]): (DKnowledge[DataFrame], InferenceWarnings) = {

    val df1Schema = df1Knowledge.single.schema
    val df2Schema = df2Knowledge.single.schema

    if (df1Schema.isDefined && df2Schema.isDefined) {
      checkSchemaMismatch(df1Schema.get, df2Schema.get)
      (df1Knowledge, InferenceWarnings())
    } else {
      (DKnowledge(DataFrame.forInference()), InferenceWarnings())
    }
  }

  private def checkSchemaMismatch(first: StructType, second: StructType): Unit = {
    if (first.treeString != second.treeString) {
      throw new SchemaMismatchException(
        "SchemaMismatch. Expected schema " +
          s"${first.treeString}" +
          s" differs from ${second.treeString}")
    }
  }

  @transient
  override lazy val tTagTI_0: ru.TypeTag[DataFrame] = ru.typeTag[DataFrame]
  @transient
  override lazy val tTagTI_1: ru.TypeTag[DataFrame] = ru.typeTag[DataFrame]
  @transient
  override lazy val tTagTO_0: ru.TypeTag[DataFrame] = ru.typeTag[DataFrame]
}
