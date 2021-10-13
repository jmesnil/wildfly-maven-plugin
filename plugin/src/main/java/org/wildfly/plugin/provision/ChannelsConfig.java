/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.provision;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author jdenise
 */
public class ChannelsConfig {
    private List<String> urls = Collections.emptyList();
    private boolean disableLatestResolution;
    private File localCache;

    /**
     * @return the urls
     */
    public List<String> getUrls() {
        return urls;
    }

    /**
     * @param urls the urls to set
     */
    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    /**
     * @return the disableLatestResolution
     */
    public boolean isDisableLatestResolution() {
        return disableLatestResolution;
    }

    /**
     * @param disableLatestResolution the disableLatestResolution to set
     */
    public void setDisableLatestResolution(boolean disableLatestResolution) {
        this.disableLatestResolution = disableLatestResolution;
    }

    /**
     * @return the localCache
     */
    public File getLocalCache() {
        return localCache;
    }

    /**
     * @param localCache the localCache to set
     */
    public void setLocalCache(File localCache) {
        this.localCache = localCache;
    }
}
