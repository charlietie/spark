/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.v2

import java.util.Objects

import org.apache.commons.lang3.StringUtils

import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.sources.v2.DataSourceV2
import org.apache.spark.sql.sources.v2.reader._
import org.apache.spark.util.Utils

/**
 * A base class for data source v2 related query plan(both logical and physical). It defines the
 * equals/hashCode methods, and provides a string representation of the query plan, according to
 * some common information.
 */
trait DataSourceV2QueryPlan {

  /**
   * The output of the data source reader, w.r.t. column pruning.
   */
  def output: Seq[Attribute]

  /**
   * The instance of this data source implementation. Note that we only consider its class in
   * equals/hashCode, not the instance itself.
   */
  def source: DataSourceV2

  /**
   * The created data source reader. Here we use it to get the filters that has been pushed down
   * so far, itself doesn't take part in the equals/hashCode.
   */
  def reader: DataSourceReader

  private lazy val filters = reader match {
    case s: SupportsPushDownCatalystFilters => s.pushedCatalystFilters().toSet
    case s: SupportsPushDownFilters => s.pushedFilters().toSet
    case _ => Set.empty
  }

  /**
   * The metadata of this data source query plan that can be used for equality check.
   */
  private def metadata: Seq[Any] = Seq(output, source.getClass, filters)

  def canEqual(other: Any): Boolean

  override def equals(other: Any): Boolean = other match {
    case other: DataSourceV2QueryPlan => canEqual(other) && metadata == other.metadata
    case _ => false
  }

  override def hashCode(): Int = {
    metadata.map(Objects.hashCode).foldLeft(0)((a, b) => 31 * a + b)
  }

  def metadataString: String = {
    val entries = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
    if (filters.nonEmpty) entries += "PushedFilter" -> filters.mkString("[", ", ", "]")

    val outputStr = Utils.truncatedString(output, "[", ", ", "]")

    val entriesStr = if (entries.nonEmpty) {
      Utils.truncatedString(entries.map {
        case (key, value) => key + ": " + StringUtils.abbreviate(redact(value), 100)
      }, " (", ", ", ")")
    } else {
      ""
    }

    s"${source.getClass.getSimpleName}$outputStr$entriesStr"
  }

  private def redact(text: String): String = {
    Utils.redact(SQLConf.get.stringRedationPattern, text)
  }
}
