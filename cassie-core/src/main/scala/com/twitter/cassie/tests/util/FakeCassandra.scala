package com.twitter.cassie.tests.util

import org.apache.thrift.transport.{TServerSocket, TFramedTransport}
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.server.TThreadPoolServer
import java.util.concurrent.CountDownLatch
import org.apache.cassandra.finagle.thrift._
import java.nio.ByteBuffer
import java.util._
import org.apache.cassandra.db.marshal.BytesType
import scala.collection.JavaConversions._

object FakeCassandra {
  class ServerThread(cassandra: Cassandra.Iface, port: Int) extends Thread {
    setDaemon(true)
    val serverTransport = new TServerSocket(port)
    val protFactory = new TBinaryProtocol.Factory(true, true)
    val transportFactory = new TFramedTransport.Factory()
    val processor = new Cassandra.Processor(cassandra)
    val server = new TThreadPoolServer(processor, serverTransport, transportFactory, protFactory)
    val latch = new CountDownLatch(1)

    override def run {
      latch.countDown()
      server.serve()
    }
  }
}

/** 
 * This is a thrift-service that will do real operations on an in-memory data structure.  
 * You don't have to create keyspaces or column families; this happens implicitly.  We 
 * support a limited and expanding subset of the cassandra api.
 */
class FakeCassandra(val port: Int) extends Cassandra.Iface {
  val comparator = BytesType.instance
  val columnComparator = new Comparator[Column] {
    def compare(a: Column, b: Column) = comparator.compare(a.BufferForName, b.BufferForName)
  }
  
  val thread = new FakeCassandra.ServerThread(this, port)
  var currentKeyspace = "default"

  //                     keyspace        CF              row         column
  val data = new TreeMap[String, TreeMap[String, TreeMap[ByteBuffer, SortedSet[Column]]]]
  
  def start() = {
    thread.start()
    thread.latch.await()
  }
  
  private def getColumnFamily(cp: ColumnParent): TreeMap[ByteBuffer, SortedSet[Column]] = getColumnFamily(cp.getColumn_family)
  private def getColumnFamily(name: String): TreeMap[ByteBuffer, SortedSet[Column]]  = synchronized {
    var keyspace = data.get(currentKeyspace)
    if (keyspace == null) {
      keyspace = new TreeMap[String, TreeMap[ByteBuffer, SortedSet[Column]]]
      data.put(currentKeyspace, keyspace)
    }
    var cf = keyspace.get(name)
    if (cf == null) {
      cf = new TreeMap[ByteBuffer, SortedSet[Column]](comparator)
      keyspace.put(name, cf)
    }
    cf
  }
  
  def stop() = thread.server.stop()
  
  def set_keyspace(keyspace: String) = currentKeyspace = keyspace

  def insert(key: ByteBuffer, column_parent: ColumnParent, column: Column, consistency_level: ConsistencyLevel) = {
    val cf = getColumnFamily(column_parent)
    var row = cf.get(key)
    if(row == null) {
      row = new TreeSet[Column](columnComparator)
      cf.put(key, row)
    }
    row.add(column)
  }
  
  def get_slice(key: ByteBuffer, column_parent: ColumnParent, predicate: SlicePredicate, consistency_level: ConsistencyLevel) = {
    val cf = getColumnFamily(column_parent)
    var row = cf.get(key)
    val list = new ArrayList[ColumnOrSuperColumn]
    def add(col: Column) = {
      val cosc = new ColumnOrSuperColumn
      cosc.setColumn(col)
      list.add(cosc)
    }
    if (row != null) {
      if (predicate.isSetColumn_names()) {
        throw new UnsupportedOperationException
      } else if (predicate.isSetSlice_range()) {
        val sr = predicate.getSlice_range
        val start = new Column
        val finish = new Column
        start.setName(if(sr.isSetStart) sr.getStart else Array.empty[Byte])
        if(sr.isSetFinish && sr.getFinish.length > 0) {
          finish.setName(sr.getFinish)
          row = row.subSet(start, finish)
        } else {
          row = row.tailSet(start)
        }
        
        var limit = if (sr.isSetCount && sr.getCount > 0) sr.getCount else Int.MaxValue
        var i = 0
        for(entry <- row) {
          if(i < limit) {
            add(entry)
            i += 1
          }
        }
      } else {
        for(entry <- row) {
          add(entry)
        }        
      }
    }
    list
  }
  

  def login(auth_request: AuthenticationRequest ) = throw new UnsupportedOperationException
  def get(key: ByteBuffer, column_path: ColumnPath, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def get_count(key: ByteBuffer, column_parent: ColumnParent, predicate: SlicePredicate, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def multiget_slice(keys: List[ByteBuffer], column_parent: ColumnParent, predicate: SlicePredicate, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def multiget_count(keys: List[ByteBuffer], column_parent: ColumnParent, predicate: SlicePredicate, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def get_range_slices(column_parent: ColumnParent, predicate: SlicePredicate, range: KeyRange, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def get_indexed_slices(column_parent: ColumnParent, index_clause: IndexClause, column_predicate: SlicePredicate, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException

  def remove(key: ByteBuffer, column_path: ColumnPath, timestamp: Long, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def batch_mutate(mutation_Map: Map[ByteBuffer,Map[String,List[Mutation]]], consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def truncate(cfname: String) = throw new UnsupportedOperationException
  def add(key: ByteBuffer, column_parent: ColumnParent, column: CounterColumn, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def batch_add(update_Map: Map[ByteBuffer,Map[String,List[CounterMutation]]], consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def get_counter(key: ByteBuffer, path: ColumnPath, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def get_counter_slice(key: ByteBuffer, column_parent: ColumnParent, predicate: SlicePredicate, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def multiget_counter_slice(keys: List[ByteBuffer], column_parent: ColumnParent, predicate: SlicePredicate, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def remove_counter(key: ByteBuffer, path: ColumnPath, consistency_level: ConsistencyLevel) = throw new UnsupportedOperationException
  def describe_schema_versions() = throw new UnsupportedOperationException
  def describe_keyspaces() = throw new UnsupportedOperationException
  def describe_cluster_name() = throw new UnsupportedOperationException
  def describe_version() = throw new UnsupportedOperationException
  def describe_ring(keyspace: String) = throw new UnsupportedOperationException
  def describe_partitioner() = throw new UnsupportedOperationException
  def describe_snitch() = throw new UnsupportedOperationException
  def describe_keyspace(keyspace: String) = throw new UnsupportedOperationException
  def describe_splits(cfName: String, start_token: String, end_token: String, keys_per_split: Int) = throw new UnsupportedOperationException
  def system_add_column_family(cf_def: CfDef) = throw new UnsupportedOperationException
  def system_drop_column_family(column_family: String) = throw new UnsupportedOperationException
  def system_add_keyspace(ks_def: KsDef) = throw new UnsupportedOperationException
  def system_drop_keyspace(keyspace: String) = throw new UnsupportedOperationException
  def system_update_keyspace(ks_def: KsDef) = throw new UnsupportedOperationException
  def system_update_column_family(cf_def: CfDef) = throw new UnsupportedOperationException
  def execute_cql_query(query: ByteBuffer, compression: Compression) = throw new UnsupportedOperationException
}