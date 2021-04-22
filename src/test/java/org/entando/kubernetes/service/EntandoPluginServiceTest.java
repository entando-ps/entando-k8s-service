package org.entando.kubernetes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entando.kubernetes.util.EntandoPluginTestHelper.TEST_PLUGIN_NAME;
import static org.entando.kubernetes.util.EntandoPluginTestHelper.TEST_PLUGIN_NAMESPACE;
import static org.entando.kubernetes.util.EntandoPluginTestHelper.getTestEntandoPlugin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.exception.NotObservedNamespaceException;
import org.entando.kubernetes.model.namespace.ObservedNamespaces;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.security.oauth2.KubernetesUtilsTest;
import org.entando.kubernetes.util.EntandoPluginTestHelper;
import org.junit.Rule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("component"), @Tag("in-process")})
@EnableRuleMigrationSupport
class EntandoPluginServiceTest {

    @Rule
    public KubernetesServer server = new KubernetesServer(false, true);

    private EntandoPluginService entandoPluginService;
    private KubernetesClient client;

    @BeforeEach
    void setUp() {
        client = server.getClient();
    }

    private void initializeService(String... namespaces) {
        KubernetesUtils kubernetesUtils = new KubernetesUtils(token -> server.getClient());
        kubernetesUtils.decode(KubernetesUtilsTest.NON_K8S_TOKEN);
        ObservedNamespaces ons = new ObservedNamespaces(kubernetesUtils, Arrays.asList(namespaces), OperatorDeploymentType.HELM);
        entandoPluginService = new EntandoPluginService(kubernetesUtils, ons);
    }

