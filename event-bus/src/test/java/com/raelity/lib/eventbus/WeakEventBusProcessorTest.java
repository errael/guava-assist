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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


/**
 *
 * @author err
 */
public class WeakEventBusProcessorTest
{

public WeakEventBusProcessorTest()
{
}

@BeforeAll
public static void setUpClass()
{
}

@AfterAll
public static void tearDownClass()
{
}

@BeforeEach
public void setUp()
{
}

@AfterEach
public void tearDown()
{
}

public static class BrOne {
@WeakSubscribe public void mOne(Long l) {}
}
public static class BrTwo {
@WeakSubscribe public void mTwo(Integer l) {}
}

/**
 * Test of process method, of class WeakEventBusProcessor.
 */
@Test
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public void testProcess() throws Exception
{
    System.out.println("process");
    //new BrOne().mOne(Long.MIN_VALUE);
    BrOne eb = new BrOne();
    //WeakEventBus.register(eb, null);
    // Set<? extends TypeElement> annotations = null;
    // RoundEnvironment roundEnv = null;
    // WeakEventBusProcessor instance = new WeakEventBusProcessor();
    // boolean expResult = false;
    // boolean result = instance.process(annotations, roundEnv);
    // assertEquals(expResult, result);
    // // TODO review the generated test code and remove the default call to fail.
    // fail("The test case is a prototype.");
}

}
