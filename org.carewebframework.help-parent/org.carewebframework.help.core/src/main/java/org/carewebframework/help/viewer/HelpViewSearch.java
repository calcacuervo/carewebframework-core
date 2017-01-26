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
package org.carewebframework.help.viewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.carewebframework.common.StrUtil;
import org.carewebframework.help.HelpSearchHit;
import org.carewebframework.help.HelpTopic;
import org.carewebframework.help.HelpViewType;
import org.carewebframework.help.IHelpSearch.IHelpSearchListener;
import org.carewebframework.help.IHelpSet;
import org.carewebframework.web.annotation.EventHandler;
import org.carewebframework.web.annotation.WiredComponent;
import org.carewebframework.web.component.BaseComponent;
import org.carewebframework.web.component.Cell;
import org.carewebframework.web.component.Image;
import org.carewebframework.web.component.Label;
import org.carewebframework.web.component.Listitem;
import org.carewebframework.web.component.Row;
import org.carewebframework.web.component.Table;
import org.carewebframework.web.component.Textbox;
import org.carewebframework.web.model.IComponentRenderer;
import org.carewebframework.web.model.IModelAndView;
import org.carewebframework.web.model.ListModel;

/**
 * Tab supporting the help system search function. Consists of a text box into which the user may
 * enter a search expression (including boolean operators) and a list box to display the results of
 * the search.
 */
public class HelpViewSearch extends HelpViewBase implements IComponentRenderer<Listitem, HelpSearchHit>, IHelpSearchListener {
    
    @WiredComponent
    private Textbox txtSearch;
    
    @WiredComponent
    private Table tblSrchResults;
    
    @WiredComponent
    private Label lblNoResultsFound;
    
    private final Image[] icons = new Image[3];
    
    private final List<IHelpSet> helpSets = new ArrayList<>();
    
    private IModelAndView<Row, HelpSearchHit> modelAndView;
    
    private double tertile1;
    
    private double tertile2;
    
    /**
     * Create the help tab for the specified viewer and viewType.
     * 
     * @param viewer The help viewer.
     * @param viewType The view type.
     */
    public HelpViewSearch(HelpViewer viewer, HelpViewType viewType) {
        super(viewer, viewType, "helpSearchTab.cwf");
    }
    
    @Override
    public void afterInitialized(BaseComponent comp) {
        super.afterInitialized(comp);
        modelAndView = tblSrchResults.getRows().getModelAndView(HelpSearchHit.class);
    }
    
    /**
     * Sets the focus to the search text box when the tab is selected.
     * 
     * @see org.carewebframework.help.viewer.HelpViewBase#onSelect()
     */
    @Override
    public void onSelect() {
        super.onSelect();
        txtSearch.selectAll();
        txtSearch.setFocus(true);
    }
    
    /**
     * Sets the currently viewed topic when a search result is selected.
     */
    @EventHandler(value = "change", target = "@tblSrchResults")
    private void onSelect$tblSrchResults() {
        Row row = tblSrchResults.getRows().getSelected().get(0);
        setTopic((HelpTopic) row.getData());
    }
    
    /**
     * Perform search when user presses enter button.
     */
    @EventHandler(value = "enter", target = "txtSearch")
    private void onEnter$txtSearch() {
        onClick$btnSearch();
    }
    
    /**
     * Perform the search and display the results.
     */
    @EventHandler(value = "click", target = "btnSearch")
    private void onClick$btnSearch() {
        modelAndView.setModel(null);
        tblSrchResults.getRows().destroyChildren();
        String query = txtSearch.getValue();
        showMessage(null);
        
        if (query != null && query.trim().length() > 0) {
            HelpUtil.getSearchService().search(query, helpSets, this);
        } else {
            showMessage("cwf.help.tab.search.noentry");
        }
    }
    
    /**
     * Returns the icon that represents the specified score. There are three icons available based
     * on within which tertile the score falls.
     * 
     * @param score The relevancy score.
     * @return Image to represent relevancy.
     */
    private Image toImage(double score) {
        int tertile = score >= tertile2 ? 2 : score >= tertile1 ? 1 : 0;
        Image aimage = icons[tertile];
        
        if (aimage == null) {
            String img = tertile <= 0 ? "empty" : tertile == 1 ? "half" : "full";
            aimage = HelpUtil.getImageContent(img + ".png");
            icons[tertile] = aimage;
        }
        
        return aimage;
    }
    
    /**
     * Displays the specified message. The list box is hidden if the message is not empty.
     * 
     * @param message Message to display.
     */
    private void showMessage(String message) {
        message = message == null ? null : StrUtil.getLabel(message);
        lblNoResultsFound.setLabel(message);
        lblNoResultsFound.setVisible(!StringUtils.isEmpty(message));
        tblSrchResults.setVisible(!lblNoResultsFound.isVisible());
    }
    
    /**
     * Renders the list box contents.
     * 
     * @param qr The search hit to render.
     */
    @Override
    public Listitem render(HelpSearchHit qr) {
        Listitem item = new Listitem();
        double score = qr.getConfidence();
        item.setData(qr.getTopic());
        Cell lc = new Cell();
        lc.addChild(toImage(score));
        String tt = StrUtil.formatMessage("@cwf.help.tab.search.score", score);
        lc.setHint(tt);
        item.addChild(lc);
        lc = new Cell(qr.getTopic().getLabel());
        item.addChild(lc);
        lc = new Cell(qr.getTopic().getSource());
        item.addChild(lc);
        return item;
    }
    
    @Override
    public void onSearchComplete(List<HelpSearchHit> searchResults) {
        Collections.sort(searchResults);
        
        if (searchResults.isEmpty()) {
            showMessage("cwf.help.tab.search.noresults");
            return;
        }
        
        double highscore = searchResults.get(0).getConfidence();
        double lowscore = searchResults.get(searchResults.size() - 1).getConfidence();
        double interval = (highscore - lowscore) / 3;
        tertile1 = lowscore + interval;
        tertile2 = tertile1 + interval;
        modelAndView.setModel(new ListModel<>(searchResults));
    }
    
    public void mergeHelpSet(IHelpSet helpSet) {
        helpSets.add(helpSet);
    }
}