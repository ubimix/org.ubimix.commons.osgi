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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import org.webreformatter.commons.osgi.OSGIObjectActivator;
import org.webreformatter.commons.osgi.OSGIObjectDeactivator;
import org.webreformatter.commons.osgi.OSGIService;
import org.webreformatter.commons.osgi.OSGIServiceActivator;
import org.webreformatter.commons.osgi.OSGIServiceDeactivator;

/**
 * This is a multi-service tracker for individual objects. It is used to
 * automatically activate the given object when all required OSGi services are
 * resolved.
 * 
 * @author kotelnikov
 */
public class ObjectServiceTracker {

    /**
     * This helper class is used as a wrapper for individual OSGi service
     * trackers and it counts the number of registered services. When the number
     * of required services is more than minimal required then it calls the
     * {@link ObjectServiceTracker#incReference()} method to notify that a new
     * service is resolved. The {@link ObjectServiceTracker#decReference()}
     * method is called to notify that at least one service is missing.
     */
    private class TrackHelper {

        private int fCounter = -1;

        private final int fMinCardinality;

        private ServiceTracker fTracker;

        public TrackHelper(final Method setMethod, int minCardinality) {
            final Class<?> type = setMethod.getParameterTypes()[0];
            fTracker = new ServiceTracker(
                fContext,
                type.getName(),
                new ServiceTrackerCustomizer() {
                    public Object addingService(ServiceReference reference) {
                        Object service = fContext.getService(reference);
                        if (service != null) {
                            try {
                                if (setMethod.getParameterTypes().length == 2) {
                                    Map<String, Object> params = getParameters(reference);
                                    call(setMethod, service, params);
                                } else {
                                    call(setMethod, service);
                                }
                                inc();
                            } catch (Throwable e) {
                                handle(e, "ERROR! Can not register service "
                                    + type);
                            }
                        }
                        return service;
                    }

                    private Map<String, Object> getParameters(
                        ServiceReference reference) {
                        Map<String, Object> params = new HashMap<String, Object>();
                        for (String key : reference.getPropertyKeys()) {
                            Object value = reference.getProperty(key);
                            params.put(key, value);
                        }
                        return params;
                    }

                    private void handle(Throwable t, String msg) {
                        if (t instanceof InvocationTargetException) {
                            t = ((InvocationTargetException) t).getCause();
                        }
                        // log.log(Level.SEVERE, msg, t);
                        if (t instanceof Error) {
                            throw (Error) t;
                        } else if (t instanceof RuntimeException) {
                            throw (RuntimeException) t;
                        } else {
                            throw new RuntimeException(t);
                        }
                    }

                    public void modifiedService(
                        ServiceReference reference,
                        Object service) {
                        try {
                            dec();
                            inc();
                        } catch (Throwable e) {
                            handle(e, "ERROR! Can not modify the service "
                                + type);
                        }
                    }

                    public void removedService(
                        ServiceReference reference,
                        Object service) {
                        try {
                            dec();
                            Method removeMethod = fServiceUnloaders.get(type);
                            if (removeMethod != null) {
                                if (removeMethod.getParameterTypes().length == 2) {
                                    Map<String, Object> params = getParameters(reference);
                                    call(removeMethod, service, params);
                                } else {
                                    call(removeMethod, service);
                                }
                            }
                        } catch (Throwable e) {
                            handle(e, "ERROR! Can not deactivate the service "
                                + type);
                        }
                    }

                });
            fMinCardinality = minCardinality;
        }

        /**
         * Closes the underlying tracker.
         * 
         * @throws Exception
         */
        public void close() throws Exception {
            fTracker.close();
            dec();
        }

        /**
         * Decrements the internal counter and if this counter equals to the
         * minimal cardinality of the service then it calls the
         * {@link ObjectServiceTracker#decReference()} method to notify that at
         * least one service is missing.
         * 
         * @throws Exception
         */
        protected void dec() throws Exception {
            boolean dec = false;
            synchronized (this) {
                dec = (fCounter == fMinCardinality);
                fCounter--;
            }
            if (dec)
                decReference();
        }

        /**
         * Increments the internal counter and if this counter equals to the
         * minimal cardinality of the service then it calls the
         * {@link ObjectServiceTracker#incReference()} method to notify that one
         * service dependency is resolved.
         * 
         * @throws Exception
         */
        protected void inc() throws Exception {
            boolean inc = false;
            synchronized (this) {
                fCounter++;
                inc = (fCounter == fMinCardinality);
            }
            if (inc)
                incReference();
        }

        /**
         * Opens the underlying tracker.
         * 
         * @throws Exception
         */
        public void open() throws Exception {
            inc();
            fTracker.open();
        }

    }

    /**
     * The logger used by this class.
     */
    private final static Logger log = Logger
        .getLogger(ObjectServiceTracker.class.getName());

    /**
     * The bundle context used to register trackers.
     */
    private BundleContext fContext;

    /**
     * The counter of resolved services. It is used to define when the object
     * should be activated/deactivated. The object is activated when all requred
     * services are resolved.
     */
    private int fCounter;

