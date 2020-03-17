package org.entando.kubernetes.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.entando.kubernetes.model.ObservedNamespaces;
import org.entando.kubernetes.model.app.DoneableEntandoApp;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.app.EntandoAppList;
import org.entando.kubernetes.model.app.EntandoAppOperationFactory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EntandoAppService extends EntandoKubernetesResourceCollector<EntandoApp> {

    public EntandoAppService(KubernetesClient client,
            ObservedNamespaces observedNamespaces) {
        super(client, observedNamespaces);
    }

    @Override
    public List<EntandoApp> getInNamespaceWithoutChecking(String namespace) {
        return getEntandoAppsOperations().inNamespace(namespace).list().getItems();
    }

    //CHECKSTYLE:OFF
    private MixedOperation<EntandoApp, EntandoAppList, DoneableEntandoApp, Resource<EntandoApp, DoneableEntandoApp>> getEntandoAppsOperations() {
        //CHECKSTYLE:ON
        return EntandoAppOperationFactory.produceAllEntandoApps(client);
    }

}
