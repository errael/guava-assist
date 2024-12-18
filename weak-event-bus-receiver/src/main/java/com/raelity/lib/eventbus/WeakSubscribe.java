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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a method as an event subscriber in a weak event bus receiver.
 * <p>
 * The type of event will be indicated by the method's first (and only) parameter,
 * which cannot be primitive. If this annotation is applied to methods with zero
 * parameters, or more than one parameter, the object containing the method will
 * not be able to register for event delivery from the
 * {@link com.google.common.eventbus.EventBus}.
 * <p>
 * Unless also annotated with {@link WeakAllowConcurrentEvents},
 * event subscriber methods
 * will be invoked serially by each event bus that they are registered with.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface WeakSubscribe
{
}
