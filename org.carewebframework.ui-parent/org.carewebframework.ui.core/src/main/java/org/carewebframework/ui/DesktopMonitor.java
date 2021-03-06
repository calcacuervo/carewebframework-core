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
package org.carewebframework.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.carewebframework.api.FrameworkUtil;
import org.carewebframework.api.domain.IUser;
import org.carewebframework.api.event.IEventManager;
import org.carewebframework.api.event.IGenericEvent;
import org.carewebframework.api.security.ISecurityService;
import org.carewebframework.api.thread.ThreadUtil;
import org.carewebframework.common.DateUtil;
import org.carewebframework.common.DateUtil.TimeUnit;
import org.carewebframework.common.StrUtil;
import org.carewebframework.ui.Application.Command;
import org.carewebframework.ui.zk.MessageWindow;
import org.carewebframework.ui.zk.ZKUtil;

import org.zkoss.zk.au.AuRequest;
import org.zkoss.zk.au.AuService;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.DesktopUnavailableException;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.HtmlBasedComponent;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.ShadowElement;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.sys.SessionCtrl;
import org.zkoss.zk.ui.util.UiLifeCycle;
import org.zkoss.zul.Label;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Window;

/**
 * Desktop timeout thread that evaluates ZK's Desktop and the time a request was last sent. Used to
 * notify user regarding their inactivity and take appropriate action.
 */
public class DesktopMonitor extends Thread {
    
    private static final Log log = LogFactory.getLog(DesktopMonitor.class);
    
    /**
     * Reflects the different execution states for the thread.
     */
    private enum State {
        INITIAL, COUNTDOWN, TIMEDOUT, DEAD
    };
    
    /**
     * Actions to be performed in an event thread.
     */
    private enum Action {
        UPDATE_COUNTDOWN, UPDATE_MODE, LOGOUT
    }
    
    /**
     * Available timeout modes.
     */
    private enum Mode {
        BASELINE, LOCK, LOGOUT, SHUTDOWN;
        
        /**
         * Returns a label from a label reference.
         * 
         * @param label Label reference with placeholder for mode.
         * @param params Optional parameters.
         * @return Fully formatted label value.
         */
        public String getLabel(String label, Object... params) {
            return StrUtil.getLabel(format(label), params);
        }
        
        /**
         * Formats a string containing a placeholder ('%') for mode.
         * 
         * @param value Value to format.
         * @return Formatted value with placeholder(s) replaced by mode.
         */
        public String format(String value) {
            return value.replace("%", name().toLowerCase());
        }
    }
    
    /**
     * Path to the zul code that will be used to display the count down.
     */
    private static final String DESKTOP_TIMEOUT_ZUL = org.carewebframework.ui.zk.Constants.RESOURCE_PREFIX
            + "desktopTimeoutWarning.zul";
            
    /**
     * Events that will not reset keepalive timer.
     */
    private static final String[] ignore = new String[] { "dummy", Events.ON_TIMER, Events.ON_CLIENT_INFO };
    
    private static final String ATTR_LOGGING_OUT = "@logging_out";
    
    private static final String TIMEOUT_WARNING = "cwf.timeout.%.warning.message";
    
    private static final String TIMEOUT_EXPIRATION = "cwf.timeout.%.reason.message";
    
    private static final String SCLASS_COUNTDOWN = "cwf-timeout-%-countdown";
    
    private static final String SCLASS_IDLE = "cwf-timeout-%-idle";
    
    private final Map<Mode, Long> countdownDuration = new HashMap<>();
    
    private final Map<Mode, Long> inactivityDuration = new HashMap<>();
    
    /**
     * Maximum interval of inactivity after which a desktop is assumed to be dead (in ms).
     */
    private long maximumInactivityInterval = 300000;
    
    /**
     * How often to update the displayed count down timer (in ms).
     */
    private long countdownInterval = 2000;
    
    private boolean canAutoLock = true;
    
    private Mode mode = Mode.BASELINE;
    
    private Mode previousMode = mode;
    
    private Window timeoutWindow;
    
    private HtmlBasedComponent timeoutPanel;
    
    private Label lblDuration;
    
    private Label lblLocked;
    
    private Label lblInfo;
    
    private Textbox txtPassword;
    
    private boolean terminate;
    
    private final Desktop desktop;
    
    private long pollingInterval;
    
    private long lastKeepAlive;
    
    private long lastActivity;
    
    private boolean desktopDead;
    
    private long countdown;
    
