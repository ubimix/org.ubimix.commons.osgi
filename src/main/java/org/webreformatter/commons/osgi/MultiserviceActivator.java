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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import org.webreformatter.commons.osgi.MultiServiceTracker;

/**
 * @author kotelnikov
 */
public class MultiserviceActivator implements BundleActivator {

    protected BundleContext fContext;

    protected MultiServiceTracker fTracker;

    /**
     * 
     */
    public MultiserviceActivator() {
        super();
    }

    /**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
     */
    public void start(BundleContext context) throws Exception {
        fContext = context;
        fTracker = new MultiServiceTracker(context, this);
        fTracker.open();
    }

    /**
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(BundleContext context) throws Exception {
        fTracker.close();
        fTracker = null;
    }

}
