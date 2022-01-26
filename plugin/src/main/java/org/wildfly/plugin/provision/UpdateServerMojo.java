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
package org.wildfly.plugin.provision;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;

/**
 * Update a server. POC.
 *
 * @author jfdenise
 */
@Mojo(name = "update", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class UpdateServerMojo extends AbstractMojo {

    @Component
    RepositorySystem repoSystem;

    @Component
    MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    /**
     * The directory name inside the buildDir where to update the server.
     */
    @Parameter(alias = "provisioning-dir", property = PropertyNames.WILDFLY_PROVISIONING_DIR, defaultValue = Utils.WILDFLY_DEFAULT_DIR)
    private String provisioningDir;

    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/", property = PropertyNames.DEPLOYMENT_TARGET_DIR)
    protected File targetDir;

    @Parameter(alias = "channels", required = true)
    ChannelsConfig channels;
    @Parameter(required = false, alias = "galleon-options")
    Map<String, String> galleonOptions = Collections.emptyMap();

    private Path wildflyDir;

    protected ChannelMavenArtifactRepositoryManager channelsArtifactResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        MavenRepositoriesEnricher.enrich(session, project, repositories);
        try {
            channelsArtifactResolver = new ChannelMavenArtifactRepositoryManager(project, channels, targetDir.toPath(), repoSystem, repoSession);
        } catch (MalformedURLException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
        wildflyDir = targetDir.toPath().resolve(provisioningDir);
        try {
            try {
                updateServer(wildflyDir);
                channelsArtifactResolver.done(wildflyDir);
            } catch (ProvisioningException | IOException | XMLStreamException ex) {
                throw new MojoExecutionException("Provisioning failed", ex);
            }
        } finally {
            // Although cli and embedded are run in their own classloader,
            // the module.path system property has been set and needs to be cleared for
            // in same JVM next execution.
            System.clearProperty("module.path");
        }
    }

    private void updateServer(Path home) throws ProvisioningException,
            MojoExecutionException, IOException, XMLStreamException {
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(channelsArtifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(true)
                .setRecordState(true)
                .build()) {
            // We should check that we have changes between the local channels.yaml file and the configured channels.
            // If some changes are detected, re-provisioning must be operated. This updates the server with new versions.
            pm.provision(pm.getProvisioningConfig(), galleonOptions);
        }
    }
}
