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

package com.raelity.play.event.bus.user;

import com.google.common.eventbus.EventBus;

import com.raelity.lib.eventbus.WeakEventBus;
import com.raelity.lib.eventbus.WeakSubscribe;

/**
 *
 */
public class EventBusUser {
    public static class BrOne {
        @WeakSubscribe
        public void mOne1(Long l) {}
        @WeakSubscribe
        public void mOne2(String l) {}
    }
    public static class BrTwo {
        @WeakSubscribe
        public void mTwo1(Integer l) {}
        @WeakSubscribe
        public void mTwo2(Integer l) {}
    }

    //BrOne br1 = new BrOne();
    //BrTwo br2 = new BrTwo();

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void main(String[] args) {
        EventBus eventBus = new EventBus();
        WeakEventBus.register(new BrOne(), eventBus);
        System.out.println("Hello World!");
    }
}