    /**
     * The object to activate.
     */
    private Object fObject;

    /**
     * The list of object activator methods. These methods are called when all
     * required services are resolved.
     */
    private List<Method> fObjectActivators = new ArrayList<Method>();

    /**
     * List of deactivator methods. These methods are called when at least one
     * required service is disapered
     */
    private List<Method> fObjectDeactivators = new ArrayList<Method>();

    /**
     * The list of methods returning services provided by the managed object.
     * These methods are called when all required services are resolved just
     * after the object is activated.
     */
    private List<Method> fObjectServiceGetters = new ArrayList<Method>();

    /**
     * The service registration returned by the OSGi framework when the managed
     * object is registered as a service. This field is not empty if the
     * {@link #fServiceType} is not <code>null</code> and the object is really
     * registered as a service.
     */
    private List<ServiceRegistration> fServiceRegistrations = new ArrayList<ServiceRegistration>();

    /**
     * The service interface implemented by the managed object.
     */
    private Class<?> fServiceType;

    /**
     * This map contains remove methods which are used to notify that a service
     * was unregistered.
     */
    protected Map<Class<?>, Method> fServiceUnloaders = new HashMap<Class<?>, Method>();

    /**
     * List of trackers associated with service setters in the managed object.
     */
    private List<TrackHelper> fTrackers = new ArrayList<TrackHelper>();

    /**
     * This constructor initializes the internal list of service setter methods
     * and activator/deactivators defined in the given object. These methods are
     * used by {@link #open()}/{@link #close()} methods of this class.
     * 
     * @param context the OSGi context to set
     * @param object the object to activate
     */
    public ObjectServiceTracker(BundleContext context, Object object) {
        fContext = context;
        fObject = object;

        Class<?> cls = fObject.getClass();
        OSGIService serviceAnnotation = cls.getAnnotation(OSGIService.class);
        if (serviceAnnotation != null) {
            fServiceType = detectServiceType(
                fObject.getClass(),
                serviceAnnotation);
        }
        Method[] methods = cls.getMethods();
        boolean ok = false;
        for (Method method : methods) {
            ok |= addServiceLoader(method)
                || addServiceUnloader(method)
                || addObjectActivator(method)
                || addObjectDeactivator(method)
                || addObjectService(method);
        }
        if (!ok) {
            log.warning("Class does not contain any services or activators: "
                + cls.getName());
        }
    }

