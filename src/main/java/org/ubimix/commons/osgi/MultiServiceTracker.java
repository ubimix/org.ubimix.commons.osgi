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
package org.ubimix.commons.osgi;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.BundleContext;

import org.ubimix.commons.osgi.ObjectServiceTracker;

/**
 * This class is used to activate multiple object at the same time. For each of
 * managed objects a new instance of the {@link ObjectServiceTracker} is
 * created.
 * 
 * @author kotelnikov
 */
public class MultiServiceTracker {

    /**
     * List of trackers activating/deactivating objects.
     */
    private List<ObjectServiceTracker> fTrackers = new ArrayList<ObjectServiceTracker>();

    /**
     * @param objects
     */
    public MultiServiceTracker(BundleContext context, Object... objects) {
        for (Object obj : objects) {
            ObjectServiceTracker tracker = new ObjectServiceTracker(
                context,
                obj);
            fTrackers.add(tracker);
        }
    }

    /**
     * Closes all underlying trackers.
     * 
     * @throws Exception
     */
    public void close() throws Exception {
        for (ObjectServiceTracker tracker : fTrackers) {
            tracker.close();
        }
    }

    /**
     * Opens all underlying trackers.
     * 
     * @throws Exception
     */
    public void open() throws Exception {
        for (ObjectServiceTracker tracker : fTrackers) {
            tracker.open();
        }
    }
}
