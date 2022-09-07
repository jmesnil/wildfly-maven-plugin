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
import java.util.Arrays;

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

    /**
     * The configuraiton of the application image.
     */
    @Parameter(property = "wildfly.image.build", alias = "image", defaultValue = "true")
    private ApplicationImageInfo imageInfo;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        if (!imageInfo.build) {
            return;
        }

        if (!isDockerBinaryAvailable()) {
            throw new RuntimeException("Unable to build container image with docker. Please check your docker installation");
        }

        String image = imageInfo.getApplicationImageName(project.getArtifactId());
        String runtimeImage = imageInfo.getWildFlyRuntimeImage();

        try {
            boolean buildSuccess = buildApplicationImage(image, runtimeImage);
            if (!buildSuccess) {
                throw new MojoExecutionException(String.format("Unable to build image %s", image));
            }
            getLog().info(String.format("Successfully built application image %s", image));

            if (imageInfo.push) {
                logToRegistry();

                boolean pushSuccess = pushApplicationImage(image);
                if (!pushSuccess) {
                    throw new MojoExecutionException(String.format("Unable to push image %s", image));
                }
                getLog().info(String.format("Successfully pushed application image %s", image));
            }
        } catch (IOException e) {
            MojoExecutionException ex = new MojoExecutionException(e.getLocalizedMessage());
            ex.initCause(e);
            throw ex;
        }
    }

    private void logToRegistry() throws MojoExecutionException {
        String registry = imageInfo.registry;
        if (registry == null) {
            getLog().info("Registry was not set. Using docker.io");
        }
        if (imageInfo.user != null && imageInfo.password != null) {
            String[] dockerArgs = new String[] {
                    "login", registry,
                    "-u", imageInfo.user,
                    "-p", imageInfo.password
            };
            boolean loginSuccessful = ExecUtil.exec(getLog(), DOCKER_BINARY, dockerArgs);
            if (!loginSuccessful) {
                throw new MojoExecutionException(String.format("Could not log to docker with the command %s %s %s",
                        DOCKER_BINARY,
                        String.join(" ", Arrays.copyOf(dockerArgs, dockerArgs.length -1)),
                        "*******"));
            }
        }
    }

    private boolean buildApplicationImage(String image, String runtimeImage) throws IOException {
        getLog().info(format("Building application image %s using %s.", image, DOCKER_BINARY));
        getLog().info(format("Base image is %s", runtimeImage));

        generateDockerfile(runtimeImage);

        String[] dockerArgs = new String[] {"build", "-t", image, "."};

        getLog().info(format("Executing the following command to build docker image: '%s %s'", DOCKER_BINARY, join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get("target").toFile(), DOCKER_BINARY, dockerArgs);

    }

    private boolean pushApplicationImage(String image) {
        getLog().info(format("Pushing application image %s using %s.", image, DOCKER_BINARY));

        String[] dockerArgs = new String[] {"push", image};

        getLog().info(format("Executing the following command to build docker image: '%s %s'", DOCKER_BINARY, join(" ", dockerArgs)));
        return ExecUtil.exec(getLog(), Paths.get("target").toFile(), DOCKER_BINARY, dockerArgs);
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
