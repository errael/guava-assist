## weak-event-bus-receiver

The artifact is `jdk-11` compatible.

Examine `event-bus-test`'s `EventBusUser.java`, and run it, for a complete example.

```java
public static class SomeStrongBusReceiver {
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

eb = new SomeStrongBusReceiver; // If you might want to manually unregister.
WeakEventBus.register(eb, myEventBus);
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
