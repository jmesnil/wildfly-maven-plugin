/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.plugin.core;

import java.nio.file.Path;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

/**
 * @author jdenise
 */
public class GalleonUtils {

    /**
     * Galleon provisioning of a default server.
     * @param jbossHome Server installation directory
     * @param location Galleon FeaturePackLocation
     * @param artifactResolver Artifact resolver used by Galleon
     * @throws ProvisioningException
     */
    public static void provision(Path jbossHome, String location, MavenRepoManager artifactResolver) throws ProvisioningException {
        ProvisioningConfig.Builder state = ProvisioningConfig.builder();
        FeaturePackLocation fpl = FeaturePackLocation.fromString(location);
        FeaturePackConfig.Builder fpConfig = FeaturePackConfig.builder(fpl);
        fpConfig.setInheritConfigs(true);
        fpConfig.setInheritPackages(true);
        state.addFeaturePackDep(fpConfig.build());
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(jbossHome)
                .build()) {
            pm.provision(state.build());
        }
    }
}
