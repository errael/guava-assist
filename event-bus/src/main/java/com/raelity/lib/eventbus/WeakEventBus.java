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
 * The Original Code is jvi - vi editor clone.
 *
 * Contributor(s): Ernie Rael <errael@raelity.com>
 */

package com.raelity.lib.eventbus;

import java.lang.ref.Cleaner;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.google.common.eventbus.EventBus;

/**
 * For hooking up and registering a weak event bus.
 * The weak event bus is generated during compilation by
 * annotations on the strong event bus; it is in the same
 * package as the strong event bus.
 * <p>
 * This class has a {@linkplain java.lang.ref.Cleaner} which,
 * when the strong event bus receiver becomes unreachable,
 * unregister's this weak event bus receiver from the EventBus.
 */
public class WeakEventBus
{
private WeakEventBus() { }

private static final Cleaner cleaner = Cleaner.create();

/**
 * Construct an EventBus receiver that only has a weak reference to
 * the argument. Register it to the EventBus.
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
        // Create the weak EB receiver which weak references the strong EB receiver.
        Object weakBR = ctor.newInstance(strongBR);
        // Register the weak event bus to the event bus.
        eventBus.register(weakBR);
        // When the strong event bus becomes unreachable, unregister the weark
        WeakEventBus.cleaner.register(strongBR, () -> {
            eventBus.unregister(weakBR);
        });
    } catch(ClassNotFoundException | NoSuchMethodException | SecurityException |
            InstantiationException | IllegalAccessException |
            IllegalArgumentException | InvocationTargetException ex) {
        throw new IllegalStateException(String.format(
                "Creation of %s's weak EventBus receiver failed", clazz.getName()), ex);
    }
}

// With MapMaker, weak keys and values.
public static void unregister(Object strongBR, EventBus eventBus)
{
    //eventBus.unregister(weakBR);
}
}
