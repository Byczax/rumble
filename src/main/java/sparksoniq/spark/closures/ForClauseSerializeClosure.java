/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Stefan Irimescu
 *
 */

package sparksoniq.spark.closures;

import org.apache.spark.api.java.function.Function;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.rumbledb.api.Item;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import sparksoniq.spark.DataFrameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ForClauseSerializeClosure implements Function<Item, Row> {


    private static final long serialVersionUID = 1L;
    private transient Kryo _kryo;
    private transient Output _output;

    public ForClauseSerializeClosure() {
        _kryo = new Kryo();
        _kryo.setReferences(false);
        DataFrameUtils.registerKryoClassesKryo(_kryo);
        _output = new Output(128, -1);
    }

    /**
     * @param item
     * @return Row object, containing byte array of a singleton list containing the given item
     */
    @Override
    public Row call(Item item) {
        List<Item> itemList = new ArrayList<>();
        itemList.add(item);

        return RowFactory.create(DataFrameUtils.serializeItemList(itemList, _kryo, _output));
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException,
                ClassNotFoundException {
        in.defaultReadObject();

        _kryo = new Kryo();
        _kryo.setReferences(false);
        DataFrameUtils.registerKryoClassesKryo(_kryo);
        _output = new Output(128, -1);
    }
}