    private State state;
    
    private ISecurityService securityService;
    
    private IEventManager eventManager;
    
    private Set<String> autoLockingExclusions = new TreeSet<>();
    
    private final Object monitor = new Object();
    
    private final UiLifeCycle uiLifeCycle = new UiLifeCycle() {
        
        /**
         * Attaches the time out warning zul to the desktop root. This markup remains hidden until
         * the timeout count down commences.
         * 
         * @see org.zkoss.zk.ui.util.UiLifeCycle#afterPageAttached(org.zkoss.zk.ui.Page,
         *      org.zkoss.zk.ui.Desktop)
         */
        @Override
        public void afterPageAttached(Page page, Desktop desktop) {
            desktop.removeListener(this);
            timeoutWindow = (Window) desktop.getExecution().createComponents(DESKTOP_TIMEOUT_ZUL, null, null);
            ZKUtil.wireController(timeoutWindow, DesktopMonitor.this);
            IUser user = securityService.getAuthenticatedUser();
            lblLocked.setValue(
                Mode.BASELINE.getLabel(TIMEOUT_EXPIRATION, user.getFullName() + "@" + user.getSecurityDomain().getName()));
            desktop.addListener(desktopActivityMonitor);
            ThreadUtil.startThread(DesktopMonitor.this);
        }
        
        /*
         * Not used.
         */
        @Override
        public void afterComponentAttached(Component cmp, Page page) {
        }
        
        /*
         * Not used.
         */
        @Override
        public void afterComponentDetached(Component cmp, Page page) {
        }
        
        /*
         * Not used.
         */
        @Override
        public void afterComponentMoved(Component cmp1, Component cmp2, Component cmp3) {
        }
        
        /*
         * Not used.
         */
        @Override
        public void afterPageDetached(Page page, Desktop desktop) {
        }
        
        @Override
        public void afterShadowAttached(ShadowElement shadow, Component host) {
        }
        
        @Override
        public void afterShadowDetached(ShadowElement shadow, Component prevhost) {
        }
        
    };
    
    /**
     * Monitors Ajax traffic to determine client activity.
     */
    private final AuService desktopActivityMonitor = new AuService() {
        
        /**
         * Tracks desktop activity.
         * 
         * @param request The asynchronous update request
         * @param everError Whether error occurred prior to processing request
         * @return whether the process has completed (always returns false)
         */
        @Override
        public boolean service(AuRequest request, boolean everError) {
            resetActivity(isKeepAliveRequest(request));
            return false;
        }
        
        /**
         * Determines if request is a 'keep alive' request.
         * 
         * @param request The inbound request.
         * @return keepAlive True if request should reset inactivity timeout.
         */
        private boolean isKeepAliveRequest(AuRequest request) {
            return !ArrayUtils.contains(ignore, request.getCommand());
        }
    };
    
    /**
     * Event listener to handle desktop actions in event thread.
     */
    private final EventListener<Event> actionHandler = new EventListener<Event>() {
        
        @Override
        public void onEvent(Event event) throws Exception {
            Action action = (Action) event.getData();
            trace(action.name());
            
            switch (action) {
                case UPDATE_COUNTDOWN:
                    String s = nextMode().getLabel(TIMEOUT_WARNING, DateUtil.formatDuration(countdown, TimeUnit.SECONDS));
                    lblDuration.setValue(s);
                    setSclass(SCLASS_COUNTDOWN);
                    ZKUtil.toggleSclass(timeoutPanel, "alert-danger", "alert-warning", countdown <= 10000);
                    resetActivity(false);
                    break;
                    
                case UPDATE_MODE:
                    setSclass(SCLASS_IDLE);
                    timeoutWindow.setMode(mode == Mode.LOCK ? "highlighted" : "embedded");
                    txtPassword.setFocus(mode == Mode.LOCK);
                    Application.getDesktopInfo(desktop).sendToSpawned(mode == Mode.LOCK ? Command.LOCK : Command.UNLOCK);
                    break;
                    
                case LOGOUT:
                    terminate = true;
                    timeoutWindow.setVisible(false);
                    securityService.logout(true, null, mode.getLabel(TIMEOUT_EXPIRATION));
                    break;
            }
        }
        
        private void setSclass(String sclass) {
            if (mode == Mode.SHUTDOWN && previousMode == Mode.LOCK) {
                timeoutWindow.setSclass(mode.format(sclass) + " " + previousMode.format(sclass));
            } else {
                timeoutWindow.setSclass(mode.format(sclass));
            }
        }
        
    };
    
