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
package org.carewebframework.ui.sharedforms;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.carewebframework.common.NumUtil;
import org.carewebframework.common.StrUtil;
import org.carewebframework.ui.command.CommandUtil;
import org.carewebframework.ui.util.CWFUtil;
import org.carewebframework.web.annotation.EventHandler;
import org.carewebframework.web.component.BaseComponent;
import org.carewebframework.web.component.BaseUIComponent;
import org.carewebframework.web.component.Cell;
import org.carewebframework.web.component.Column;
import org.carewebframework.web.component.Columns;
import org.carewebframework.web.component.Pane;
import org.carewebframework.web.component.Paneview;
import org.carewebframework.web.component.Paneview.Orientation;
import org.carewebframework.web.component.Row;
import org.carewebframework.web.component.Rowcell;
import org.carewebframework.web.component.Rows;
import org.carewebframework.web.component.Table;
import org.carewebframework.web.event.ChangeEvent;
import org.carewebframework.web.event.ClickEvent;
import org.carewebframework.web.event.Event;
import org.carewebframework.web.event.IEventListener;
import org.carewebframework.web.model.IComponentRenderer;
import org.carewebframework.web.model.ListModel;
import org.carewebframework.web.model.ModelAndView;

/**
 * Controller for list view based forms.
 * 
 * @param <DAO> Data access object type.
 */
public abstract class ListViewForm<DAO> extends CaptionedForm {
    
    private static final String SORT_TYPE_ATTR = "@sort_type";
    
    private static final String COL_INDEX_ATTR = "@col_index";
    
    private static final String SIZE_ATTR = "@size";
    
    private Paneview mainView;
    
    private Table table;
    
    private Columns columns;
    
    private Rows rows;
    
    private Pane detailPane;
    
    private Pane listPane;
    
    private Cell status;
    
    private boolean allowPrint;
    
    private String alternateColor = "#F0F0F0";
    
    private int colCount;
    
    private String dataName;
    
    private boolean deferUpdate = true;
    
    private boolean dataNeedsUpdate = true;
    
    private int sortColumn;
    
    private boolean sortAscending;
    
    protected final ListModel<DAO> model = new ListModel<>();
    
    protected ModelAndView<Row, DAO> modelAndView;
    
    private final IComponentRenderer<Row, DAO> renderer = new IComponentRenderer<Row, DAO>() {
        
        @Override
        public Row render(DAO object) {
            Row item = new Row();
            item.addEventForward(ClickEvent.TYPE, table, ChangeEvent.TYPE);
            ListViewForm.this.renderItem(item, object);
            return item;
        }
        
    };
    
    private final IEventListener sortListener = new IEventListener() {
        
        @Override
        public void onEvent(Event event) {
            //TODO: sortAscending = event.isAscending();
            //sortColumn = (Integer) event.getTarget().getAttribute(COL_INDEX_ATTR);
        }
        
    };
    
    /**
     * Abort any pending async call.
     */
    protected abstract void asyncAbort();
    
    /**
     * Async request to fetch data.
     */
    protected abstract void requestData();
    
    @Override
    protected void init() {
        super.init();
        modelAndView = new ModelAndView<Row, DAO>(table, model, renderer);
        root = detailPane;
        setSize(50);
        CommandUtil.associateCommand("REFRESH", table);
        getContainer().registerProperties(this, "allowPrint", "alternateColor", "deferUpdate", "showDetailPane", "layout",
            "horizontal");
    }
    
    public void destroy() {
        asyncAbort();
    }
    
    protected void setup(String title, String... headers) {
        setup(title, 1, headers);
    }
    
