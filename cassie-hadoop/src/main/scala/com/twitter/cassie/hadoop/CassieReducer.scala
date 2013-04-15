// Copyright 2012 Twitter, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
// http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie.hadoop

import com.twitter.cassie.clocks._
import com.twitter.cassie.codecs._
import com.twitter.cassie.hadoop._
import com.twitter.cassie._
import com.twitter.conversions.time._
import com.twitter.util._
import java.nio.ByteBuffer
import org.apache.hadoop.io._
import org.apache.hadoop.mapreduce._
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.math._

object CassieReducer {
  val DEFAULT_PAGE_SIZE = 100
  val PAGE_SIZE = "page_size"
  val KEYSPACE = "keyspace"
  val COLUMN_FAMILY = "column_family"
  val HOSTS = "hosts"
  val PORT = "port"
  val MIN_BACKOFF = "min_backoff"
  val MAX_BACKOFF = "max_backoff"
  val IGNORE_FAILURES = "ignore_failures"
}

import CassieReducer._

class CassieReducer extends Reducer[BytesWritable, ColumnWritable, BytesWritable, BytesWritable] {

  val defaultReadConsistency = ReadConsistency.One
  val defaultWriteConsistency = WriteConsistency.One

  var cluster: Cluster = null
  var keyspace: Keyspace = null
  var columnFamily: ColumnFamily[ByteBuffer, ByteBuffer, ByteBuffer] = null
  var page: Int = CassieReducer.DEFAULT_PAGE_SIZE
  var i = 0
  var consecutiveFailures = 0
  var consecutiveSuccesses = 0

  var minBackoff = 1000
  var maxBackoff = 30000
  var ignoreFailures = true

  var batch: BatchMutationBuilder[ByteBuffer, ByteBuffer, ByteBuffer] = null

  type ReducerContext = Reducer[BytesWritable, ColumnWritable, BytesWritable, BytesWritable]#Context

  override def setup(context: ReducerContext) = {
    def conf(key: String) = context.getConfiguration.get(key)
    val port = if (conf(PORT) == null) 9160 else conf(PORT).toInt
    cluster = new Cluster(conf(HOSTS), port)
    cluster = configureCluster(cluster)
    if (conf(MIN_BACKOFF) != null) minBackoff = Integer.valueOf(conf(MIN_BACKOFF)).intValue
    if (conf(MAX_BACKOFF) != null) maxBackoff = Integer.valueOf(conf(MAX_BACKOFF)).intValue
    if (conf(IGNORE_FAILURES) != null) ignoreFailures = conf(IGNORE_FAILURES) == "true"
    if (conf(PAGE_SIZE) != null) page = Integer.valueOf(conf(PAGE_SIZE)).intValue

    keyspace = configureKeyspace(cluster.keyspace(conf(KEYSPACE))).connect()
    columnFamily = keyspace.columnFamily[ByteBuffer, ByteBuffer, ByteBuffer](conf(COLUMN_FAMILY),
      ByteArrayCodec, ByteArrayCodec, ByteArrayCodec)
    batch = columnFamily.batch
  }

  def configureKeyspace(c: KeyspaceBuilder): KeyspaceBuilder = {
    c.retries(2)
  }

  def configureCluster(cluster: Cluster): Cluster = {
    cluster
  }

  override def reduce(key: BytesWritable, values: java.lang.Iterable[ColumnWritable], context: ReducerContext) = {
    for (value <- values) {
      val bufKey = bufCopy(ByteBuffer.wrap(key.getBytes, 0, key.getLength))
      batch.insert(bufKey, new Column(bufCopy(value.name), bufCopy(value.value)))
      i += 1
      if (i % page == 0) {
        execute(context)
        consecutiveSuccesses += 1
        consecutiveFailures = 0
        batch = columnFamily.batch
      }
    }
  }

  private def execute(context: ReducerContext): Unit = try {
    Await.result(batch.execute)
    context.getCounter(CassieCounters.Counters.SUCCESS).increment(1)
  } catch {
    case t: Throwable => {
      t.printStackTrace
      val toSleep = minBackoff * (1 << consecutiveFailures)
      if(toSleep < maxBackoff) {
        context.progress()
        Thread.sleep(toSleep)
        context.getCounter(CassieCounters.Counters.RETRY).increment(1)
        execute(context)
      } else {
        context.getCounter(CassieCounters.Counters.FAILURE).increment(1)
        if (ignoreFailures) {
          System.err.println("Ignoring......")
          t.printStackTrace
          //continue
        } else {
          throw (t)
        }
      }
      consecutiveSuccesses = 0
      consecutiveFailures += 1
    }
  }

  private def bufCopy(old: ByteBuffer) = {
    val n = ByteBuffer.allocate(old.remaining)
    n.put(old.array, old.position, old.remaining)
    n.rewind
    n
  }

  override def cleanup(context: ReducerContext) = execute(context)
}