    private final IGenericEvent<Object> desktopEventListener = new IGenericEvent<Object>() {
        
        @Override
        public void eventCallback(String eventName, Object eventData) {
            if (eventName.equals(Constants.SHUTDOWN_ABORT_EVENT)) {
                abortShutdown(eventData == null ? null : eventData.toString());
            } else if (eventName.equals(Constants.SHUTDOWN_START_EVENT)) {
                startShutdown(NumberUtils.toLong(eventData == null ? "" : eventData.toString()));
            } else if (eventName.equals(Constants.SHUTDOWN_EVENT)) {
                updateShutdown(NumberUtils.toLong(StrUtil.piece(eventData.toString(), StrUtil.U)) * 1000);
            } else if (eventName.equals(Constants.LOCK_EVENT)) {
                lockDesktop(eventData == null || BooleanUtils.toBoolean(eventData.toString()));
            }
        }
    };
    
    private void setMode(Mode newMode) {
        resetActivity(true);
        mode = newMode == null ? previousMode : newMode;
        pollingInterval = mode == newMode ? pollingInterval : inactivityDuration.get(mode);
        countdown = countdownDuration.get(mode);
        previousMode = mode == Mode.SHUTDOWN ? previousMode : mode;
        queueDesktopAction(Action.UPDATE_MODE);
        wakeup();
    }
    
    /**
     * Create a monitor instance for the specified desktop.
     * 
     * @param desktop Desktop with which this thread will be associated.
     */
    public DesktopMonitor(Desktop desktop) {
        this.desktop = desktop;
        setName("DesktopMonitor-" + desktop.getId());
        inactivityDuration.put(Mode.BASELINE, 900000L);
        inactivityDuration.put(Mode.LOCK, 900000L);
        inactivityDuration.put(Mode.LOGOUT, 0L);
        inactivityDuration.put(Mode.SHUTDOWN, 0L);
        countdownDuration.put(Mode.BASELINE, 60000L);
        countdownDuration.put(Mode.LOCK, 60000L);
        countdownDuration.put(Mode.LOGOUT, 60000L);
    }
    
    /**
     * Aborts any shutdown in progress.
     * 
     * @param message Optional message to send to user.
     */
    public void abortShutdown(String message) {
        if (mode == Mode.SHUTDOWN) {
            updateShutdown(0);
            eventManager.fireLocalEvent(MessageWindow.EVENT_SHOW,
                StringUtils.isEmpty(message) ? StrUtil.getLabel("cwf.timeout.shutdown.abort.message") : message);
        }
    }
    
    /**
     * Starts the shutdown sequence based on the specified delay.
     * 
     * @param delay Delay in ms. If zero or negative, uses the default delay of 5 minutes.
     */
    public void startShutdown(long delay) {
        updateShutdown(delay > 0 ? delay : 5 * 60000);
    }
    
    /**
     * Updates the shutdown state.
     * 
     * @param delay If positive integer, enables shutdown and begins countdown at specified # of ms.
     *            If zero or negative, aborts any shutdown in progress.
     */
    private void updateShutdown(long delay) {
        countdownDuration.put(Mode.SHUTDOWN, delay);
        setMode(delay > 0 ? Mode.SHUTDOWN : previousMode);
    }
    
    /**
     * Resets the activity timer. Note that we always keep the session alive since the desktop
     * monitor is responsible for handling the session timeout.
     * 
     * @param resetKeepAlive If true, the keepalive timer is reset.
     */
    private synchronized void resetActivity(boolean resetKeepAlive) {
        lastActivity = System.currentTimeMillis();
        ((SessionCtrl) desktop.getSession()).notifyClientRequest(true);
        
        if (resetKeepAlive) {
            lastKeepAlive = lastActivity;
        }
    }
    
