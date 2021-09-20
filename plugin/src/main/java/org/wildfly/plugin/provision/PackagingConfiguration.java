/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.plugin.provision;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.galleon.maven.plugin.util.Configuration;
import org.jboss.galleon.maven.plugin.util.FeaturePack;
import org.wildfly.plugin.common.PropertyNames;

public class PackagingConfiguration extends Configuration {

    /**
     * The path to the {@code provisioning.xml} file to use. Note that this cannot be used with the {@code feature-packs}
     * or {@code configurations}.
     * If the provisioning file is not absolute, it has to be relative to the project base directory.
     */
    @Parameter(alias = "provisioning-file", property = PropertyNames.WILDFLY_PROVISION_PROVISIONING_FILE, defaultValue = "${project.basedir}/galleon/provisioning.xml")
    private File provisioningFile = new File("${project.basedir}/galleon/provisioning.xml");

    /**
     * A list of feature-pack configurations to install, can be combined with
     * layers.
     */
    @Parameter(required = false, alias= "feature-packs")
    List<FeaturePack> featurePacks = Collections.emptyList();

    /**
     * Arbitrary Galleon options used when provisioning the server. In case you
     * are building a large amount of server in the same maven session, it
     * is strongly advised to set 'jboss-fork-embedded' option to 'true' in
     * order to fork Galleon provisioning and CLI scripts execution in dedicated
     * processes. For example:
     * <br/>
     * &lt;plugin-options&gt;<br/>
     * &lt;jboss-fork-embedded&gt;true&lt;/jboss-fork-embedded&gt;<br/>
     * &lt;/plugin-options&gt;
     */
    @Parameter(required = false, alias = "galleon-options")
    Map<String, String> galleonOptions = Collections.emptyMap();

    @Parameter(required = false, alias = "packaging-scripts")
    List<PackagingScript> packagingScripts = Collections.emptyList();

    /**
     * A list of directories to copy content to the provisioned server. If a
     * directory is not absolute, it has to be relative to the project base
     * directory.
     */
    @Parameter(alias = "extra-server-content-dirs")
    List<String> extraServerContentDirs = Collections.emptyList();

    boolean logTime = false;
    boolean recordState = false;
    private String provisionDir = "server";

    public boolean isLogTime() {
        return logTime;
    }

    public boolean isRecordState() {
        return recordState;
    }

    public List<FeaturePack> getFeaturePacks() {
        return featurePacks;
    }

    public Map<String, String> getGalleonOptions() {
        return galleonOptions;
    }

    public List<PackagingScript> getPackagingScripts() {
        return packagingScripts;
    }

    public List<String> getExtraServerContentDirs() {
        return extraServerContentDirs;
    }

    public File getProvisioningFile() {
        return provisioningFile;
    }

    public String getProvisionDir() {
        return provisionDir;
    }
}
