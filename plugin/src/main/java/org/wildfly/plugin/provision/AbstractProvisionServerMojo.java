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

import static org.wildfly.plugin.core.Constants.PLUGIN_PROVISIONING_FILE;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.GalleonUtils;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;


/**
 * Provision a server
 *
 * @author jfdenise
 */
abstract class AbstractProvisionServerMojo extends AbstractMojo {

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
     * Whether to use offline mode when the plugin resolves an artifact. In
     * offline mode the plugin will only use the local Maven repository for an
     * artifact resolution.
     */
    @Parameter(defaultValue = "false")
    boolean offline;

    /**
     * Set to {@code true} if you want the goal to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    boolean skip;

    /**
     * A list of feature-pack configurations to install, can be combined with
     * layers.
     */
    @Parameter(required = false)
    PackagingConfiguration packaging = new PackagingConfiguration();

    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/", property = PropertyNames.DEPLOYMENT_TARGET_DIR)
    protected File targetDir;

    private Path wildflyDir;

    private MavenRepoManager artifactResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        MavenRepositoriesEnricher.enrich(session, project, repositories);
        artifactResolver = offline ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);

        wildflyDir = targetDir.toPath().resolve(packaging.getProvisionDir());
        IoUtils.recursiveDelete(wildflyDir);

        try {
            provisionServer(wildflyDir);
        } catch (ProvisioningException | IOException | XMLStreamException ex) {
            throw new MojoExecutionException("Provisioning failed", ex);
        }
        serverProvisioned(wildflyDir);
    }

    protected abstract void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException;

    private void provisionServer(Path home) throws ProvisioningException,
            MojoExecutionException, IOException, XMLStreamException {
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(packaging.isLogTime())
                .setRecordState(packaging.isRecordState())
                .build()) {
            getLog().info("Provisioning server in " + home);
            ProvisioningConfig config = null;
            Path resolvedProvisioningFile = resolvePath(project, packaging.getProvisioningFile().toPath());
            boolean provisioningFileExists = Files.exists(resolvedProvisioningFile);
            if (packaging.getFeaturePacks().isEmpty()) {
                if (provisioningFileExists) {
                    getLog().info("Provisioning server using " + resolvedProvisioningFile + " file.");
                    config = GalleonUtils.buildConfig(resolvedProvisioningFile);
                } else {
                    throw new MojoExecutionException("No feature-pack has been configured, can't provision a server.");
                }
            } else {
                if (provisioningFileExists) {
                    getLog().warn("Galleon provisioning file " + packaging.getProvisioningFile() + " is ignored, plugin configuration is used.");
                }
                config = GalleonUtils.buildConfig(pm, packaging.getFeaturePacks(), Arrays.asList(packaging), packaging.getGalleonOptions());
            }
            pm.provision(config);
            if (!Files.exists(home)) {
                getLog().error("Invalid galleon provisioning, no server provisioned in " + home);
                throw new MojoExecutionException("Invalid plugin configuration, no server provisioned.");
            }
            if (!packaging.isRecordState()) {
                Path file = home.resolve(PLUGIN_PROVISIONING_FILE);
                try (FileWriter writer = new FileWriter(file.toFile())) {
                    ProvisioningXmlWriter.getInstance().write(config, writer);
                }
            }
        }
    }

    static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }
}