    /**
     * Processes a polling request.
     * 
     * @throws Exception Unspecified exception.
     */
    private void process() throws Exception {
        long now = System.currentTimeMillis();
        long silence = now - lastActivity;
        long interval = now - lastKeepAlive;
        State oldState = state;
        long delta = inactivityDuration.get(mode) - interval;
        state = silence >= maximumInactivityInterval ? State.DEAD
                : delta > 0 ? State.INITIAL : countdown <= 0 ? State.TIMEDOUT : State.COUNTDOWN;
        boolean stateChanged = oldState != state;
        
        switch (state) {
            case INITIAL:
                if (stateChanged) {
                    pollingInterval = inactivityDuration.get(mode);
                    countdown = countdownDuration.get(mode);
                    queueDesktopAction(Action.UPDATE_MODE);
                } else {
                    pollingInterval = delta;
                }
                
                break;
                
            case COUNTDOWN:
                if (stateChanged) {
                    pollingInterval = countdownInterval;
                } else {
                    countdown -= countdownInterval;
                }
                
                if (countdown > 0) {
                    queueDesktopAction(Action.UPDATE_COUNTDOWN);
                    break;
                }
                
                // fall through is intentional here.
                
            case TIMEDOUT:
                setMode(nextMode());
                
                if (mode == Mode.LOGOUT) {
                    requestLogout();
                }
                
                break;
                
            case DEAD:
                this.terminate = true;
                this.desktopDead = true;
                break;
        }
    }
    
    /**
     * Returns the next mode in the timeout sequence.
     * 
     * @return Next mode.
     */
    private Mode nextMode() {
        switch (mode) {
            case BASELINE:
                return canAutoLock ? Mode.LOCK : Mode.LOGOUT;
                
            default:
                return Mode.LOGOUT;
        }
    }
    
    /**
     * Queues a logout request.
     */
    private void requestLogout() {
        boolean inProgress = desktop.hasAttribute(ATTR_LOGGING_OUT);
        
        if (!inProgress) {
            desktop.setAttribute(ATTR_LOGGING_OUT, true);
            queueDesktopAction(Action.LOGOUT);
        } else {
            log.debug("Logout already underway");
        }
    }
    
    /**
     * Queues a desktop action for deferred execution.
     * 
     * @param action The action to queue.
     */
    private void queueDesktopAction(Action action) {
        Event actionEvent = new Event("onAction", null, action);
        
        if (Events.inEventListener()) {
            try {
                actionHandler.onEvent(actionEvent);
            } catch (Exception e) {
                log.error("Error executing desktop action.", e);
            }
        } else {
            Executions.schedule(desktop, actionHandler, actionEvent);
        }
    }
    
    /**
     * Resets the keep alive timer when the "keep open" button is clicked.
     */
    public void onClick$btnKeepOpen() {
        resetActivity(true);
        wakeup();
    }
    
    public void onClick$btnLogout() {
        securityService.logout(true, null, null);
    }
    
    public void onClick$btnUnlock() {
        String s = txtPassword.getText();
        txtPassword.setText(null);
        lblInfo.setValue(null);
        txtPassword.focus();
        
        if (!StringUtils.isEmpty(s)) {
            if (securityService.validatePassword(s)) {
                setMode(Mode.BASELINE);
            } else {
                lblInfo.setValue(StrUtil.getLabel("cwf.timeout.lock.badpassword.message"));
            }
        }
    }
    
    /**
     * Log a trace message.
     * 
     * @param message The text message to log.
     */
    private void trace(String message) {
        trace(message, null);
    }
    
    /**
     * Log a trace message.
     * 
     * @param label Text to precede the displayed value.
     * @param value The value to display (may be null).
     */
    private void trace(String label, Object value) {
        if (log.isTraceEnabled()) {
            log.trace(this.desktop + " " + label + (value == null ? "" : " = " + value));
        }
    }
    
    /**
     * Thread execution loop.
     * 
     * @see java.lang.Thread#run()
     */
    @Override
    public void run() {
        if (log.isTraceEnabled()) {
            trace("The DesktopMonitor has started", getName());
        }
        
        setMode(Mode.BASELINE);
        
        synchronized (monitor) {
            while (!terminate && desktop.isAlive() && desktop.isServerPushEnabled()
                    && !Thread.currentThread().isInterrupted()) {
                try {
                    process();
                    monitor.wait(pollingInterval);
                } catch (DesktopUnavailableException e) {
                    log.warn(desktop + " DesktopUnavailableException: " + e.getMessage());
                } catch (InterruptedException e) {
                    log.warn(String.format("%s interrupted. Terminating.", this.getClass().getName()));
                    terminate = true;
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error(desktop + " : " + e.getMessage(), e);
                    throw UiException.Aide.wrap(e);
                }
            }
        }
        
        if (log.isTraceEnabled()) {
            trace("The DesktopMonitor terminated", getName());
        }
        
        if (desktopDead && desktop.isAlive()) {
            log.warn("Desktop presumed dead due to prolonged inactivity: " + this.desktop);
            Application.getInstance().register(this.desktop, false);
        }
    }
    
