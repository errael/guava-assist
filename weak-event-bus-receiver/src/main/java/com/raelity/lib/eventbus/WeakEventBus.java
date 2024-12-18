/*
 * Portions created by Ernie Rael are
 * Copyright (C) 2024 Ernie Rael.  All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Contributor(s): Ernie Rael <errael@raelity.com>
 */

package com.raelity.lib.eventbus;

import java.lang.ref.Cleaner;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.google.common.collect.MapMaker;
import com.google.common.eventbus.EventBus;

/**
 * Static methods for hooking up and registering a weak event bus receiver.
 * <p>
 * The weak event bus receiver is generated during compilation by
 * annotations on the strong event bus receiver; the weak receiver
 * holds a {@linkplain java.lang.ref.WeakReference} to the strong receiver.
 * The generated class file
 * is in the same package as the strong event bus.
 * <p>
 * This class has a {@linkplain java.lang.ref.Cleaner} which,
 * when the strong event bus receiver becomes unreachable,
 * unregister's this weak event bus receiver from the EventBus.
 * 
 * See {@link WeakSubscribe} {@link WeakAllowConcurrentEvents}
 */
public class WeakEventBus
{
private WeakEventBus() { }

private static final Cleaner cleaner = Cleaner.create();

/**
 * Construct an EventBus receiver that only has a weak reference to
 * "stringBR". Register it to the specified EventBus.
 * @param strongBR the event bus receiver to weakly reference
 * @param eventBus the event bus
 */
public static void register(Object strongBR, EventBus eventBus)
{
    Class<?> clazz = strongBR.getClass();
    // Derive the name of the generated class from the strong bus receiver.
    String nameWeakBR = WeakEventBusProcessor.nameWeakBR(
            clazz.getName(), clazz.getPackageName());
    try {
        // weakClazz is the generated class that hold a
        // weak reference to the strong EB receiver.
        Class<?> weakClazz = Class.forName(clazz.getPackageName() + "." + nameWeakBR);
        Constructor<?> ctor = weakClazz.getConstructor(clazz);
        // Create the weak EB receiver that weak references the strong EB receiver.
        Object weakBR = ctor.newInstance(strongBR);
        // Register the weak event bus to the event bus.
        eventBus.register(weakBR);
        // When the strong event bus becomes unreachable, unregister the weark
        WeakEventBus.cleaner.register(strongBR, () -> {
            eventBus.unregister(weakBR);
        });
        registered.put(strongBR, weakBR);
    } catch(ClassNotFoundException | NoSuchMethodException | SecurityException |
            InstantiationException | IllegalAccessException |
            IllegalArgumentException | InvocationTargetException ex) {
        throw new IllegalStateException(String.format(
                "Creation of %s's weak EventBus receiver failed", clazz.getName()), ex);
    }
}

// With MapMaker, weak keys and values.
private static Map<Object, Object> registered = new MapMaker()
        .concurrencyLevel(1)
        .weakKeys()
        .weakValues()
        .makeMap();

/**
 * Unregisters all subscriber methods on a registered object
 * from the specified EventBus.
 * @param strongBR the object whose weak proxy should be unregistered
 * @param eventBus the eventBus which from which to unregister
 */
public static void unregister(Object strongBR, EventBus eventBus)
{
    Object weakBR = registered.remove(strongBR);
    if (weakBR != null)
        eventBus.unregister(weakBR);
}
}