    protected void setup(String title, int sortBy, String... headers) {
        setCaption(title);
        dataName = title;
        String defWidth = (100 / headers.length) + "%";
        
        for (String header : headers) {
            String[] pcs = StrUtil.split(header, StrUtil.U, 3);
            Column lhdr = new Column(pcs[0]);
            columns.addChild(lhdr);
            lhdr.setAttribute(SORT_TYPE_ATTR, NumberUtils.toInt(pcs[1]));
            lhdr.setAttribute(COL_INDEX_ATTR, colCount++);
            String width = pcs[2];
            
            if (!width.isEmpty()) {
                if (NumberUtils.isDigits(width) || "min".equals(width)) {
                    //lhdr.setHflex(width);
                } else {
                    lhdr.setWidth(width);
                }
            } else {
                lhdr.setWidth(defWidth);
            }
            
            lhdr.addEventListener("sort", sortListener);
        }
        
        sortColumn = Math.abs(sortBy) - 1;
        sortAscending = sortBy > 0;
        doSort();
    }
    
    public boolean getHorizontal() {
        return mainView.getOrientation() == Orientation.HORIZONTAL;
    }
    
    public void setHorizontal(boolean value) {
        mainView.setOrientation(value ? Orientation.HORIZONTAL : Orientation.VERTICAL);
    }
    
    public String getAlternateColor() {
        return alternateColor;
    }
    
    public void setAlternateColor(String value) {
        this.alternateColor = value;
    }
    
    public boolean getShowDetailPane() {
        return detailPane.isVisible();
    }
    
    public void setShowDetailPane(boolean value) {
        if (getShowDetailPane() != value) {
            if (value) {
                //listPane.setRelativeSize(getSize());
            } else {
                //setSize(listPane.getRelativeSize());
                //listPane.setRelativeSize(100);
            }
            
            detailPane.setVisible(value);
        }
    }
    
    public boolean getAllowPrint() {
        return allowPrint;
    }
    
    public void setAllowPrint(boolean value) {
        this.allowPrint = value;
    }
    
    public boolean getDeferUpdate() {
        return deferUpdate;
    }
    
    public void setDeferUpdate(boolean value) {
        this.deferUpdate = value;
    }
    
    /**
     * Getter method for Layout property. Format:
     * 
     * <pre>
     *   List Pane Size:Sort Column:Sort Direction;Column 0 Index:Column 0 Width;...
     * </pre>
     * 
     * @return The layout data.
     */
    public String getLayout() {
        StringBuilder sb = new StringBuilder();
        sb.append(NumUtil.toString(getSize())).append(':').append(sortColumn).append(':').append(sortAscending);
        
        for (BaseComponent comp : columns.getChildren()) {
            Column lhdr = (Column) comp;
            sb.append(';').append(lhdr.getAttribute(COL_INDEX_ATTR)).append(':').append(lhdr.getWidth());
        }
        return sb.toString();
    }
    
    /**
     * Setter method for Layout property. This property allows an application to control the
     * position of the splitter bar and ordering of columns.
     * 
     * @param layout The layout data.
     */
    public void setLayout(String layout) {
        String[] pcs = StrUtil.split(layout, ";");
        
        if (pcs.length > 0) {
            String[] spl = StrUtil.split(pcs[0], ":", 3);
            setSize(NumberUtils.toInt(spl[0]));
            sortColumn = NumberUtils.toInt(spl[1]);
            sortAscending = BooleanUtils.toBoolean(spl[2]);
        }
        
        for (int i = 1; i < pcs.length; i++) {
            String[] col = StrUtil.split(pcs[i], ":", 2);
            Column lhdr = getColumnByIndex(NumberUtils.toInt(col[0]));
            
            if (lhdr != null) {
                lhdr.setWidth(col[1]);
                lhdr.setIndex(i - 1);
            }
        }
        
        doSort();
    }
    
    private double getSize() {
        return (Double) listPane.getAttribute(SIZE_ATTR);
    }
    
    private void setSize(double value) {
        listPane.setAttribute(SIZE_ATTR, value);
    }
    
    private void doSort() {
        getColumnByIndex(sortColumn).sort();
    }
    
