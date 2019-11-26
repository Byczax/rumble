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
 * Authors: Stefan Irimescu, Can Berker Cikis
 *
 */

package sparksoniq.spark.udf;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;
import org.joda.time.Instant;
import org.rumbledb.api.Item;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import scala.collection.mutable.WrappedArray;
import sparksoniq.exceptions.SparksoniqRuntimeException;
import sparksoniq.jsoniq.compiler.translator.expr.flowr.OrderByClauseExpr;
import sparksoniq.jsoniq.item.NullItem;
import sparksoniq.semantics.DynamicContext;
import sparksoniq.semantics.types.ItemTypes;
import sparksoniq.spark.DataFrameUtils;
import sparksoniq.spark.iterator.flowr.expression.OrderByClauseSparkIteratorExpression;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class OrderClauseCreateColumnsUDF implements UDF1<WrappedArray<byte[]>, Row> {

    private static final long serialVersionUID = 1L;
    private List<OrderByClauseSparkIteratorExpression> _expressions;
    Map<String, DynamicContext.VariableDependency> _dependencies;

    List<String> _columnNames;
    private Map<Integer, String> _allColumnTypes;

    private List<List<Item>> _deserializedParams;
    private DynamicContext _context;
    private List<Object> _results;

    private transient Kryo _kryo;
    private transient Input _input;

    public OrderClauseCreateColumnsUDF(
            List<OrderByClauseSparkIteratorExpression> expressions,
            Map<Integer, String> allColumnTypes,
            List<String> columnNames
    ) {
        _expressions = expressions;
        _allColumnTypes = allColumnTypes;

        _deserializedParams = new ArrayList<>();
        _context = new DynamicContext();
        _results = new ArrayList<>();

        _dependencies = new TreeMap<String, DynamicContext.VariableDependency>();
        for (OrderByClauseSparkIteratorExpression expression : _expressions) {
            _dependencies.putAll(expression.getExpression().getVariableDependencies());
        }
        _columnNames = columnNames;

        _kryo = new Kryo();
        _kryo.setReferences(false);
        DataFrameUtils.registerKryoClassesKryo(_kryo);
        _input = new Input();
    }

    @Override
    public Row call(WrappedArray<byte[]> wrappedParameters) {
        _deserializedParams.clear();
        _context.removeAllVariables();
        _results.clear();

        DataFrameUtils.deserializeWrappedParameters(wrappedParameters, _deserializedParams, _kryo, _input);

        DataFrameUtils.prepareDynamicContext(_context, _columnNames, _deserializedParams);

        for (int expressionIndex = 0; expressionIndex < _expressions.size(); expressionIndex++) {
            OrderByClauseSparkIteratorExpression expression = _expressions.get(expressionIndex);

            // nulls and empty sequences have special ordering captured in the first sorting column
            // if non-null, non-empty-sequence value is given, the second column is used to sort the input
            // indices are assigned to each value type for the first column
            int emptySequenceOrderIndex = 1; // by default, empty sequence is taken as first(=least)
            int nullOrderIndex = 2; // null is the smallest value except empty sequence(default)
            int valueOrderIndex = 3; // values are larger than null and empty sequence(default)
            if (expression.getEmptyOrder() == OrderByClauseExpr.EMPTY_ORDER.LAST) {
                emptySequenceOrderIndex = 4;
            }

            // apply expression in the dynamic context
            expression.getExpression().open(_context);
            boolean isEmptySequence = true;
            while (expression.getExpression().hasNext()) {
                isEmptySequence = false;
                Item nextItem = expression.getExpression().next();
                if (nextItem instanceof NullItem) {
                    _results.add(nullOrderIndex);
                    _results.add(null); // placeholder for valueColumn(2nd column)
                } else {
                    // any other atomic type
                    _results.add(valueOrderIndex);

                    // extract type information for the sorting column
                    String typeName = _allColumnTypes.get(expressionIndex);
                    try {
                        switch (typeName) {
                            case "bool":
                                _results.add(nextItem.getBooleanValue());
                                break;
                            case "string":
                                _results.add(nextItem.getStringValue());
                                break;
                            case "integer":
                                _results.add(nextItem.castToIntegerValue());
                                break;
                            case "double":
                                _results.add(nextItem.castToDoubleValue());
                                break;
                            case "decimal":
                                _results.add(nextItem.castToDecimalValue());
                                break;
                            case "duration":
                            case "yearMonthDuration":
                            case "dayTimeDuration":
                                _results.add(nextItem.getDurationValue().toDurationFrom(Instant.now()).getMillis());
                                break;
                            case "dateTime":
                            case "date":
                            case "time":
                                _results.add(nextItem.getDateTimeValue().getMillis());
                                break;
                            default:
                                throw new SparksoniqRuntimeException(
                                        "Unexpected ordering type found while creating columns.");
                        }
                    } catch (RuntimeException e) {
                        throw new SparksoniqRuntimeException("Invalid sort key: cannot compare item of type "
                                + typeName
                                + " with item of type "
                                + ItemTypes.getItemTypeName(nextItem.getClass().getSimpleName())
                                + ".");
                    }
                }
            }
            if (isEmptySequence) {
                _results.add(emptySequenceOrderIndex);
                _results.add(null); // placeholder for valueColumn(2nd column)
            }
            expression.getExpression().close();

        }
        return RowFactory.create(_results.toArray());
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException,
                ClassNotFoundException {
        in.defaultReadObject();

        _kryo = new Kryo();
        _kryo.setReferences(false);
        DataFrameUtils.registerKryoClassesKryo(_kryo);
        _input = new Input();
    }
}
