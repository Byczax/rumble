(:JIQS: ShouldCrash; ErrorCode="RBST0003"; ErrorMetadata="LINE:3:COLUMN:4:" :)
for $i in json-lines("../../queries/conf-ex.json")
let $j := for $k in json-lines("../../queries/conf-ex.json")
          return $k.target
return $j

(: Job within job :)
