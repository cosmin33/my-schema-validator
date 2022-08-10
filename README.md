# json-schema-validator

Test project for a json schema validator

## Instructions

1. [Install sbt](http://www.scala-sbt.org/1.0/docs/Setup.html)
2. `git clone https://github.com/cosmin33/my-schema-validator`
3. `cd my-schema-validator`
4. `sbt run`
5. `curl http://localhost:8080/schema/config-schema -X POST -d @config-schema.json`

This will add a new schema file in folder "schemas" and will respond with the appropriate json.
The schema files contained there will all have the format 'SCHEMA-<schema-id>' and will contain the json of the said schema.

All schema id's will be restricted to containing letters + numbers + hiphens, all other characters will produce an error from the respective api entry.
This was done so I can easily implement the file KVStore but should the KVStore be moved to a database one then said restrictions can be lifted

6. `curl http://localhost:8080/schema/config-schema`

This will show the content json of the schema just inserted, status code 201 returned

7. `curl http://localhost:8080/schema/nonexistent-schema`

This will return a 'not found' message

8. `curl http://localhost:8080/schema/config-schema1 -X POST -d @config-schema1.json`

A second schema will be added in the 'schemas' folder, named 'config-schema1'

9. `curl http://localhost:8080/validate/config-schema -X POST -d @config.json`

This will validate the json contained in 'config.json' file against the schema 'config-schema' and will be successful, status code 200

10. `curl http://localhost:8080/validate/config-schema -X POST -d @bad-config.json`

This will validate the json contained in 'bad-config.json' against the same schema and will find it non compliant, the json returned will also contain an ugly error message resulted from the failed validation, status code 406

