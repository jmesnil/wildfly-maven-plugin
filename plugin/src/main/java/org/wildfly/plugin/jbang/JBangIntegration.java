package org.wildfly.plugin.jbang;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JBangIntegration {

    public static Map<String, Object> postBuild(Path appClasses, Path pomFile,
            List<Map.Entry<String, String>> repositories,
            List<Map.Entry<String, Path>> originalDeps,
            List<String> comments, boolean nativeImage) throws IOException {

        System.out.println("JBangIntegration.postBuild()");
        System.out.println(appClasses);
        System.out.println(pomFile);
        System.out.println(repositories);
        System.out.println(originalDeps);
        System.out.println(comments);
        System.out.println(nativeImage);

        // TODO Build a bootable jar from the appclasses
        // TODO run the bootable jar main class

        return new HashMap<>();
    }

}