    /**
     * Checks if the given method is an object activator and if it is then adds
     * the method to the internal list of object activators.
     * 
     * @param method the method to check
     * @return <code>true</code> if the given method is an object activator
     */
    private boolean addObjectActivator(Method method) {
        if (method.getAnnotation(OSGIObjectActivator.class) == null)
            return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length > 0) {
            throw new IllegalArgumentException("The object activator method "
                + method.getName()
                + " can not have arguments.");
        }
        fObjectActivators.add(method);
        return true;
    }

    /**
     * Checks if the given method is an object deactivator and if it is then
     * adds the method to the internal list of deactivators.
     * 
     * @param method the method to check
     * @return <code>true</code> if the given method is an object deactivator
     */
    private boolean addObjectDeactivator(Method method) {
        if (method.getAnnotation(OSGIObjectDeactivator.class) == null)
            return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length > 0) {
            throw new IllegalArgumentException("The object deactivator method "
                + method.getName()
                + " can not have arguments.");
        }
        fObjectDeactivators.add(method);
        return true;
    }

    /**
     * Checks if the given method is getter method returning a service exposed
     * by the managed object. If so it adds this method to the internal list of
     * service getters.
     * 
     * @param method the method to check
     * @return <code>true</code> if the given method is a method returns a
     *         service exposed by the managed object
     */
    private boolean addObjectService(Method method) {
        if (method.getAnnotation(OSGIService.class) == null)
            return false;
        Class<?>[] params = method.getParameterTypes();
        if (params.length > 1) {
            throw new IllegalArgumentException("The service method "
                + method.getName()
                + " can not have arguments.");
        } else if (params.length == 1
            && !Dictionary.class.isAssignableFrom(params[0])) {
            throw new IllegalArgumentException(
                "Bad service parameters in the method "
                    + method.getName()
                    + ". "
                    + Dictionary.class.getName()
                    + " is expected.");
        }
        fObjectServiceGetters.add(method);
        return true;
    }

    /**
     * Checks if the given method is a service activator and if it is then it
     * adds to the internal map of activators.
     * 
     * @param method the method to check
     * @return <code>true</code> if the given method is a service activator
     */
    private boolean addServiceLoader(Method method) {
        OSGIServiceActivator service = method
            .getAnnotation(OSGIServiceActivator.class);
        if (service == null)
            return false;
        TrackHelper helper;
        Class<?>[] params = method.getParameterTypes();
        if (checkServiceMethodParams(params)) {
            helper = new TrackHelper(method, service.min());
        } else {
            throw new IllegalArgumentException("The method "
                + method.getName()
                + " has to have the type of the service"
                + " and (optionally) a map of service parameters");
        }
        fTrackers.add(helper);
        return true;
    }

    /**
     * Checks if the given method is a service deactivator and if it is then it
     * adds to the internal map of deactivators.
     * 
     * @param method the method to check
     * @return <code>true</code> if the given method is a service deactivator
     */
    private boolean addServiceUnloader(Method method) {
        OSGIServiceDeactivator service = method
            .getAnnotation(OSGIServiceDeactivator.class);
        if (service == null)
            return false;
        Class<?>[] params = method.getParameterTypes();
        if (!checkServiceMethodParams(params)) {
            throw new IllegalArgumentException(
                "The service deactivator method "
                    + method.getName()
                    + " should have exactly one parameter - "
                    + "the service to deactivate.");
        }
        fServiceUnloaders.put(params[0], method);
        return true;
    }

    /**
     * Tries to call the given method with specified parameters. This method do
     * nothing if the method is not defined (if it is <code>null</code>).
     * 
     * @param method the method to call
     * @param params the parameters for the method to call
     * @throws Exception if the method can not be called
     */
    private Object call(Method method, Object... params) throws Exception {
        if (method != null) {
            return method.invoke(fObject, params);
        }
        return null;
    }

    private boolean checkServiceMethodParams(Class<?>[] params) {
        return (params.length == 1)
            || (params.length == 2 && Map.class.isAssignableFrom(params[1]));
    }

    /**
     * Closes this tracker. This method deactivates the managed object and
     * closes all underlying trackers.
     * 
     * @throws Exception
     */
    public void close() throws Exception {
        if (fTrackers.isEmpty()) {
            decReference();
        } else {
            for (TrackHelper tracker : fTrackers) {
                tracker.close();
            }
        }
    }

    /**
     * Decrements the number of available services. If the managed object is
     * active then this method deactivates it.
     * 
     * @throws Exception an exception can be rised if something goes wrong with
     *         object deactivation
     */
    private void decReference() throws Exception {
        if (fCounter == fTrackers.size()) {
            for (ServiceRegistration r : fServiceRegistrations) {
                r.unregister();
            }
            fServiceRegistrations.clear();
            for (Method deactivator : fObjectDeactivators) {
                call(deactivator);
            }
        }
        if (fCounter > 0)
            fCounter--;
    }

    /**
     * Defines the type of the specified service using the object itself and the
     * given service annotation.
     * 
     * @param serviceType the real type of the service object for which the
     *        corresponding service type should be detected and returned; the
     *        returned service type is used to register the service in the OSGi
     *        platform
     * @param serviceAnnotation the annotation for the service
     * @return the type of the specified service corresponding to the given
     *         annotation
     */
    private Class<?> detectServiceType(
        Class<? extends Object> serviceType,
        OSGIService serviceAnnotation) {
        Class<?> annotationType = serviceAnnotation.serviceType();
        if (annotationType == Object.class) {
            annotationType = serviceType;
        } else {
            if (annotationType == null
                || !annotationType.isAssignableFrom(serviceType)) {
                if (annotationType.isInterface()) {
                    throw new IllegalArgumentException(
                        "The service object does not implement the "
                            + annotationType
                            + " interface.");
                } else {
                    throw new IllegalArgumentException(
                        "The service object type is not a sublclass of the "
                            + annotationType
                            + " type.");
                }
            }
        }
        return annotationType;
    }

    /**
     * Increments the counter of available required services. When all services
     * are resolved (when the internal counter equals to the number of trackers)
     * then this method activates the managed object.
     * 
     * @throws Exception an exception can be rised if something goes wrong with
     *         object activation
     */
    private void incReference() throws Exception {
        int size = fTrackers.size();
        if (fCounter < size)
            fCounter++;
        if (fCounter == size) {
            for (Method activator : fObjectActivators) {
                call(activator);
            }
            fServiceRegistrations.clear();
            if (fServiceType != null) {
                ServiceRegistration r = fContext.registerService(fServiceType
                    .getName(), fObject, null);
                fServiceRegistrations.add(r);
            }
            for (Method method : fObjectServiceGetters) {
                try {
                    OSGIService serviceAnnotation = method
                        .getAnnotation(OSGIService.class);
                    Object service = null;
                    Dictionary<?, ?> dictionary = new Hashtable<Object, Object>();
                    if (method.getParameterTypes().length == 1) {
                        service = call(method, dictionary);
                    } else {
                        service = call(method);
                    }
                    Class<?> serviceType = detectServiceType(method
                        .getReturnType(), serviceAnnotation);
                    ServiceRegistration r = fContext.registerService(
                        serviceType.getName(),
                        service,
                        dictionary);
                    fServiceRegistrations.add(r);
                } catch (Exception e) {
                    log.log(
                        Level.WARNING,
                        "Can not register the returned service",
                        e);
                }
            }
        }
    }

    /**
     * Opens all underlying trackers.
     * 
     * @throws Exception
     */
    public void open() throws Exception {
        if (fTrackers.isEmpty()) {
            incReference();
        } else {
            for (TrackHelper tracker : fTrackers) {
                tracker.open();
            }
        }
    }

}
