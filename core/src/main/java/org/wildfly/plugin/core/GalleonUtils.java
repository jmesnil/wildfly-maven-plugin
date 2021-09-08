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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.Configuration;
import org.jboss.galleon.maven.plugin.util.ConfigurationId;
import org.jboss.galleon.maven.plugin.util.FeaturePack;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import static org.wildfly.plugin.core.Constants.STANDALONE;
import static org.wildfly.plugin.core.Constants.STANDALONE_XML;

/**
 * @author jdenise
 */
public class GalleonUtils {

    /**
     * Galleon provisioning of a default server.
     *
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

    /**
     * Build a Galleon provisioning configuration based on a provisioning.xml file.
     * @param provisioningFile
     * @return The provisioning config.
     * @throws ProvisioningException
     */
    public static ProvisioningConfig buildConfig(Path provisioningFile) throws ProvisioningException {
        return ProvisioningXmlParser.parse(provisioningFile);
    }

    /**
     * Build a Galleon provisioning configuration.
     * @param pm The Galleon provisioning runtime.
     * @param featurePacks The list of feature-packs.
     * @param configurations List of custom configurations.
     * @param pluginOptions Galleon plugin options.
     * @return The provisioning config.
     * @throws ProvisioningException
     */
    public static ProvisioningConfig buildConfig(ProvisioningManager pm,
            List<FeaturePack> featurePacks,
            List<Configuration> configurations,
            Map<String, String> pluginOptions) throws ProvisioningException, IllegalArgumentException {
        final ProvisioningConfig.Builder state = ProvisioningConfig.builder();
        for (FeaturePack fp : featurePacks) {

            if (fp.getLocation() == null && (fp.getGroupId() == null || fp.getArtifactId() == null)
                    && fp.getNormalizedPath() == null) {
                throw new IllegalArgumentException("Feature-pack location, Maven GAV or feature pack path is missing");
            }

            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = pm.getLayoutFactory().addLocal(fp.getNormalizedPath(), false);
            } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                String coords = getMavenCoords(fp);
                fpl = FeaturePackLocation.fromString(coords);
            } else {
                fpl = FeaturePackLocation.fromString(fp.getLocation());
            }

            final FeaturePackConfig.Builder fpConfig = fp.isTransitive() ? FeaturePackConfig.transitiveBuilder(fpl)
                    : FeaturePackConfig.builder(fpl);
            if (fp.isInheritConfigs() != null) {
                fpConfig.setInheritConfigs(fp.isInheritConfigs());
            }
            if (fp.isInheritPackages() != null) {
                fpConfig.setInheritPackages(fp.isInheritPackages());
            }

            if (!fp.getExcludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getExcludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.excludeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.excludeDefaultConfig(configId.getId());
                    }
                }
            }
            if (!fp.getIncludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getIncludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.includeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.includeDefaultConfig(configId.getId());
                    }
                }
            }

            if (!fp.getIncludedPackages().isEmpty()) {
                for (String includedPackage : fp.getIncludedPackages()) {
                    fpConfig.includePackage(includedPackage);
                }
            }
            if (!fp.getExcludedPackages().isEmpty()) {
                for (String excludedPackage : fp.getExcludedPackages()) {
                    fpConfig.excludePackage(excludedPackage);
                }
            }

            state.addFeaturePackDep(fpConfig.build());
        }

        boolean hasLayers = false;
        for (Configuration config : configurations) {
            String model = config.getModel() == null ? STANDALONE : config.getModel();
            String name = config.getName() == null ? STANDALONE_XML : config.getName();
            ConfigModel.Builder configBuilder = ConfigModel.
                    builder(model, name);
            for (String layer : config.getLayers()) {
                hasLayers = true;
                configBuilder.includeLayer(layer);
            }
            if (config.getExcludedLayers() != null) {
                for (String layer : config.getExcludedLayers()) {
                    configBuilder.excludeLayer(layer);
                }
            }
            state.addConfig(configBuilder.build());
        }

        if (hasLayers) {
            if (pluginOptions.isEmpty()) {
                pluginOptions = Collections.
                        singletonMap(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            } else if (!pluginOptions.containsKey(Constants.OPTIONAL_PACKAGES)) {
                pluginOptions.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            }
        }
        state.addOptions(pluginOptions);

        return state.build();
    }

    private static String getMavenCoords(FeaturePack fp) {
        StringBuilder builder = new StringBuilder();
        builder.append(fp.getGroupId()).append(":").append(fp.getArtifactId());
        String type = fp.getExtension() == null ? fp.getType() : fp.getExtension();
        if (fp.getClassifier() != null || type != null) {
            builder.append(":").append(fp.getClassifier() == null ? "" : fp.getClassifier()).append(":").append(type == null ? "" : type);
        }
        if (fp.getVersion() != null) {
            builder.append(":").append(fp.getVersion());
        }
        return builder.toString();
    }
}
