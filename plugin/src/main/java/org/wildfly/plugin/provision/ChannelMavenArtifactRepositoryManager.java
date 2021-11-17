/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
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

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager {

    private static class WildFlyMavenVersionsResolver implements MavenVersionsResolver {

        private final RepositorySystem system;
        private final DefaultRepositorySystemSession session;

        WildFlyMavenVersionsResolver(Path localCache,
                                     RepositorySystem system,
                                     RepositorySystemSession contextSession) {
            this.system = system;
            session = newRepositorySystemSession(localCache, contextSession, system);
        }

        @Override
        public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
            requireNonNull(groupId);
            requireNonNull(artifactId);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, "[0,)");
            VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
            versionRangeRequest.setArtifact(artifact);

            try {
                VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
                Set<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toSet());
                return versions;
            } catch (VersionRangeResolutionException e) {
                return emptySet();
            }
        }

        @Override
        public File resolveLatestVersionFromMavenMetadata(String groupId, String artifactId, String extension, String classifier) throws UnresolvedMavenArtifactException {
            requireNonNull(groupId);
            requireNonNull(artifactId);
            System.out.println(String.format("Resolving the latest version of %s:%s from Maven Metadata",
                    groupId, artifactId));

            requireNonNull(groupId);
            requireNonNull(artifactId);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, "[0,)");
            VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
            versionRangeRequest.setArtifact(artifact);

            try {
                VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
                Set<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toSet());
                String latestVersion =  versionRangeResult.getHighestVersion().toString();
                System.out.println(String.format("Latest version of %s:%s is %s",
                        groupId, artifactId, latestVersion));
                return resolveArtifact(groupId, artifactId, extension, classifier, latestVersion);
            } catch (VersionRangeResolutionException e) {
                UnresolvedMavenArtifactException umae = new UnresolvedMavenArtifactException();
                umae.initCause(e);
                throw umae;
            }
        }

        @Override
        public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);

            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            ArtifactResult result;
            try {
                result = system.resolveArtifact(session, request);
            } catch (ArtifactResolutionException ex) {
                throw new UnresolvedMavenArtifactException(ex.getLocalizedMessage(), ex);
            }
            return result.getArtifact().getFile();
        }

        public static DefaultRepositorySystemSession newRepositorySystemSession(Path localCache,
                                                                                RepositorySystemSession contextSession,
                                                                                RepositorySystem system) {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

            if (localCache == null) {
                session.setLocalRepositoryManager(contextSession.getLocalRepositoryManager());
            } else {
                LocalRepository localRepo = new LocalRepository(localCache.toString());
                session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
            }
            return session;
        }

        @Override
        public void close() {
        }

    }

    private static class WildFlyMavenVersionResolverFactory implements MavenVersionsResolver.Factory {

        private final RepositorySystemSession contextSession;
        private final RepositorySystem system;
        private final Path buildDir;
        private final Path localCache;
        final List<WildFlyMavenVersionsResolver> resolvers = new ArrayList<>();

        WildFlyMavenVersionResolverFactory(Path localCache, Path buildDir, RepositorySystem system, RepositorySystemSession contextSession) {
            this.localCache = localCache;
            this.buildDir = buildDir;
            this.system = system;
            this.contextSession = contextSession;
        }

        @Override
        public WildFlyMavenVersionsResolver create() {

            WildFlyMavenVersionsResolver res = new WildFlyMavenVersionsResolver(localCache, system, contextSession);
            resolvers.add(res);
            return res;
        }
    }

    private final ChannelSession channelSession;
    private final WildFlyMavenVersionResolverFactory factory;
    private final boolean disableLatest;
    private Log log;

    public ChannelMavenArtifactRepositoryManager(MavenProject project, ChannelsConfig config,
                                                 Path buildDir, RepositorySystem system, RepositorySystemSession contextSession, Log log) throws MalformedURLException {
        disableLatest = config.isDisableLatestResolution();
        this.log = log;
        List<Channel> channels = new ArrayList<>();
        factory = new WildFlyMavenVersionResolverFactory(config.getLocalCache()== null ? null :
                resolvePath(project, config.getLocalCache().toPath()), buildDir, system, contextSession);

        WildFlyMavenVersionsResolver resolver = factory.create();
        for (String channel : config.getChannels()) {
            log.debug(">> channel = " + channel);
            String[] s = channel.split(":");
            System.out.println("s = " + Arrays.asList(s));
            String groupId = s[0];
            String artifactId = s[1];
            try {
                File file = resolver.resolveLatestVersionFromMavenMetadata(groupId, artifactId, "yaml", null);
                log.info(">>>file = " + file);
                Channel channel1 = ChannelMapper.from(file.toURI().toURL());
                log.info(">> channel = " + channel1);
                channels.add(channel1);
            } catch (UnresolvedMavenArtifactException e) {
                e.printStackTrace();
            }
        }
        resolver.close();
        channelSession = new ChannelSession(channels, factory);
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
           // log.debug("artifact = " + artifact);
            resolveChannel(artifact);
        } catch (UnresolvedMavenArtifactException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }

    }

    private void resolveChannel(MavenArtifact artifact) throws MavenUniverseException, UnresolvedMavenArtifactException {
        log.info("artifact = " + artifact);

        org.wildfly.channel.MavenArtifact result;
        if (disableLatest) {
            result = channelSession.resolveExactMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
        } else {
            result = channelSession.resolveLatestMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
            log.info("result.getVersion() = " + result.getVersion());
            artifact.setVersion(result.getVersion());
        }
        log.info("result = " + result);
        artifact.setPath(result.getFile().toPath());
    }

    public void done(Path home) throws MavenUniverseException, IOException {
        Channel channel = channelSession.getRecordedChannel();
        Files.write(home.resolve(".channel.yaml"), ChannelMapper.toYaml(channel).getBytes());
    }

    private static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public boolean isResolved(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public boolean isLatestVersionResolved(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
                                     Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void install(MavenArtifact artifact, Path path) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }
}
