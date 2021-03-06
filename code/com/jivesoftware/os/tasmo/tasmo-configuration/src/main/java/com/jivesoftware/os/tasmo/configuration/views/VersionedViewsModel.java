/*
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-$year$ Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package com.jivesoftware.os.tasmo.configuration.views;

import com.jivesoftware.os.tasmo.id.ChainedVersion;
import java.util.Map;

/**
 *
 * @author jonathan.colt
 */
class VersionedViewsModel {

    private final ChainedVersion version;
    private final Map<String, Map<Integer, PathAndDictionary>> viewValueBindings;

    VersionedViewsModel(ChainedVersion version, Map<String, Map<Integer, PathAndDictionary>> viewValueBindings) {
        this.version = version;
        this.viewValueBindings = viewValueBindings;
    }

    public ChainedVersion getVersion() {
        return version;
    }

    public Map<String, Map<Integer, PathAndDictionary>> getViewsModel() {
        return viewValueBindings;
    }

    @Override
    public String toString() {
        return "VersionedEventsModel{" + "version=" + version + ", viewValueBindings=" + viewValueBindings + '}';
    }
}