    @Test
    void shouldReturnAnEmptyListIfNoPluginAvailable() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        assertTrue(entandoPluginService.getAll().isEmpty());
    }

    @Test
    void shouldReturnOnePlugin() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        assertEquals(1, entandoPluginService.getAll().size());
    }

    @Test
    void shouldReturnPluginInClientNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        assertEquals(1, entandoPluginService.getAllInNamespace(TEST_PLUGIN_NAMESPACE).size());
    }

    @Test
    void shouldReturnOnlyPluginsFromObservedNamespaces() {
        initializeService(TEST_PLUGIN_NAMESPACE, "invalid-namespace");
        EntandoPluginService epsMock = mock(EntandoPluginService.class);
        when(epsMock.collectFromNamespaces(anyList()))
                .thenCallRealMethod();
        when(epsMock.getAllInNamespace(eq(TEST_PLUGIN_NAMESPACE)))
                .thenReturn(Collections.singletonList(getTestEntandoPlugin()));
        List<EntandoPlugin> plugins = epsMock.collectFromNamespaces(Arrays.asList(TEST_PLUGIN_NAMESPACE, "invalid-namespace"));
        verify(epsMock, times(2)).getAllInNamespace(anyString());
        assertThat(plugins).hasSize(1);
    }

    @Test
    void shouldFindAPluginInAnyNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        Optional<EntandoPlugin> opl = entandoPluginService.findByName(TEST_PLUGIN_NAME);
        assertTrue(opl.isPresent());
        EntandoPlugin plg = opl.get();

        assertEquals(TEST_PLUGIN_NAME, plg.getMetadata().getName());
        assertEquals(TEST_PLUGIN_NAMESPACE, plg.getMetadata().getNamespace());
    }

    @Test
    void shouldFindAPluginInNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        Optional<EntandoPlugin> opl =
                entandoPluginService.findByNameAndNamespace(TEST_PLUGIN_NAME, TEST_PLUGIN_NAMESPACE);
        assertTrue(opl.isPresent());
        EntandoPlugin plg = opl.get();

        assertEquals(TEST_PLUGIN_NAME, plg.getMetadata().getName());
        assertEquals(TEST_PLUGIN_NAMESPACE, plg.getMetadata().getNamespace());
    }

    @Test
    void shouldThrowExceptionWhenSearchingPluginInNotObservedNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        Assertions.assertThrows(NotObservedNamespaceException.class, () -> {
            entandoPluginService.findByNameAndNamespace(TEST_PLUGIN_NAME, "some-other-namespace");
        });
    }

    @Test
    void shouldReturnEmptyOptionalForNotFoundPlugin() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        assertFalse(entandoPluginService.findByName("some-plugin").isPresent());
    }

    @Test
    void shouldCreateAPluginInProvidedNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPlugin testPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        entandoPluginService.deploy(testPlugin);

        List<EntandoPlugin> availablePlugins = EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace(TEST_PLUGIN_NAMESPACE).list().getItems();
        assertOnEntandoPlugins(testPlugin, availablePlugins);
    }

    @Test
    void shouldCreateOrReplaceAPluginInProvidedNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPlugin testPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        entandoPluginService.deploy(testPlugin, true);

        List<EntandoPlugin> availablePlugins = EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace(TEST_PLUGIN_NAMESPACE).list().getItems();
        assertOnEntandoPlugins(testPlugin, availablePlugins);
    }

    @Test
    void shouldCreateAPluginInAnotherObservedNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE, "my-namespace");
        EntandoPlugin testPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        testPlugin.getMetadata().setNamespace("my-namespace");
        entandoPluginService.deploy(testPlugin);

        List<EntandoPlugin> availablePlugins = EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace("my-namespace").list().getItems();
        assertOnEntandoPlugins(testPlugin, availablePlugins);
    }

    @Test
    void shouldCreateOrReplaceAPluginInAnotherObservedNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE, "my-namespace");
        EntandoPlugin testPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        testPlugin.getMetadata().setNamespace("my-namespace");
        entandoPluginService.deploy(testPlugin, true);

        List<EntandoPlugin> availablePlugins = EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace("my-namespace").list().getItems();
        assertOnEntandoPlugins(testPlugin, availablePlugins);
    }

    @Test
    void shouldUseCurrentNamespaceIfPluginNamespaceIsAbsent() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        KubernetesUtils k8sUtils = entandoPluginService.observedNamespaces.getKubernetesUtils();
        EntandoPlugin testPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        testPlugin.getMetadata().setNamespace(null);
        entandoPluginService.deploy(testPlugin);
        List<EntandoPlugin> availablePlugins = EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace(k8sUtils.getCurrentNamespace()).list().getItems();
        assertEquals(1, availablePlugins.size());
    }

    @Test
    void shouldThrowAnExceptionWhenDeployingInNotObservedNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        EntandoPlugin testPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        testPlugin.getMetadata().setNamespace("not-observed-namespace");
        Assertions.assertThrows(NotObservedNamespaceException.class, () -> {
            entandoPluginService.deploy(testPlugin);
        });
    }

    @Test
    void shouldDeletePluginInNamespace() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        // given I have one plugin in the namespace
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        assertEquals(1, EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace(TEST_PLUGIN_NAMESPACE).list().getItems().size());
        // when I delete the plugin
        entandoPluginService.deletePluginInNamespace(TEST_PLUGIN_NAME, TEST_PLUGIN_NAMESPACE);
        // That plugin is not available anymore
        assertEquals(0, EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace("my-namespace").list().getItems().size());
    }

    @Test
    void shouldDeletePluginAnywhere() {
        initializeService(TEST_PLUGIN_NAMESPACE);
        // given I have one plugin in the namespace
        EntandoPluginTestHelper.createTestEntandoPlugin(client);
        assertEquals(1, EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace(TEST_PLUGIN_NAMESPACE).list().getItems().size());
        // when I delete the plugin
        entandoPluginService.deletePlugin(TEST_PLUGIN_NAME);
        // That plugin is not available anymore
        assertEquals(0, EntandoPluginTestHelper.getEntandoPluginOperations(client)
                .inNamespace(TEST_PLUGIN_NAMESPACE).list().getItems().size());
    }

    /**
     * forces the validation to expect the same response for a plugin create action and a plugin createOrReplace action.
     *
     * @param expected the expected EntandoPlugin
     * @param availablePlugins the list of the available plugin in the current namespace
     */
    private void assertOnEntandoPlugins(EntandoPlugin expected, List<EntandoPlugin> availablePlugins) {

        assertEquals(1, availablePlugins.size());
        EntandoPlugin actual = availablePlugins.get(0);
        assertEquals(TEST_PLUGIN_NAME, actual.getMetadata().getName());
        assertEquals(expected.getSpec().getImage(), actual.getSpec().getImage());
        assertEquals(expected.getSpec().getHealthCheckPath(), actual.getSpec().getHealthCheckPath());
        assertEquals(expected.getSpec().getIngressPath(), actual.getSpec().getIngressPath());
        assertEquals(expected.getSpec().getKeycloakToUse(), actual.getSpec().getKeycloakToUse());
        assertEquals(expected.getSpec().getConnectionConfigNames(), actual.getSpec().getConnectionConfigNames());
        assertEquals(expected.getSpec().getEnvironmentVariables(), actual.getSpec().getEnvironmentVariables());
        assertEquals(expected.getSpec().getPermissions(), actual.getSpec().getPermissions());
        assertEquals(expected.getSpec().getRoles(), actual.getSpec().getRoles());
        assertEquals(expected.getSpec().getSecurityLevel(), actual.getSpec().getSecurityLevel());
        assertEquals(expected.getSpec().getDbms(), actual.getSpec().getDbms());
        assertEquals(expected.getSpec().getIngressHostName(), actual.getSpec().getIngressHostName());
        assertEquals(expected.getSpec().getReplicas(), actual.getSpec().getReplicas());
        assertEquals(expected.getSpec().getTlsSecretName(), actual.getSpec().getTlsSecretName());
    }
}

