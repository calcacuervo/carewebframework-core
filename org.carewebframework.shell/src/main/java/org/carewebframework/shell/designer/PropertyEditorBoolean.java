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
package org.carewebframework.shell.designer;

import org.carewebframework.shell.layout.UIElementBase;
import org.carewebframework.shell.property.PropertyInfo;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zul.Radio;
import org.zkoss.zul.Radiogroup;

/**
 * Editor for boolean values.
 */
public class PropertyEditorBoolean extends PropertyEditorBase<Radiogroup> {
    
    /**
     * Create property editor.
     * 
     * @throws Exception Unspecified exception.
     */
    public PropertyEditorBoolean() throws Exception {
        super(DesignConstants.RESOURCE_PREFIX + "PropertyEditorBoolean.zul");
    }
    
    @Override
    protected void init(UIElementBase target, PropertyInfo propInfo, PropertyGrid propGrid) {
        super.init(target, propInfo, propGrid);
        editor.addForward(Events.ON_CHECK, propGrid, Events.ON_CHANGE);
        editor.addForward(Events.ON_CLICK, propGrid, Events.ON_SELECT);
        editor.addForward(Events.ON_CLICK, propGrid, Events.ON_SELECT);
        
        for (Radio radio : editor.getItems()) {
            String label = propInfo.getConfigValue(radio.getLabel().trim());
            
            if (label != null) {
                radio.setLabel(label);
            }
        }
    }
    
    /**
     * Sets focus to the selected radio button.
     */
    @Override
    public void setFocus() {
        Radio radio = editor.getSelectedItem();
        
        if (radio == null) {
            radio = editor.getItems().get(0);
        }
        
        radio.setFocus(true);
    }
    
    @Override
    protected Boolean getValue() {
        int i = editor.getSelectedIndex();
        return i < 0 ? null : i == 0;
    }
    
    @Override
    protected void setValue(Object value) {
        int i = value == null ? -1 : (Boolean) value ? 0 : 1;
        editor.setSelectedIndex(i);
    }
}