    /**
     * Wakes up the background thread.
     * 
     * @return True if the operation was successful.
     */
    private synchronized boolean wakeup() {
        try {
            synchronized (monitor) {
                monitor.notify();
            }
            return true;
        } catch (Throwable t) {
            log.warn("Unexpected exception.", t);
            return false;
        }
    }
    
    /**
     * Called by the IOC container after all properties have been set.
     */
    public void init() {
        desktop.addListener(uiLifeCycle);
        eventManager.subscribe(Constants.DESKTOP_EVENT, desktopEventListener);
        String path = desktop.getRequestPath();
        path = path.startsWith("/") ? path.substring(1) : path;
        canAutoLock = !autoLockingExclusions.contains(path);
    }
    
    /**
     * Called by IOC container during bean destruction.
     */
    public void tearDown() {
        eventManager.unsubscribe(Constants.DESKTOP_EVENT, desktopEventListener);
        desktop.removeListener(uiLifeCycle);
        terminate = true;
        wakeup();
    }
    
    public void lockDesktop(boolean lock) {
        if (mode != Mode.SHUTDOWN) {
            setMode(lock ? Mode.LOCK : Mode.BASELINE);
        }
    }
    
    /**
     * Return the maximum interval of inactivity after which a desktop is assumed to be dead (in
     * ms). Note: setting this too short will result in a desktop whose event thread is busy in a
     * prolonged operation being prematurely discarded.
     * 
     * @return maximum inactivity interval
     */
    public long getMaximumInactivityInterval() {
        return maximumInactivityInterval;
    }
    
    /**
     * Set the maximum interval of inactivity after which a desktop is assumed to be dead (in ms).
     * Note: setting this too short will result in a desktop whose event thread is busy in a
     * prolonged operation being prematurely discarded.
     * 
     * @param maximumInactivityInterval Maximum inactivity interval in ms.
     */
    public void setMaximumInactivityInterval(long maximumInactivityInterval) {
        this.maximumInactivityInterval = maximumInactivityInterval;
    }
    
    /**
     * Return how often to update the displayed count down timer (in ms).
     * 
     * @return countdown interval
     */
    public long getCountdownInterval() {
        return countdownInterval;
    }
    
    /**
     * Set how often to update the displayed count down timer (in ms).
     * 
     * @param countdownInterval The countdown interval in ms.
     */
    public void setCountdownInterval(long countdownInterval) {
        this.countdownInterval = countdownInterval;
    }
    
    public ISecurityService getSecurityService() {
        return securityService;
    }
    
    public void setSecurityService(ISecurityService securityService) {
        this.securityService = securityService;
    }
    
    public IEventManager getEventManager() {
        return eventManager;
    }
    
    public void setEventManager(IEventManager eventManager) {
        this.eventManager = eventManager;
    }
    
    public long getBaselineInactivityDuration() {
        return inactivityDuration.get(Mode.BASELINE);
    }
    
    public void setBaselineInactivityDuration(long duration) {
        inactivityDuration.put(Mode.BASELINE, duration);
    }
    
    public long getLockInactivityDuration() {
        return inactivityDuration.get(Mode.LOCK);
    }
    
    public void setLockInactivityDuration(long duration) {
        inactivityDuration.put(Mode.LOCK, duration);
    }
    
    public long getBaselineCountdownDuration() {
        return countdownDuration.get(Mode.BASELINE);
    }
    
    public void setBaselineCountdownDuration(long duration) {
        countdownDuration.put(Mode.BASELINE, duration);
    }
    
    public long getLockCountdownDuration() {
        return countdownDuration.get(Mode.LOCK);
    }
    
    public void setLockCountdownDuration(long duration) {
        countdownDuration.put(Mode.LOCK, duration);
    }
    
    /**
     * @return Set of application names (FrameworkUtil.getAppName) to exclude from automatic
     *         locking.
     * @see FrameworkUtil#getAppName()
     */
    public Set<String> getAutoLockingExclusions() {
        return autoLockingExclusions;
    }
    
    /**
     * Set of application names (FrameworkUtil.getAppName) to exclude from automatic locking.
     * 
     * @param autoLockingExclusions The set of exclusions.
     * @see FrameworkUtil#getAppName()
     */
    public void setAutoLockingExclusions(Set<String> autoLockingExclusions) {
        this.autoLockingExclusions = autoLockingExclusions;
    }
    
}
