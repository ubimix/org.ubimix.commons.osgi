/* ************************************************************************** *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 * 
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * ************************************************************************** */
package org.webreformatter.commons.osgi;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

/**
 * @author kotelnikov
 */
public abstract class ConfigurableMultiserviceActivator
    implements
    BundleActivator {

    protected BundleContext fContext;

    private final Logger fLogger = Logger.getLogger(getClass().getName());

    private ServiceRegistration fManagedServiceRegistration;

    protected Dictionary<?, ?> fProperties;

    private MultiServiceTracker fTracker;

    /**
     * 
     */
    public ConfigurableMultiserviceActivator() {
        super();
    }

    /**
     * This method checks that the specified properties were modified and that
     * internal services should be re-loaded and re-configured; it returns
     * <code>true</code> if the service should be re-configured.
     * 
     * @param properties the properties to check
     * @return <code>true</code> if the specified configuration was modified
     */
    protected boolean checkPropertiesModifications(Dictionary<?, ?> properties) {
        return (fProperties == null || !fProperties.equals(properties));
    }

    private synchronized void closeTracker() throws Exception {
        if (fTracker != null) {
            fTracker.close();
            fTracker = null;
        }
    }

    public Dictionary<?, ?> getfProperties() {
        return fProperties;
    }

    protected String getServiceID() {
        String id = getClass().getPackage().getName();
        return id;
    }

    protected Object[] getTrackedObjects() {
        return new Object[] { this };
    }

    protected void handleError(String msg, Throwable e) {
        fLogger.log(Level.WARNING, msg, e);
    }

    private synchronized void openTracker() throws Exception {
        Object[] trackedObjects = getTrackedObjects();
        fTracker = new MultiServiceTracker(fContext, trackedObjects);
        fTracker.open();
    }

    /**
     * Returns <code>true</code> if the tracker should be reloaded when services
     * are changed.
     * 
     * @return <code>true</code> if the tracker should be reloaded when services
     *         are changed
     */
    protected boolean reloadOnUpdate() {
        return false;
    }

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public synchronized void start(BundleContext context) throws Exception {
        fContext = context;
        Dictionary<String, String> params = new Hashtable<String, String>();
        String serviceID = getServiceID();
        params.put(Constants.SERVICE_PID, serviceID);
        ManagedService managedService = new ManagedService() {
            @SuppressWarnings("rawtypes")
            public void updated(Dictionary properties)
                throws ConfigurationException {
                try {
                    synchronized (ConfigurableMultiserviceActivator.this) {
                        boolean modified = checkPropertiesModifications(properties);
                        fProperties = properties;
                        if (modified && (fTracker == null || reloadOnUpdate())) {
                            closeTracker();
                            openTracker();
                        }
                    }
                } catch (Exception e) {
                    handleError("Can not update the configuration", e);
                    throw new ConfigurationException(null, e.getMessage());
                }
            }
        };
        fManagedServiceRegistration = fContext.registerService(
            ManagedService.class.getName(),
            managedService,
            params);
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public synchronized void stop(BundleContext context) throws Exception {
        if (fManagedServiceRegistration != null) {
            fManagedServiceRegistration.unregister();
            fManagedServiceRegistration = null;
        }
        closeTracker();
    }

}
