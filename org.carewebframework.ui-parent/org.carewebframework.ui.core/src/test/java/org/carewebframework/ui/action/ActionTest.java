/*
 * #%L
 * carewebframework
 * %%
 * Copyright (C) 2008 - 2016 Regenstrief Institute, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This Source Code Form is also subject to the terms of the Health-Related
 * Additional Disclaimer of Warranty and Limitation of Liability available at
 *
 *      http://www.carewebframework.org/licensing/disclaimer.
 *
 * #L%
 */
package org.carewebframework.ui.action;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ActionTest {
    
    @Test
    public void testActionFormats() {
        assertEquals(ActionType.ZSCRIPT, ActionType.getType("zscript: xyz"));
        assertEquals(ActionType.JSCRIPT, ActionType.getType("jscript: alert('hi');"));
        assertEquals(ActionType.JSCRIPT, ActionType.getType("javascript: alert('hi');"));
        assertEquals(ActionType.URL, ActionType.getType("http://www.regenstrief.org"));
        assertEquals(ActionType.URL, ActionType.getType("https://www.regenstrief.org"));
        assertEquals(ActionType.ZUL, ActionType.getType("~./org/regenstrief/test/test.zul"));
        assertEquals(ActionType.UNKNOWN, ActionType.getType("unknown type"));
    }
    
}
