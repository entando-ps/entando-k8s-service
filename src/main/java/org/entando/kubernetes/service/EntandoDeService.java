package org.entando.kubernetes.service;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Collections;
import java.util.List;
import org.entando.kubernetes.model.debundle.DoneableEntandoDeBundle;
import org.entando.kubernetes.model.debundle.EntandoDeBundle;
import org.entando.kubernetes.model.debundle.EntandoDeBundleList;
import org.entando.kubernetes.model.plugin.DoneableEntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.model.plugin.EntandoPluginList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EntandoDeService {

    private final KubernetesClient client;

    public EntandoDeService(@Autowired final KubernetesClient client) {
        this.client = client;
    }

    public List<EntandoDeBundle> getAllBundles() {
        return getBundleOperations().inAnyNamespace().list().getItems();
    }

    public List<EntandoDeBundle> getAllBundlesInNamespace(String namespace) {
        return getBundleOperations().inNamespace(namespace).list().getItems();
    }

    //CHECKSTYLE:OFF
    private MixedOperation<EntandoDeBundle, EntandoDeBundleList, DoneableEntandoDeBundle, Resource<EntandoDeBundle, DoneableEntandoDeBundle>> getBundleOperations() {
        //CHECKSTYLE:ON
        CustomResourceDefinition entandoDeBundleCrd = client.customResourceDefinitions()
                .withName(EntandoDeBundle.CRD_NAME).get();
        return client.customResources(entandoDeBundleCrd, EntandoDeBundle.class, EntandoDeBundleList.class,
                DoneableEntandoDeBundle.class);
    }
}
