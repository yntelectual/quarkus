package io.quarkus.kotlin.maven.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.shared.invoker.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import io.quarkus.maven.CreateProjectMojo;
import io.quarkus.maven.it.MojoTestBase;
import io.quarkus.maven.it.verifier.RunningInvoker;
import io.quarkus.maven.utilities.MojoUtils;

public class KotlinCreateMavenProjectIT extends MojoTestBase {

    private Invoker invoker;
    private RunningInvoker running;
    private File testDir;

    @Test
    public void testProjectGenerationFromScratchForKotlin() throws MavenInvocationException, IOException {
        testDir = initEmptyProject("projects/project-generation-kotlin");
        assertThat(testDir).isDirectory();
        invoker = initInvoker(testDir);

        Properties properties = new Properties();
        properties.put("projectGroupId", "org.acme");
        properties.put("projectArtifactId", "acme");
        properties.put("projectVersion", "1.0-SNAPSHOT");
        properties.put("extensions", "kotlin,resteasy-jsonb");
        setup(properties);

        // As the directory is not empty (log) navigate to the artifactID directory
        testDir = new File(testDir, "acme");

        assertThat(new File(testDir, "pom.xml")).isFile();
        assertThat(new File(testDir, "src/main/kotlin")).isDirectory();
        assertThat(new File(testDir, "src/main/resources/application.properties")).isFile();

        String config = Files
                .asCharSource(new File(testDir, "src/main/resources/application.properties"), Charsets.UTF_8)
                .read();
        assertThat(config).contains("key = value");

        assertThat(new File(testDir, "src/main/docker/Dockerfile.native")).isFile();
        assertThat(new File(testDir, "src/main/docker/Dockerfile.jvm")).isFile();

        Model model = loadPom(testDir);
        final DependencyManagement dependencyManagement = model.getDependencyManagement();
        final List<Dependency> dependencies = dependencyManagement.getDependencies();
        assertThat(dependencies.stream().anyMatch(d -> d.getArtifactId().equalsIgnoreCase(MojoUtils.getBomArtifactId())
                && d.getVersion().equalsIgnoreCase("${quarkus.version}")
                && d.getScope().equalsIgnoreCase("import")
                && d.getType().equalsIgnoreCase("pom"))).isTrue();

        assertThat(
                model.getDependencies().stream().anyMatch(d -> d.getArtifactId().equalsIgnoreCase("quarkus-resteasy")
                        && d.getVersion() == null)).isTrue();
        assertThat(
                model.getDependencies().stream().anyMatch(d -> d.getArtifactId().equalsIgnoreCase("quarkus-kotlin")
                        && d.getVersion() == null)).isTrue();

        assertThat(model.getProfiles()).hasSize(1);
        assertThat(model.getProfiles().get(0).getId()).isEqualTo("native");
    }

    @AfterEach
    public void cleanup() {
        if (running != null) {
            running.stop();
        }
    }

    private InvocationResult setup(Properties params)
            throws MavenInvocationException, FileNotFoundException, UnsupportedEncodingException {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setBatchMode(true);
        request.setGoals(Collections.singletonList(
                CreateProjectMojo.PLUGIN_KEY + ":" + MojoUtils.getPluginVersion() + ":create"));
        request.setProperties(params);
        getEnv().forEach(request::addShellEnvironment);
        File log = new File(testDir, "build-create-" + testDir.getName() + ".log");
        PrintStreamLogger logger = new PrintStreamLogger(new PrintStream(new FileOutputStream(log), false, "UTF-8"),
                InvokerLogger.DEBUG);
        invoker.setLogger(logger);
        return invoker.execute(request);
    }

}
