package org.entando.kubernetes.util;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import java.io.IOException;
import java.util.List;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.plugin.DoneableEntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginBuilder;
import org.entando.kubernetes.model.plugin.EntandoPluginList;
import org.entando.kubernetes.model.plugin.PluginSecurityLevel;
import org.springframework.core.io.ClassPathResource;

public class EntandoPluginTestHelper {

    public static String BASE_PLUGIN_ENDPOINT = "/plugins";

    public static void createEntandoPlugin(KubernetesClient client, String pluginName) {
        EntandoPluginTestHelper.createEntandoPlugin(client, pluginName, client.getConfiguration().getNamespace());
    }

    public static void createEntandoPlugin(KubernetesClient client, String pluginName, String pluginNamespace) {

        EntandoPlugin entandoPlugin = getTestEntandoPlugin(pluginName);
        entandoPlugin.getMetadata().setNamespace(pluginNamespace);

        KubernetesDeserializer
                .registerCustomKind(entandoPlugin.getApiVersion(), entandoPlugin.getKind(), EntandoPlugin.class);

        getEntandoPluginOperations(client).inNamespace(pluginNamespace).createOrReplace(entandoPlugin);
    }


    public static MixedOperation<EntandoPlugin, EntandoPluginList, DoneableEntandoPlugin,
                Resource<EntandoPlugin, DoneableEntandoPlugin>> getEntandoPluginOperations(KubernetesClient client) {
        CustomResourceDefinition entandoPluginCrd = createEntandoPluginCrd(client);

        return client.customResources(entandoPluginCrd, EntandoPlugin.class, EntandoPluginList.class,
                DoneableEntandoPlugin.class);
    }

    public static CustomResourceDefinition createEntandoPluginCrd(KubernetesClient client) {
        String entandoPluginCrdResource = "crd/EntandoPluginCRD.yaml";
        CustomResourceDefinition entandoPluginCrd = client.customResourceDefinitions().withName(EntandoPlugin.CRD_NAME)
                .get();
        if (entandoPluginCrd == null) {
            List<HasMetadata> list = null;
            try {
                list = client.load(new ClassPathResource(entandoPluginCrdResource).getInputStream())
                        .get();
            } catch (IOException e) {
                throw new RuntimeException("An error occurred while reading resource " + entandoPluginCrdResource, e);
            }
            entandoPluginCrd = (CustomResourceDefinition) list.get(0);
            // see issue https://github.com/fabric8io/kubernetes-client/issues/1486
            entandoPluginCrd.getSpec().getValidation().getOpenAPIV3Schema().setDependencies(null);
            return client.customResourceDefinitions().createOrReplace(entandoPluginCrd);
        }
        return entandoPluginCrd;
    }

    public static EntandoPlugin getTestEntandoPlugin(String name) {
        EntandoPlugin entandoPlugin = new EntandoPluginBuilder().withNewSpec()
                .withImage("entando/entando-avatar-plugin")
                .withDbms(DbmsImageVendor.POSTGRESQL)
                .withReplicas(1)
                .withHealthCheckPath("/management/health")
                .withIngressPath("/dummyPlugin")
                .withSecurityLevel(PluginSecurityLevel.LENIENT)
                .withIngressHostName("dummyPlugin.test")
                .endSpec()
                .build();

        entandoPlugin.setMetadata(new ObjectMetaBuilder().withName(name).build());
        entandoPlugin.setApiVersion("entando.org/v1alpha1");
        return entandoPlugin;
    }
}
