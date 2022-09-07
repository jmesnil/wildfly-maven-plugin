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

import static java.lang.String.format;
import static java.lang.String.join;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "image", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class ApplicationImageMojo extends PackageServerMojo {

    public static final int DOCKER_CMD_CHECK_TIMEOUT = 3000;
    private static final String DOCKER_BINARY = "docker";

    @Parameter(property = "wildfly.image.build", alias = "build-image", defaultValue = "true")
    private boolean buildApplicationImageNeeded;

    @Parameter(property = "wildfly.image.jdk", alias = "image-jdk", defaultValue = "11")
    private String runtimeJDKVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        if (!buildApplicationImageNeeded) {
            return;
        }

        if (!isDockerBinaryAvailable()) {
            throw new RuntimeException("Unable to build container image with docker. Please check your docker installation");
        }

        try {

            boolean usesJDK17 = "17".equals(runtimeJDKVersion);

            String runtimeImage = "quay.io/wildfly/wildfly-runtime-jdk11:latest";
            if (usesJDK17) {
                runtimeImage = "quay.io/wildfly/wildfly-runtime-jdk17:latest";
            }
            generateDockerfile(runtimeImage);

            getLog().info(format("Building application image for %s using %s.", project.getArtifactId(), DOCKER_BINARY));
            getLog().info(format("Base image is %s", runtimeImage));

            String imageName = project.getArtifactId().toLowerCase();
            String[] dockerArgs = new String[] {"build", "-t", imageName, "."};

            getLog().info(format("Executing the following command to build docker image: '%s %s'", DOCKER_BINARY, join(" ", dockerArgs)));
            boolean buildSuccessful = ExecUtil.exec(Paths.get("target").toFile(), DOCKER_BINARY, dockerArgs);

            if (buildSuccessful) {
                getLog().info(String.format("Successfully built application image %s.", imageName));
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw new MojoExecutionException(e.getLocalizedMessage());
        }

    }

    private void generateDockerfile(String runtimeImage) throws IOException {
        Files.writeString(Paths.get("target", "Dockerfile"),
                "FROM " + runtimeImage + "\n" +
                        "COPY --chown=jboss:root server $JBOSS_HOME\n" +
                        "RUN chmod -R ug+rwX $JBOSS_HOME",
                StandardCharsets.UTF_8);
    }

    private boolean isDockerBinaryAvailable() {
        try {
            if (!ExecUtil.execSilentWithTimeout(Duration.ofMillis(DOCKER_CMD_CHECK_TIMEOUT), DOCKER_BINARY, "-v")) {

                getLog().warn(format("'%s -v' returned an error code. Make sure your %s binary is correct", DOCKER_BINARY, DOCKER_BINARY));
                return false;
            }
        } catch (Exception e) {
            getLog().warn(format("No %s binary found or general error: %s", DOCKER_BINARY, e));
            return false;
        }

        return true;
    }

}
