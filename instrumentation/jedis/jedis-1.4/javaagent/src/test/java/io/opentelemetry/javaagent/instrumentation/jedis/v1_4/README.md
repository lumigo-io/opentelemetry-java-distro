# Jedis tests
This test file was copied from [Jedis-1.4 instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/jedis/jedis-1.4/javaagent), with the following changes:
1. change the check of `DB_STATEMENT` to contin the command's parameters
2. add the test `setSetTransactionCommand` to test the `MULTI` command
