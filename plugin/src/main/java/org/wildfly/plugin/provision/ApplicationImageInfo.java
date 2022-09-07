package org.wildfly.plugin.provision;

public class ApplicationImageInfo {

    /**
     * Whether the application image should be built (default is {@code true}
     */
    protected boolean build = true;

    /**
     * Whether the application image should be pushed (default is {@code false}
     */
    protected boolean push;

    /**
     * Determine which WildFly Runtime image to use so that the application runs with the specified JDK.
     * The default is "11". Accepted values are "11", "17".
     */
    private String jdk = "11";

    /**
     * The group part of the name of the application image.
     */
    private String group;

    /**
     * The name part of the application image. If not set, the value of the artifactId (in lower case) is used.
     */
    private String name;

    /**
     * The tag part of the application image (default is @{code latest}.
     */
    private String tag = "latest";

    /**
     * The container registry.
     *
     * If set, the registry is added to the application name.
     * If the image is pushed and the registry is not set, it defaults to "docker.io" to login to the registry
     */
    protected String registry;

    /**
     * The user name to login to the container registry.
     */
    protected String user;

    /**
     * The user password to login to the container registry.
     */
    protected String password;

    String getApplicationImageName(String artifactId) {
        String registry = this.registry != null ? this.registry + "/"  : "";
        String group = this.group != null ? this.group + "/" : "";
        String imageName = this.name != null ? this.name : artifactId.toLowerCase();
        String tag = this.tag;

        return registry + group + imageName + ":" + tag;
    }

    String getWildFlyRuntimeImage() {
        switch (jdk) {
            case "17":
                return "quay.io/wildfly/wildfly-runtime-jdk17:latest";
            default:
                return "quay.io/wildfly/wildfly-runtime-jdk11:latest";
        }
    }
}
