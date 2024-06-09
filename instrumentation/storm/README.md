# Background on storm instrumentation

This is lumigo instrumentation for the [storm](https://storm.apache.org/) library.

## We instrument 3 things in the storm library:

* Storm Spouts 
* Storm Bolts 
* Storm `ExecutorTransfer` - the class that is responsible for transferring tuples between storm components (used inside the storm `OutputCollector`).

each one of those components has their own instrumentation class.
An internal span is created for each run of the `spout` / `bolt` and each tuple emitted with the OutputCollector to each component it's emitted to.

## Otel conventions
for this instrumentation we used the following attributes:
1. `thread.name` - semantic convention default attribute
2. `messaging.system` - semantic convention default attribute
3. `messaging.message.id` - semantic convention default attribute
4. `messaging.destination.name` - semantic convention default attribute
5. `storm.type` - the type of the storm component spout / bolt
6. `storm.id` - the id of the storm topology
7. `storm.version` - the version of the storm library
8. `storm.tuple.values` - the value that emitted to each bolt
9. `source.component` - the previous component name (can be used to create trigger by behaviour)
10. `component.name` - the name of the component in the invocation override the otel_service_name property

## Building transactions

Transactions are built using storm `message.id` added to the `spout` / `bolt` span attributes (based on Opentelemetry semantic convensions).
This message id is created by the `ExecutorTransfer` when it sends the tuple to the next component, and we add it to the `ExecutorTransfer` span too.
In addition, the `ExecutorTransfer` span has a parent span of the spout / bolt that emitted the tuple.

## Possible improvements:

* Create Internal span for spout is which emitted no tuple. 
* We create internal span for tuples that emitted with the `OutputCollector` to storm internal components (like the acker) - we don't show them in the three because they are not bolts / spout, so they don't have spans.
* We infer the component name from the thread name because no other way was found
