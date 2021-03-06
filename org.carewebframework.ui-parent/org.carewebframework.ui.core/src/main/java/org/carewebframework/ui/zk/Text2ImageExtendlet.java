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
package org.carewebframework.ui.zk;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.carewebframework.common.ColorUtil;
import org.carewebframework.common.ImageUtil;
import org.carewebframework.ui.FrameworkWebSupport;

import org.zkoss.util.resource.Loader;
import org.zkoss.util.resource.ResourceCache;
import org.zkoss.web.util.resource.Extendlet;
import org.zkoss.web.util.resource.ExtendletConfig;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.http.WebManager;
import org.zkoss.zk.ui.util.WebAppInit;

/**
 * This is a ZK extendlet that intercepts resources with a ".txt2img" extension and returns a png
 * image of the text. A sample url follows:
 * <p>
 * <code>~./.txt2img?text=text to display&amp;font=Arial-Bold-12&amp;bgcolor=yellow&amp;fgcolor=red</code>
 * <p>
 * To active the extendlet, include it as a registered listener in the zk.xml configuration file:
 * 
 * <pre>
 * {@literal
 * <listener>
 *      <listener-class>org.carewebframework.ui.zk.Text2ImageExtendlet</listener-class>
 * </listener>
 * }
 * </pre>
 */
public class Text2ImageExtendlet implements Extendlet, WebAppInit {
    
    
    private class ImageLoader implements Loader<Object, Object> {
        
        
        @Override
        public boolean shallCheck(Object src, long expiredMillis) {
            return false;
        }
        
        @Override
        public long getLastModified(Object src) {
            return 0;
        }
        
        @Override
        public Object load(Object src) throws Exception {
            Map<String, String> map = FrameworkWebSupport.queryStringToMap(src.toString());
            String text = getParam("text", map);
            Font font = toFont(getParam("font", map));
            Color backColor = ColorUtil.toColor(getParam("bgcolor", map));
            Color fontColor = ColorUtil.toColor(getParam("fgcolor", map));
            BufferedImage bi = ImageUtil.toImage(text, font, backColor, fontColor);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", os);
            return os.toByteArray();
        }
        
        private String getParam(String param, Map<String, String> map) {
            String val = map.get(param);
            return val == null || val.isEmpty() ? null : val;
        }
        
        private Font toFont(String font) {
            return font == null ? null : Font.decode(font);
        }
    }
    
    private ResourceCache<Object, Object> _cache;
    
    @Override
    public void init(ExtendletConfig config) {
        ImageLoader loader = new ImageLoader();
        _cache = new ResourceCache<>(loader, 131);
        _cache.setMaxSize(256);
        _cache.setLifetime(60 * 60 * 1000); //1hr
        _cache.setCheckPeriod(60 * 60 * 1000); //1hr
    }
    
    @Override
    public boolean getFeature(int feature) {
        return feature == ALLOW_DIRECT_INCLUDE;
    }
    
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response, String path) throws ServletException,
                                                                                               IOException {
        byte[] data = (byte[]) _cache.get(request.getQueryString());
        response.setContentType("image/png");
        response.getOutputStream().write(data);
    }
    
    @Override
    public void init(WebApp wapp) throws Exception {
        WebManager.getWebManager(wapp).getClassWebResource().addExtendlet("txt2img", this);
    }
    
}
