## weak-event-bus-receiver

Use this to help avoid memory leaks.
An EventBus always holds a strong reference to an event bus receiver class;
a receiver class has one or more methods annotated with `@Subscribe`.
A receiver class often has strong references
to other classes. This easily creates memory leaks; consider the case where
a GUI window listens to an EventBus. If the window closes, but has a registered
event bus receiver, it is not garbage collected.

Using this artifact's annotations results in the creation of a WeakEventBus
receiver that is used a a proxy to the strong receiver. The weak reciever
only holds a weak reference to the strong receiver; so the strong receiver
can be garbage collected. When the strong receiver is collected,
the weak receiver is automatically unregistered.

The artifact requires `jdk-11` or later.

Examine `weak-event-bus-test`'s `EventBusUser.java`, and run it, for a complete example.

```java
// Must be PACKAGE so that the proxy can access it.
static class SomeStrongBusReceiver {
    @WeakSubscribe
    public void catch1(SomeEvent ev) {
        // ...
    }
    @WeakSubscribe
    @WeakAllowConcurrentEvents
    public void catch2(SomeOtherEvent ev) {
        // ...
    }
}

ebr = new SomeStrongBusReceiver; // Must have a STRONG REFERENCE.
WeakEventBus.register(ebr, myEventBus);
```

In `pom.xml`
```xml
<dependency>
    <groupId>com.raelity.lib</groupId>
    <artifactId>weak-event-bus-receiver</artifactId>
    <version>1.0.0</version>
</dependency>
```
And in the `maven-compiler-plugin`'s `<configuration>`
```xml
<annotationProcessors>
    <annotationProcessor>
        com.raelity.lib.eventbus.WeakEventBusProcessor
    </annotationProcessor>
</annotationProcessors>
```