    /**
     * Returns the column corresponding to the specified index.
     * 
     * @param index Column index.
     * @return List header at index.
     */
    private Column getColumnByIndex(int index) {
        for (BaseComponent comp : columns.getChildren()) {
            if (((Integer) comp.getAttribute(COL_INDEX_ATTR)).intValue() == index) {
                return (Column) comp;
            }
        }
        
        return null;
    }
    
    /**
     * Clears the list and status.
     */
    protected void reset() {
        modelAndView.setModel(null);
        model.clear();
        status(null);
    }
    
    protected Row getSelectedItem() {
        return table.getRows().getSelectedRow();
    }
    
    @SuppressWarnings("unchecked")
    protected DAO getSelectedValue() {
        Row item = getSelectedItem();
        
        if (item != null) {
            return (DAO) item.getData();
        }
        
        return null;
    }
    
    /**
     * Initiate asynchronous call to retrieve data from host.
     */
    protected void loadData() {
        dataNeedsUpdate = false;
        asyncAbort();
        reset();
        status("Retrieving " + dataName + "...");
        
        try {
            requestData();
        } catch (Throwable t) {
            status("Error Retrieving " + dataName + "...^" + t.getMessage());
        }
    }
    
    /**
     * Converts a DAO object for rendering.
     * 
     * @param dao DAO object to be rendered.
     * @param columns Returns a list of objects to render, one per column.
     */
    protected abstract void render(DAO dao, List<Object> columns);
    
    /**
     * Render a single item.
     * 
     * @param item List item being rendered.
     * @param dao DAO object
     */
    protected void renderItem(Row item, DAO dao) {
        List<Object> columns = new ArrayList<>();
        boolean error = false;
        
        try {
            render(dao, columns);
        } catch (Exception e) {
            columns.clear();
            columns.add(CWFUtil.formatExceptionForDisplay(e));
            error = true;
        }
        
        item.setVisible(!columns.isEmpty());
        
        for (Object colData : columns) {
            Rowcell cell = null;//TODO: renderer.createCell(item, transformData(colData));
            cell.setData(colData);
            
            if (error) {
                cell.setColspan(colCount);
            }
        }
    }
    
    /**
     * Override to perform any necessary transforms on data before rendering.
     * 
     * @param data Data to transform.
     * @return Transformed data.
     */
    protected Object transformData(Object data) {
        return data;
    }
    
    /**
     * Render the model data.
     */
    protected void renderData() {
        if (model.isEmpty()) {
            status("No " + dataName + " Found");
        } else {
            status(null);
            alphaSort();
            modelAndView.setModel(model);
        }
    }
    
    /**
     * Implement to sort the data before displaying.
     */
    protected void alphaSort() {
        
    }
    
    /**
     * Forces an update of displayed list.
     */
    @Override
    public void refresh() {
        dataNeedsUpdate = true;
        
        if (!deferUpdate || isActive()) {
            loadData();
        } else {
            reset();
        }
    }
    
    protected void status(String message) {
        if (message != null) {
            status.setLabel(StrUtil.piece(message, StrUtil.U));
            status.setHint(StrUtil.piece(message, StrUtil.U, 2, 999));
            ((BaseUIComponent) status.getParent()).setVisible(true);
            columns.setVisible(false);
        } else {
            ((BaseUIComponent) status.getParent()).setVisible(false);
            columns.setVisible(true);
        }
    }
    
    @EventHandler(value = "click", target = "mnuRefresh")
    private void onClick$mnuRefresh() {
        refresh();
    }
    
    @EventHandler(value = "command", target = "@table")
    private void onCommand$table() {
        refresh();
    }
    
    @EventHandler(value = "change", target = "@rows")
    private void onChange$rows(Event event) {
        if (getShowDetailPane() == (event instanceof ChangeEvent)) {
            itemSelected(getSelectedItem());
        }
    }
    
    /**
     * Called when an item is selected. Override for specialized handling.
     * 
     * @param li Selected list item.
     */
    protected void itemSelected(Row li) {
        
    }
    
    @Override
    public void onActivate() {
        super.onActivate();
        
        if (dataNeedsUpdate) {
            loadData();
        }
    }
    
}
