package org.gembaboo.maven.jasper;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.maven.shared.filtering.PropertyUtils;
import org.apache.maven.shared.utils.ReaderFactory;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;


/**
 * This mojo is based on the org.apache.maven.plugin.resources.ResourcesMojo, which runs in the default lifecycle
 */
@Mojo(name = "resources",  defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class JasperCypherFilterMojo extends AbstractMojo {

    public static final String ENCRYPTION_PREFIX = "ENC&lt;";
    public static final String ENCRYPTION_SUFFIX = "&gt;";
    public static final String KEY_BYTES = "0x1b 0xd4 0xa6 0x10 0x44 0x42 0x6f 0xb5 0x15 0xda 0xd3 0xf2 0x1f 0x18 0xaa 0x57";
    public static final String KEY_ALGORYTHM = "AES";
    public static final String CYPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private Cipherer cipherer = new Cipherer();

    private Properties additionalProperties = new Properties();

    @Parameter(property = "passwordKeys")
    protected List<String> passwordKeys;

    @Component(role = org.apache.maven.shared.filtering.MavenResourcesFiltering.class, hint = "default")
    protected MavenResourcesFiltering mavenResourcesFiltering;

    @Parameter(required = true)
    protected List<Resource> resources;

    @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    protected File outputDirectory;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The list of additional filter properties files to be used along with System and project
     * properties, which would be used for the filtering.
     * <br/>
     * See also: {@link org.apache.maven.plugin.resources.ResourcesMojo#filters}.
     */
    @Parameter(defaultValue = "${project.build.filters}", readonly = true)
    protected List buildFilters;

    /**
     * The list of extra filter properties files to be used along with System properties,
     * project properties, and filter properties files specified in the POM build/filters section,
     * which should be used for the filtering during the current mojo execution.
     * <br/>
     * Normally, these will be configured from a plugin's execution section, to provide a different
     * set of filters for a particular execution. For instance, starting in Maven 2.2.0, you have the
     * option of configuring executions with the id's <code>default-resources</code> and
     * <code>default-testResources</code> to supply different configurations for the two
     * different types of resources. By supplying <code>extraFilters</code> configurations, you
     * can separate which filters are used for which type of resource.
     */
    @Parameter
    protected List filters;

    /**
     * If false, don't use the filters specified in the build/filters section of the POM when
     * processing resources in this mojo execution.
     * <br/>
     * See also: {@link org.apache.maven.plugin.resources.ResourcesMojo#buildFilters} and {@link org.apache.maven.plugin.resources.ResourcesMojo#filters}
     */
    @Parameter(defaultValue = "true")
    protected boolean useBuildFilters;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;


    /**
     * Expression preceded with the String won't be interpolated
     * \${foo} will be replaced with ${foo}
     */
    @Parameter(defaultValue = "${maven.resources.escapeString}")
    protected String escapeString;

    /**
     * Overwrite existing files even if the destination files are newer.
     */
    @Parameter(property = "maven.resources.overwrite", defaultValue = "false")
    protected boolean overwrite;

    /**
     * Copy any empty directories included in the Ressources.
     */
    @Parameter(property = "maven.resources.includeEmptyDirs", defaultValue = "false")
    protected boolean includeEmptyDirs;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     */
    @Parameter(property = "maven.resources.escapeWindowsPaths", defaultValue = "true")
    protected boolean escapeWindowsPaths;


    public JasperCypherFilterMojo() {
        super();
        cipherer.setKeyBytes(KEY_BYTES);
        cipherer.setKeyAlgorithm(KEY_ALGORYTHM);
        cipherer.setCipherTransformation(CYPHER_TRANSFORMATION);
        cipherer.setLog(getLog());
        cipherer.init();
    }

    public void execute() throws MojoExecutionException {
        try {

            makeResourcesToFiltered();
            validateEncoding();
            List<String> filters = getCombinedFiltersList();
            encryptPasswords(filters);
            MavenResourcesExecution mavenResourcesExecution = createMavenResourcesExecution(filters);

            mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        } catch (MavenFilteringException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void validateEncoding() {
        if (StringUtils.isEmpty(encoding) && isFilteringEnabled(resources)) {
            getLog().warn(
                    "File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING
                            + ", i.e. build is platform dependent!");
        }
    }

    private MavenResourcesExecution createMavenResourcesExecution(List<String> filters) {
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(resources,
                outputDirectory,
                project, encoding, filters,
                Collections.EMPTY_LIST,
                session);
        mavenResourcesExecution.setResources(resources);
        mavenResourcesExecution.setAdditionalProperties(additionalProperties);
        mavenResourcesExecution.setEscapeWindowsPaths(escapeWindowsPaths);
        mavenResourcesExecution.setInjectProjectBuildFilters(false);
        mavenResourcesExecution.setEscapeString(escapeString);
        mavenResourcesExecution.setOverwrite(overwrite);
        mavenResourcesExecution.setIncludeEmptyDirs(includeEmptyDirs);
        return mavenResourcesExecution;
    }

    private void makeResourcesToFiltered() {
        for (Resource resource : resources) {
            resource.setFiltering(true);
        }
    }

    private void encryptPasswords(List<String> filters) throws IOException {
        for (String filterFile : filters) {
            File propFile = FileUtils.resolveFile(project.getBasedir(), filterFile);
            Properties properties = PropertyUtils.loadPropertyFile(propFile, null);
            for (Object key : properties.keySet()) {
                if (passwordKeys.contains(key)) {
                    String password = (String) properties.get(key);
                    String encryptedPassword = encrypt(password);
                    properties.put(key, encryptedPassword);
                    getLog().info("Value of " + key + " encrypted to " + encryptedPassword);
                }
            }
            additionalProperties.putAll(properties);
        }
    }

    private String encrypt(String password) {
        return ENCRYPTION_PREFIX + cipherer.encode(password) + ENCRYPTION_SUFFIX;
    }


    protected List getCombinedFiltersList() {
        if (filters == null || filters.isEmpty()) {
            return useBuildFilters ? buildFilters : null;
        } else {
            List result = new ArrayList();

            if (useBuildFilters && buildFilters != null && !buildFilters.isEmpty()) {
                result.addAll(buildFilters);
            }

            result.addAll(filters);

            return result;
        }
    }

    private boolean isFilteringEnabled(List<Resource> resources) {
        if (resources != null) {

            for (Resource resource : resources) {
                if (resource.isFiltering()) {
                    return true;
                }
            }
        }
        return false;
    }
}
