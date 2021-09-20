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

import static org.wildfly.plugin.core.Constants.CLI_ECHO_COMMAND_ARG;
import static org.wildfly.plugin.core.Constants.CLI_RESOLVE_PARAMETERS_VALUES;
import static org.wildfly.plugin.core.Constants.DOMAIN;
import static org.wildfly.plugin.core.Constants.DOMAIN_XML;
import static org.wildfly.plugin.core.Constants.STANDALONE;
import static org.wildfly.plugin.core.Constants.STANDALONE_XML;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.plugin.cli.CommandConfiguration;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.deployment.PackageType;

/**
 * Provision a server, copy extra content and deploy primary artifact if it
 * exists
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends AbstractProvisionServerMojo {

    /**
     * The server groups the content should be deployed to.
     */
    @Parameter(alias = "server-groups", property = PropertyNames.SERVER_GROUPS)
    private List<String> serverGroups;

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of
     * <code>${project.build.finalName}.${project.packaging}</code>. The default
     * value is not injected as it normally would be due to packaging types like
     * {@code ejb} that result in a file with a {@code .jar} extension rather
     * than an {@code .ejb} extension.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    private String filename;

    /**
     * The name of the server configuration to use when deploying the
     * deployment. Defaults to 'standalone.xml' if no server-groups have been
     * provided otherwise 'domain.xml'.
     */
    @Parameter(property = PropertyNames.SERVER_CONFIG, alias = "server-config")
    private String serverConfig;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * In some cases users may wish to have two deployments with the same
     * {@code runtime-name} (e.g. two versions of {@code example.war}) both
     * available in the management configuration, in which case the deployments
     * would need to have distinct {@code name} values but would have the same
     * {@code runtime-name}.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_RUNTIME_NAME, alias = "runtime-name")
    private String runtimeName;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the
     * spawned CLI process. Note that {@code stderr} will be redirected to
     * {@code stdout} if the value is defined unless the value is {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the
     * current process. You can change the setting to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream
     * should not be consumed</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current
     * processes <em>(use this option if you see odd behavior from maven with
     * the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the
     * {@code stdout} and {@code stderr} will be written there</li>
     * </ul>
     * </div>
     */
    @Parameter(name = "stdout", defaultValue = "System.out", property = PropertyNames.STDOUT)
    private String stdout;

    @Inject
    private CommandExecutor commandExecutor;

    @Override
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException {
        try {
            if (!packaging.getExtraServerContentDirs().isEmpty()) {
                getLog().info("Copying extra content to server");
                copyExtraContent(jbossHome);
            }
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }

        final Path deploymentContent = getDeploymentContent();
        if (Files.exists(deploymentContent)) {
            getLog().info("Deploying " + deploymentContent);
            List<String> deploymentCommands = getDeploymentCommands(deploymentContent);
            final CommandConfiguration cmdConfigDeployment = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                    .addCommands(deploymentCommands)
                    .setJBossHome(jbossHome)
                    .addCLIArguments(CLI_ECHO_COMMAND_ARG)
                    .setFork(true)
                    .setStdout(stdout)
                    .setOffline(true);
            commandExecutor.execute(cmdConfigDeployment);
        }
        // CLI execution
         try {
             if (!packaging.getPackagingScripts().isEmpty()) {
                 getLog().info("Executing packaging scripts using CLI");
                 for (PackagingScript packagingScript : packaging.getPackagingScripts()) {
                     Path wrappedScript = wrapScript(packagingScript.getFile());
                     try {
                         final CommandConfiguration cmdConfig = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                                 .addScripts(Arrays.asList(wrappedScript.toFile()))
                                 .addCLIArguments(CLI_ECHO_COMMAND_ARG)
                                 .setJBossHome(jbossHome)
                                 .setFork(true)
                                 .setStdout(stdout)
                                 .addJvmOptions(packagingScript.getJavaOpts())
                                 .setOffline(true);
                         if (packagingScript.getPropertiesFile() != null) {
                             cmdConfig.addPropertiesFiles(Arrays.asList(resolvePath(project, packagingScript.getPropertiesFile().toPath()).toFile()));
                         }
                         if (packagingScript.isResolveExpressions()) {
                            cmdConfig.addCLIArguments(CLI_RESOLVE_PARAMETERS_VALUES);
                         }
                         commandExecutor.execute(cmdConfig);
                     } finally {
                         Files.delete(wrappedScript);
                     }
                 }
             }

            cleanupServer(jbossHome);
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private List<File> resolveFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return files;
        }
        List<File> resolvedFiles = new ArrayList<>();
        for(File f : files) {
            resolvedFiles.add(resolvePath(project, f.toPath()).toFile());
        }
        return resolvedFiles;
    }

    private List<String> getDeploymentCommands(Path deploymentContent) throws MojoExecutionException {
        List<String> deploymentCommands = new ArrayList<>();
        StringBuilder deploymentBuilder = new StringBuilder();
        deploymentBuilder.append("deploy  ").append(deploymentContent).append(" --name=").
                append(name == null ? deploymentContent.getFileName() : name).append(" --runtime-name=").
                append(runtimeName == null ? deploymentContent.getFileName() : runtimeName);
        if (isDomain()) {
            if (serverGroups == null || serverGroups.isEmpty()) {
                throw new MojoExecutionException("Can't deploy, no server-groups provided.");
            }
            deploymentBuilder.append(" --server-groups=");
            for (int i = 0; i < serverGroups.size(); i++) {
                deploymentBuilder.append(serverGroups.get(i));
                if (i < serverGroups.size() - 1) {
                    deploymentBuilder.append(",");
                }
            }
            deploymentCommands.add(deploymentBuilder.toString());
        } else {
            deploymentCommands.add(deploymentBuilder.toString());
        }

        return wrapOfflineCommands(deploymentCommands);
    }

    private List<String> wrapOfflineCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return commands;
        }
        List<String> offlineCommands = new ArrayList<>();
         if (isDomain()) {
            offlineCommands.add("embed-host-controller --domain-config=" + getServerConfigName(true));
            offlineCommands.addAll(commands);
            offlineCommands.add("stop-embedded-host-controller");
         } else {
            offlineCommands.add("embed-server --server-config=" + getServerConfigName(false));
            offlineCommands.addAll(commands);
            offlineCommands.add("stop-embedded-server");
         }
         return offlineCommands;
    }

    private String getServerConfigName(boolean isDomain) {
        return isDomain ? (serverConfig == null ? DOMAIN_XML : serverConfig) : (serverConfig == null ? STANDALONE_XML : serverConfig);
    }

    private List<File> wrapOfflineScripts(List<File> scripts) throws IOException, MojoExecutionException {
        List<File> wrappedScripts = new ArrayList<>();
        for(File script : scripts) {
            wrappedScripts.add(wrapScript(script).toFile());
        }
        return wrappedScripts;
    }

    private Path wrapScript(File script) throws IOException, MojoExecutionException {
        final Path tempScript = Files.createTempFile("offline-cli-script", ".cli");
        Path resolvedScript = resolvePath(project, script.toPath());
        if (!Files.exists(resolvedScript)) {
            throw new MojoExecutionException("CLI script " + resolvedScript + " doesn't exist");
        }
        List<String> cmds = Files.readAllLines(resolvedScript, StandardCharsets.UTF_8);
        List<String> wrappedCommands = wrapOfflineCommands(cmds);
        Files.write(tempScript, wrappedCommands, StandardCharsets.UTF_8);
        return tempScript;
    }

    public void copyExtraContent(Path target) throws MojoExecutionException, IOException {
        for (String path : packaging.getExtraServerContentDirs()) {
            Path extraContent = Paths.get(path);
            extraContent = resolvePath(project, extraContent);
            if (Files.notExists(extraContent)) {
                throw new MojoExecutionException("Extra content dir " + extraContent + " doesn't exist");
            }
            // Check for the presence of a standalone.xml file
            warnExtraConfig(extraContent);
            IoUtils.copy(extraContent, target);
        }

    }

    private boolean isDomain() {
      if (DOMAIN_XML.equals(serverConfig)) {
          return true;
      }
      if (serverGroups != null && !serverGroups.isEmpty()) {
          return true;
      }
      return false;
    }

    private void warnExtraConfig(Path extraContentDir) {
        boolean isDomain = isDomain();
        String configDir = isDomain ? DOMAIN : STANDALONE;
        Path config = extraContentDir.resolve(configDir).resolve("configurations").resolve(getServerConfigName(isDomain));
        if (Files.exists(config)) {
            getLog().warn("The file " + config + " overrides the Galleon generated configuration, "
                    + "un-expected behavior can occur when starting the server");
        }
    }

    private Path getDeploymentContent() {
        final PackageType packageType = PackageType.resolve(project);
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        return targetDir.toPath().resolve(filename);
    }

    private static void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Path tmp = jbossHome.resolve("standalone").resolve("tmp");
        IoUtils.recursiveDelete(tmp);
        Path log = jbossHome.resolve("standalone").resolve("log");
        IoUtils.recursiveDelete(log);
        Path domainHistory = jbossHome.resolve("domain").resolve("configuration").resolve("domain_xml_history");
        IoUtils.recursiveDelete(domainHistory);
        Path hostHistory = jbossHome.resolve("domain").resolve("configuration").resolve("host_xml_history");
        IoUtils.recursiveDelete(hostHistory);
        Path domainTmp = jbossHome.resolve("domain").resolve("tmp");
        IoUtils.recursiveDelete(domainTmp);
        Path domainLog = jbossHome.resolve("domain").resolve("log");
        IoUtils.recursiveDelete(domainLog);
    }

    private MavenModelControllerClientConfiguration getClientConfiguration() {
        return null;
    }

    private ModelControllerClient createClient() {
        return null;
    }

}
