# Background on storm instrumentation

This is lumigo instrumentation for the [storm](https://storm.apache.org/) library.

## We instrument 3 things in the storm library:

* Storm Spouts 
* Storm Bolts 
* Storm `ExecutorTransfer` - the class that is responsible for transferring tuples between storm components (used inside the storm `OutputCollector`).

each one of those components has their own instrumentation class.
I create an internal span for each run of the spout / bolt and for each tuple that is emitted with the OutputCollector to each component its emitted to.

## Building transactions

we can build the transaction because we add the storm message id to the spout / bolt span attributes as `messaging.message.id` (based on Opentelemetry semantic convensions).
this message id is created by the `ExecutorTransfer` when it sends the tuple to the next component, and we add it to the `ExecutorTransfer` span too.
In addition, the `ExecutorTransfer` span has a parent span of the spout / bolt that emitted the tuple.


## Possible improvements:

* We create internal span for spout that doesn't emit any tuple.
* We create internal span for tuples that emitted with the `OutputCollector` to storm internal components (like the acker) - we don't show them in the three because they are not bolts / spout, so they don't have spans.
* We infer the component name from the thread name because I didn't manage to find any other way to do it.
