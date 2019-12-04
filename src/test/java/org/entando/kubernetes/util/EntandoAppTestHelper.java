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
import org.entando.kubernetes.model.JeeServer;
import org.entando.kubernetes.model.app.DoneableEntandoApp;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppBuilder;
import org.entando.kubernetes.model.app.EntandoAppList;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.springframework.core.io.ClassPathResource;

public class EntandoAppTestHelper {

    public static final String BASE_APP_ENDPOINT = "/apps";
    public static final String TEST_APP_NAME = "my-app";
    public static final String TEST_APP_NAMESPACE = "my-app-namespace";

    public static void createEntandoApp(KubernetesClient client, String appName) {
        EntandoAppTestHelper.createEntandoApp(client, appName, client.getNamespace());
    }

    public static EntandoApp createTestEntandoApp(KubernetesClient client) {
        return createEntandoApp(client, TEST_APP_NAME, TEST_APP_NAMESPACE);
    }

    public static EntandoApp createEntandoApp(KubernetesClient client, String appName, String appNamespace) {

        EntandoApp ea = getTestEntandoApp(appName);
        ea.getMetadata().setNamespace(appNamespace);

        KubernetesDeserializer.registerCustomKind(ea.getApiVersion(), ea.getKind(), EntandoApp.class);

        return getEntandoAppOperations(client).inNamespace(appNamespace).createOrReplace(ea);
    }


    public static MixedOperation<EntandoApp, EntandoAppList, DoneableEntandoApp,
            Resource<EntandoApp, DoneableEntandoApp>> getEntandoAppOperations(KubernetesClient client) {
        CustomResourceDefinition entandoAppCrd = createEntandoAppCrd(client);

        return client.customResources(entandoAppCrd, EntandoApp.class, EntandoAppList.class,
                DoneableEntandoApp.class);
    }

    public static CustomResourceDefinition createEntandoAppCrd(KubernetesClient client) {
        String entandoAppCrdResource = "crd/EntandoAppCRD.yaml";
        CustomResourceDefinition entandoAppCrd = client.customResourceDefinitions().withName(EntandoApp.CRD_NAME)
                .get();
        if (entandoAppCrd == null) {
            List<HasMetadata> list = null;
            try {
                list = client.load(new ClassPathResource(entandoAppCrdResource).getInputStream())
                        .get();
            } catch (IOException e) {
                System.out.println(e.getMessage());
                throw new RuntimeException("An error occurred while reading resource " + entandoAppCrdResource, e);
            }
            entandoAppCrd = (CustomResourceDefinition) list.get(0);
            // see issue https://github.com/fabric8io/kubernetes-client/issues/1486
            entandoAppCrd.getSpec().getValidation().getOpenAPIV3Schema().setDependencies(null);
            return client.customResourceDefinitions().createOrReplace(entandoAppCrd);
        }
        return entandoAppCrd;
    }

    public static EntandoApp getTestEntandoApp() {
        EntandoApp entandoApp = new EntandoAppBuilder().withNewSpec()
                .withDbms(DbmsImageVendor.POSTGRESQL)
                .withReplicas(1)
                .withEntandoImageVersion("6.0.0-SNAPSHOT")
                .withStandardServerImage(JeeServer.WILDFLY)
                .endSpec()
                .build();

        entandoApp.setMetadata(new ObjectMetaBuilder().withName("my-app").build());
        entandoApp.setApiVersion("entando.org/v1alpha1");
        return entandoApp;
    }

    public static EntandoApp getTestEntandoApp(String name) {
        EntandoApp entandoApp = new EntandoAppBuilder().withNewSpec()
                .withDbms(DbmsImageVendor.POSTGRESQL)
                .withReplicas(1)
                .withEntandoImageVersion("6.0.0-SNAPSHOT")
                .withStandardServerImage(JeeServer.WILDFLY)
                .endSpec()
                .build();

        entandoApp.setMetadata(new ObjectMetaBuilder().withName(name).build());
        entandoApp.setApiVersion("entando.org/v1alpha1");
        return entandoApp;
    }
}
