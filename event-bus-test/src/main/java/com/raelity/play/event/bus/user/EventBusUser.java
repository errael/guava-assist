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

package com.raelity.play.event.bus.user;

import java.util.ArrayList;
import java.util.List;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import com.raelity.lib.eventbus.WeakAllowConcurrentEvents;
import com.raelity.lib.eventbus.WeakEventBus;
import com.raelity.lib.eventbus.WeakSubscribe;

/**
 *
 */
public class EventBusUser {
    static List<String> events = new ArrayList<>();

    public static class BrOne {
        @WeakSubscribe
        public void mOne1(Long l) {
            events.add("mOne1:" + l);
        }
        @WeakSubscribe
        @WeakAllowConcurrentEvents
        public void mOne2(String l) {
            events.add("mOne2:" + l);
        }
    }

    public static class BrTwo {
        @WeakSubscribe
        public void mTwo1(Integer l) {
        }
        @WeakSubscribe
        public void mTwo2(Long l) {
        }
    }

    public static class BrNormal {
        @Subscribe
        public void m(Integer i) {}
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void main(String[] args) {
        EventBus eventBus = new EventBus();
        BrOne br = new BrOne();

        WeakEventBus.register(br, eventBus);

        eventBus.post(Long.valueOf(3));
        eventBus.post("foo");
        System.out.println(events.toString());
        if (!events.get(0).equals("mOne1:3")
                || !events.get(1).equals("mOne2:foo"))
            throw new IllegalStateException("wrong post");

        events.clear();

        WeakEventBus.unregister(br, eventBus);

        eventBus.post(Long.valueOf(3));
        eventBus.post("foo");
        if (!events.isEmpty())
            throw new IllegalStateException("not empty");
        
        
        System.out.println("OK");
    }
}
