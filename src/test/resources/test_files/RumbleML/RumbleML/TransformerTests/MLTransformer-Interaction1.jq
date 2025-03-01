(:JIQS: ShouldRun; Output="({ "label" : 0, "name" : "a", "age" : 20, "weight" : 50, "interaction" : [ 1000 ] }, { "label" : 1, "name" : "b", "age" : 21, "weight" : 55.3, "interaction" : [ 1161.3 ] }, { "label" : 2, "name" : "c", "age" : 22, "weight" : 60.6, "interaction" : [ 1333.2 ] }, { "label" : 3, "name" : "d", "age" : 23, "weight" : 65.9, "interaction" : [ 1515.7 ] }, { "label" : 4, "name" : "e", "age" : 24, "weight" : 70.3, "interaction" : [ 1687.1999999999998 ] }, { "label" : 5, "name" : "f", "age" : 25, "weight" : 75.6, "interaction" : [ 1889.9999999999998 ] })" :)
let $data := annotate(
    json-lines("../../../../queries/rumbleML/sample-ml-data-flat.json"),
    { "label": "integer", "binaryLabel": "integer", "name": "string", "age": "double", "weight": "double", "booleanCol": "boolean", "nullCol": "null", "stringCol": "string", "stringArrayCol": ["string"], "intArrayCol": ["integer"],  "doubleArrayCol": ["double"],  "doubleArrayArrayCol": [["double"]] }
)

let $transformer := get-transformer("Interaction")
for $result in $transformer(
    $data,
    {"inputCols": ["age", "weight"], "outputCol": "interaction"}
)
return {
    "label": $result.label,
    "name": $result.name,
    "age": $result.age,
    "weight": $result.weight,
    "interaction": $result.interaction
}
