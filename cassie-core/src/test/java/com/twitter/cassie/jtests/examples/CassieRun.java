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

package com.twitter.cassie.jtests.examples;

import com.twitter.cassie.*;
import com.twitter.cassie.clocks.MicrosecondEpochClock;
import com.twitter.cassie.codecs.*;
import com.twitter.cassie.types.*;
import com.twitter.finagle.stats.NullStatsReceiver$;
import com.twitter.util.Function2;
import com.twitter.util.Function;
import com.twitter.util.Future;
import com.twitter.util.Await;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public final class CassieRun {
  public static <V> HashSet<V> Set(V... values) {
    return new HashSet<V>(Arrays.asList(values));
  }

  public static void info(Object o) {
    System.out.println(o);
  }

  public static void main(String[] args) throws Exception {
    // create a cluster with a single seed from which to map keyspaces
    Cluster cluster = new Cluster("localhost", NullStatsReceiver$.MODULE$);

    // create a keyspace
    Keyspace keyspace = cluster.keyspace("Keyspace1").connect();

    // create a column family
    ColumnFamily<String, String, String> cass = keyspace.columnFamily("Standard1", Utf8Codec.get(), Utf8Codec.get(), Utf8Codec.get());

    info("inserting some columns");

    Await.result(cass.insert("yay for me", cass.newColumn("name", "Coda")));
    Await.result(cass.insert("yay for me", cass.newColumn("motto", "Moar lean.")));

    Await.result(cass.insert("yay for you", cass.newColumn("name", "Niki")));
    Await.result(cass.insert("yay for you", cass.newColumn("motto", "Told ya.")));

    Await.result(cass.insert("yay for us", cass.newColumn("name", "Biscuit")));
    Await.result(cass.insert("yay for us", cass.newColumn("motto", "Mlalm.")));

    Await.result(cass.insert("yay for everyone", cass.newColumn("name", "Louie")));
    Await.result(cass.insert("yay for everyone", cass.newColumn("motto", "Swish!")));

    info("getting a column: " + Await.result(cass.getColumn("yay for me", "name")));
    info("getting a column that doesn't exist: " + Await.result(cass.getColumn("yay for no one", "name")));
    info("getting a column that doesn't exist #2: " + Await.result(cass.getColumn("yay for no one", "oink")));
    info("getting a set of columns: " + Await.result(cass.getColumns("yay for me", Set("name", "motto"))));
    info("getting a whole row: " + Await.result(cass.getRow("yay for me")));
    info("getting a column from a set of keys: " + Await.result(cass.multigetColumn(Set("yay for me", "yay for you"), "name")));
    info("getting a set of columns from a set of keys: " + Await.result(cass.multigetColumns(Set("yay for me", "yay for you"), Set("name", "motto"))));

    info("Iterating!");
    Future f = cass.rowsIteratee(2).foreach(new scala.runtime.AbstractFunction2<String, List<Column<String, String>>, scala.runtime.BoxedUnit>() {
      public scala.runtime.BoxedUnit apply(String key, List<Column<String,String>> columns) {
        info("Found: " + key);
        return null;
      }
    });

    Await.result(f);

    Future f2 = cass.columnsIteratee(2, "yay for me").foreach(new Function<Column<String, String>, scala.runtime.BoxedUnit>() {
      public scala.runtime.BoxedUnit apply(Column<String,String> column){
        info("Found Columns Iteratee: " + column);
        return null;
      }
    });

    Await.result(f2);

    info("removing a column");
    Await.result(cass.removeColumn("yay for me", "motto"));

    info("removing a row");
    Await.result(cass.removeRow("yay for me"));

    info("Batching up some stuff");
    Await.result(cass.batch()
      .removeColumn("yay for you", "name")
      .removeColumns("yay for us", Set("name", "motto"))
      .insert("yay for nobody", cass.newColumn("name", "Burt"))
      .insert("yay for nobody", cass.newColumn("motto", "'S funny."))
      .execute());

    info("Wrappin' up");
    keyspace.close();
  }
}
