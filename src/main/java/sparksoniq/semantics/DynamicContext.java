/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package sparksoniq.semantics;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import sparksoniq.exceptions.SparksoniqRuntimeException;
import sparksoniq.jsoniq.item.ItemFactory;
import sparksoniq.jsoniq.tuple.FlworTuple;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rumbledb.api.Item;

public class DynamicContext implements Serializable, KryoSerializable {

  	private static final long serialVersionUID = 1L;

	  public enum VariableDependency {
	    FULL,
	    COUNT,
	    SUM,
	    AVG,
	    MAX,
	    MIN
	  }

	private Map<String, List<Item>> _variableValues;
    private Map<String, Item> _variableCounts;
    private long _position;
    private long _last;
    private DynamicContext _parent;

    public DynamicContext() {
        this._parent = null;
        this._variableValues = new HashMap<>();
        this._variableCounts = new HashMap<>();
        this._position = -1;
        this._last = -1;
    }

    public DynamicContext(DynamicContext parent) {
        this._parent = parent;
        this._variableValues = new HashMap<>();
        this._variableCounts = new HashMap<>();
        this._position = -1;
        this._last = -1;
    }

    public DynamicContext(FlworTuple tuple) {
        this();
        setBindingsFromTuple(tuple);
    }

    public DynamicContext(DynamicContext parent, FlworTuple tuple) {
        this._parent = parent;
        this._variableValues = new HashMap<>();
        this._variableCounts = new HashMap<>();
        this._position = -1;
        this._last = -1;
        setBindingsFromTuple(tuple);
    }

    public void setBindingsFromTuple(FlworTuple tuple) {
        for (String key : tuple.getKeys())
            if (!key.startsWith("."))
                this.addVariableValue(key, tuple.getValue(key));
    }

    public void addVariableValue(String varName, List<Item> value) {
        this._variableValues.put(varName, value);
    }

    public void addVariableCount(String varName, Item count) {
        this._variableCounts.put(varName, count);
    }

    public List<Item> getVariableValue(String varName) {
        if (_variableValues.containsKey(varName))
            return _variableValues.get(varName);

        if (_parent != null)
            return _parent.getVariableValue(varName);

        if(_variableCounts.containsKey(varName))
            throw new SparksoniqRuntimeException("Runtime error retrieving variable " + varName + " value: only count available.");

        throw new SparksoniqRuntimeException("Runtime error retrieving variable " + varName + " value");
    }

    public Item getVariableCount(String varName) {
        if(_variableCounts.containsKey(varName))
        {
            return _variableCounts.get(varName);
        }
        if (_variableValues.containsKey(varName))
        {
            Item count = ItemFactory.getInstance().createIntegerItem(_variableValues.get(varName).size());
            return count;
        }
        if (_parent != null)
        {
            return _parent.getVariableCount(varName);
        }
        throw new SparksoniqRuntimeException("Runtime error retrieving variable " + varName + " value");
    }

    public void removeVariable(String varName) {
        this._variableValues.remove(varName);
        this._variableCounts.remove(varName);
    }

    public void removeAllVariables() {
        this._variableValues.clear();
        this._variableCounts.clear();
        this._position = -1;
        this._last = -1;
    }

    @Override
    public void write(Kryo kryo, Output output) {
        kryo.writeObject(output, _parent);
        kryo.writeObject(output, _variableValues);
    }

    @SuppressWarnings("unchecked")
	@Override
    public void read(Kryo kryo, Input input) {
        _parent = kryo.readObjectOrNull(input, DynamicContext.class);
        _variableValues = kryo.readObject(input, HashMap.class);
    }
    
    public long getPosition() {
    	if(_position != -1)
    	{
    		return _position;
    	}
    	if (_parent != null)
            return _parent.getPosition();
    	return -1;
    }
    
    public void setPosition (long position) {
    	_position = position;
    }
    
    public long getLast() {
    	if(_last != -1)
    	{
    		return _last;
    	}
    	if (_parent != null)
            return _parent.getLast();
    	return -1;
    }
    
    public void setLast (long last) {
    	_last = last;
    }
}

