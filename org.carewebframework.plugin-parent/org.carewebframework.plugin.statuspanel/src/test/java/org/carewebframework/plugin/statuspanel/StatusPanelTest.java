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
package org.carewebframework.plugin.statuspanel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.carewebframework.api.event.EventManager;
import org.carewebframework.shell.CareWebShell;
import org.carewebframework.shell.plugins.PluginContainer;
import org.carewebframework.ui.FrameworkController;
import org.carewebframework.ui.test.CommonTest;
import org.junit.Test;
import org.zkoss.zk.ui.Component;
import org.zkoss.zul.Label;

public class StatusPanelTest extends CommonTest {
    
    @Test
    public void test() throws Exception {
        CareWebShell shell = new CareWebShell();
        shell.setLayout("/StatusPanelTest.xml");
        shell.setPage(mockEnvironment.getDesktop().getFirstPage());
        shell.afterCompose();
        mockEnvironment.flushEvents();
        PluginContainer plugin = shell.getActivatedPlugin("cwfStatusPanel");
        Component root = plugin.getFirstChild();
        StatusPanel controller = (StatusPanel) FrameworkController.getController(root);
        assertNotNull("Controller must not be null.", controller);
        assertEquals(1, root.getChildren().size());
        test(root, "STATUS", 1, 1);
        test(root, "STATUS.TEST1", 1, 3);
        test(root, "STATUS.TEST1", 2, 3);
        test(root, "STATUS.TEST2", 1, 5);
        test(root, "STATUS.TEST2", 2, 5);
    }
    
    private void test(Component root, String eventName, int eventData, int expectedSize) {
        String labelText = eventName + "." + eventData;
        EventManager.getInstance().fireLocalEvent(eventName, labelText);
        assertEquals(expectedSize, root.getChildren().size());
        Label label = (Label) root.getChildren().get(expectedSize - 1).getFirstChild();
        assertEquals(labelText, label.getValue());
    }
}
