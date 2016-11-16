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
import org.carewebframework.web.component.Radiobutton;
import org.carewebframework.web.component.Radiogroup;
import org.carewebframework.web.event.ClickEvent;
import org.carewebframework.web.event.SelectEvent;

/**
 * Editor for boolean values.
 */
public class PropertyEditorBoolean extends PropertyEditorBase {
    
    private Radiogroup radiogroup;
    
    /**
     * Create property editor.
     * 
     * @throws Exception Unspecified exception.
     */
    public PropertyEditorBoolean() throws Exception {
        super(DesignConstants.RESOURCE_PREFIX + "PropertyEditorBoolean.cwf");
    }
    
    @Override
    protected void init(UIElementBase target, PropertyInfo propInfo, PropertyGrid propGrid) {
        super.init(target, propInfo, propGrid);
        //radiogroup.registerEventForward(Events.ON_CHECK, propGrid, Events.ON_CHANGE);
        radiogroup.registerEventForward(ClickEvent.TYPE, propGrid, SelectEvent.TYPE);
        component.registerEventForward(ClickEvent.TYPE, propGrid, SelectEvent.TYPE);
        
        for (Radiobutton radio : radiogroup.getChildren(Radiobutton.class)) {
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
        Radiobutton radio = radiogroup.getSelected();
        
        if (radio == null) {
            radio = (Radiobutton) radiogroup.getChildren().get(0);
        }
        
        radio.setFocus(true);
    }
    
    @Override
    protected Boolean getValue() {
        return radiogroup.getSelected() != null;
    }
    
    @Override
    protected void setValue(Object value) {
        int i = value == null ? -1 : (Boolean) value ? 0 : 1;
        Radiobutton rb = radiogroup.getSelected();
        
        if (rb != null) {
            rb.setChecked(false);
        }
        
        rb = (Radiobutton) radiogroup.getChildAt(i);
        
        if (rb != null) {
            rb.setChecked(true);
        }
    }
}
