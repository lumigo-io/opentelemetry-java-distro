# Background on fork

This module is a fork of the upstream [Jedis-1.4 instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/jedis/jedis-1.4/javaagent).
Changes list:
* `JedisRequestContext` moved from `jedis/common` to `jedis/jedis-1.4/javaagent`
* `JedisDbAttributesGetter` add command's arguments by default: `CommonConfig.get().isStatementSanitizationEnabled()` -> `false`
* JedisClientTest - because we don't have version: `testing.waitAndAssertTraces` -> `TracesAssert.assertThat(testing.waitForTraces(2))` 
* JedisClientTest - because we also include thread name/id: `hasAttributesSatisfyingExactly` -> `hasAttributesSatisfying`
* JedisClientTest - I added a test for transactions: `setSetTransactionCommand`
