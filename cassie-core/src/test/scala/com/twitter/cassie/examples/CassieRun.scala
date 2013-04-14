// Copyright 2012 Twitter, Inc.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

// http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.twitter.cassie.tests.examples

import com.twitter.util.Await;

import com.twitter.cassie._
import com.twitter.cassie.codecs.Utf8Codec
import com.twitter.cassie.types.LexicalUUID
// TODO: unfortunate
import scala.collection.JavaConversions._

import org.slf4j.LoggerFactory

object CassieRun {
  private val log = LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]) {
    // create a cluster with a single seed from which to map keyspaces
    val cluster = new Cluster("localhost")

    // create a keyspace object (does nothing on the server)
    val keyspace = cluster.keyspace("Keyspace1").connect()

    // create a column family object (does nothing on the server)
    val cass = keyspace.columnFamily("Standard1", Utf8Codec, Utf8Codec, Utf8Codec)

    log.info("inserting some columns")
    Await.result(cass.insert("yay for me", Column("name", "Coda")))
    Await.result(cass.insert("yay for me", Column("motto", "Moar lean.")))

    Await.result(cass.insert("yay for you", Column("name", "Niki")))
    Await.result(cass.insert("yay for you", Column("motto", "Told ya.")))

    Await.result(cass.insert("yay for us", Column("name", "Biscuit")))
    Await.result(cass.insert("yay for us", Column("motto", "Mlalm.")))

    Await.result(cass.insert("yay for everyone", Column("name", "Louie")))
    Await.result(cass.insert("yay for everyone", Column("motto", "Swish!")))

    log.info("getting a column: %s", Await.result(cass.getColumn("yay for me", "name")))
    log.info("getting a column that doesn't exist: %s", Await.result(cass.getColumn("yay for no one", "name")))
    log.info("getting a column that doesn't exist #2: %s", Await.result(cass.getColumn("yay for no one", "oink")))
    log.info("getting a set of columns: %s", Await.result(cass.getColumns("yay for me", Set("name", "motto"))))
    log.info("getting a whole row: %s", Await.result(cass.getRow("yay for me")))
    log.info("getting a column from a set of keys: %s", Await.result(cass.multigetColumn(Set("yay for me", "yay for you"), "name")))
    log.info("getting a set of columns from a set of keys: %s", Await.result(cass.multigetColumns(Set("yay for me", "yay for you"), Set("name", "motto"))))

    log.info("Iterating!")
    val f = cass.rowsIteratee(2).foreach {
      case (key, cols) =>
        log.info("Found: %s %s", key, cols)
    }
    Await.result(f)

    val f2 = cass.columnsIteratee(2, "yay for me").foreach { col =>
      log.info("Found Columns Iteratee: %s", col)
    }
    Await.result(f2)

    log.info("removing a column")
    Await.result(cass.removeColumn("yay for me", "motto"))

    log.info("removing a row")
    Await.result(cass.removeRow("yay for me"))

    log.info("Batching up some stuff")
    Await.result(cass.batch()
      .removeColumn("yay for you", "name")
      .removeColumns("yay for us", Set("name", "motto"))
      .insert("yay for nobody", Column("name", "Burt"))
      .insert("yay for nobody", Column("motto", "'S funny."))
      .execute())

    log.info("Wrappin' up");
    keyspace.close();
  }
}
