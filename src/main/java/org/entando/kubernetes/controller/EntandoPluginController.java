package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;
import static org.springframework.hateoas.MediaTypes.HAL_JSON_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.entando.kubernetes.exception.BadRequestExceptionFactory;
import org.entando.kubernetes.exception.NotFoundExceptionFactory;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.service.EntandoLinkService;
import org.entando.kubernetes.service.EntandoPluginService;
import org.entando.kubernetes.service.IngressService;
import org.entando.kubernetes.service.assembler.EntandoPluginResourceAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zalando.problem.ThrowableProblem;


@Slf4j
@RestController
@RequestMapping("/plugins")
@RequiredArgsConstructor
public class EntandoPluginController {

    private final EntandoLinkService linkService;
    private final EntandoPluginService pluginService;
    private final EntandoPluginResourceAssembler resourceAssembler;
    private final IngressService ingressService;

    @GetMapping(produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<EntandoPlugin>>> list() {
        log.info("Listing all deployed plugins in observed namespaces");
        List<EntandoPlugin> plugins = pluginService.getAll();
        CollectionModel<EntityModel<EntandoPlugin>> collection = getPluginCollectionModel(plugins);
        addCollectionLinks(collection);
        return ResponseEntity.ok(collection);
    }


    @GetMapping(produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE}, params = "namespace")
    public ResponseEntity<CollectionModel<EntityModel<EntandoPlugin>>> listInNamespace(@RequestParam String namespace) {
        log.info("Listing all deployed plugins in {} observed namespace", namespace);
        List<EntandoPlugin> plugins = pluginService.getAllInNamespace(namespace);
        CollectionModel<EntityModel<EntandoPlugin>> collection = getPluginCollectionModel(plugins);
        addCollectionLinks(collection);
        return ResponseEntity.ok(collection);
    }

    @GetMapping(path = "/{name}", produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<EntityModel<EntandoPlugin>> get(@PathVariable("name") final String pluginName,
            @RequestParam(value = "namespace", required = false) String namespace) {
        log.info("Searching plugin with name {} in observed namespaces", pluginName);

        if (StringUtils.isEmpty(namespace)) {
            log.info("Searching plugin with name {} in observed namespaces", pluginName);
        } else {
            log.info("Searching plugin with name {} in namespace {}", pluginName, namespace);
        }

        EntandoPlugin plugin = getEntandoPluginOrFail(pluginName, namespace);
        return ResponseEntity.ok(resourceAssembler.toModel(plugin));
    }

    @GetMapping(path = "/{name}/ingress", produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<EntityModel<Ingress>> getPluginIngress(@PathVariable("name") final String pluginName,
            @RequestParam(value = "namespace", required = false) String namespace) {

        if (StringUtils.isEmpty(namespace)) {
            log.info("Searching plugin ingress with name {} in observed namespaces", pluginName);
        } else {
            log.info("Searching plugin ingress with name {} in namespace {}", pluginName, namespace);
        }

        EntandoPlugin plugin = getEntandoPluginOrFail(pluginName, namespace);
        Ingress pluginIngress = getEntandoPluginIngressOrFail(plugin);
        return ResponseEntity.ok(new EntityModel<>(pluginIngress));
    }

    @DeleteMapping(path = "/{name}", produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<Void> delete(@PathVariable("name") String pluginName,
            @RequestParam(value = "namespace", required = false) String namespace) {

        if (StringUtils.isEmpty(namespace)) {
            log.info("Deleting plugin with identifier {} from observed namespaces", pluginName);
        } else {
            log.info("Deleting plugin with identifier {} from namespace {}", pluginName, namespace);
        }

        EntandoPlugin plugin = getEntandoPluginOrFail(pluginName, namespace);
        pluginService.deletePlugin(plugin);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping(path = "/ingress/{name}", produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<Void> deletePluginIngressPath(@PathVariable("name") String pluginName,
            @RequestParam(value = "namespace", required = false) String namespace) {

        if (StringUtils.isEmpty(namespace)) {
            log.info("Deleting ingress path for plugin with identifier {} from observed namespaces", pluginName);
        } else {
            log.info("Deleting ingress path for plugin with identifier {} from namespace {}", pluginName, namespace);
        }

        EntandoPlugin plugin = getEntandoPluginOrFail(pluginName, namespace);
        List<EntandoAppPluginLink> links = linkService.getPluginLinks(plugin);
        ingressService.deletePathFromIngressByEntandoPlugin(plugin, links);
        
        return ResponseEntity.accepted().build();
    }


    @PostMapping(consumes = APPLICATION_JSON_VALUE, produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<EntityModel<EntandoPlugin>> create(
            @RequestBody EntandoPlugin entandoPlugin) {

        throwExceptionIfAlreadyDeployed(entandoPlugin);
        return this.excuteCreateOrReplacePlugin(entandoPlugin, EntandoPluginService.CREATE);
    }

    @PutMapping(consumes = APPLICATION_JSON_VALUE, produces = {APPLICATION_JSON_VALUE, HAL_JSON_VALUE})
    public ResponseEntity<EntityModel<EntandoPlugin>> createOrReplace(
            @RequestBody EntandoPlugin entandoPlugin) {

        return this.excuteCreateOrReplacePlugin(entandoPlugin, EntandoPluginService.CREATE_OR_REPLACE);
    }

    private ResponseEntity<EntityModel<EntandoPlugin>> excuteCreateOrReplacePlugin(EntandoPlugin entandoPlugin,
            boolean createOrReplace) {

        EntandoPlugin deployedPlugin = pluginService.deploy(entandoPlugin, createOrReplace);
        URI resourceLink = linkTo(methodOn(getClass()).get(deployedPlugin.getMetadata().getName(),
                deployedPlugin.getMetadata().getNamespace())).toUri();
        return ResponseEntity.created(resourceLink).body(resourceAssembler.toModel(deployedPlugin));
    }

    private EntandoPlugin getEntandoPluginOrFail(String pluginName, String namespace) {

        return ofNullable(namespace)
                .flatMap(ns -> pluginService.findByNameAndNamespace(pluginName, ns))
                .or(() -> pluginService.findByName(pluginName))
                .orElseThrow(() -> NotFoundExceptionFactory.entandoPlugin(pluginName));
    }

    private Ingress getEntandoPluginIngressOrFail(EntandoPlugin plugin) {
        return ingressService
                .findByEntandoPlugin(plugin)
                .<ThrowableProblem>orElseThrow(() -> {
                    throw NotFoundExceptionFactory.ingress(plugin);
                });

    }

    private void throwExceptionIfAlreadyDeployed(EntandoPlugin entandoPlugin) {
        Optional<EntandoPlugin> alreadyDeployedPlugin = pluginService
                .findByName(entandoPlugin.getMetadata().getName());
        if (alreadyDeployedPlugin.isPresent()) {
            throw BadRequestExceptionFactory.pluginAlreadyDeployed(alreadyDeployedPlugin.get());
        }
    }

    private void addCollectionLinks(CollectionModel<EntityModel<EntandoPlugin>> collection) {
        collection.add(linkTo(methodOn(EntandoPluginController.class).get(null, null)).withRel("plugin"));
        collection.add(linkTo(methodOn(EntandoPluginController.class).listInNamespace(null)).withRel("plugins-in-namespace"));
        collection.add(linkTo(methodOn(EntandoLinksController.class).listPluginLinks(null)).withRel("plugin-links"));
        collection.add(linkTo(methodOn(EntandoPluginController.class).deletePluginIngressPath(null, null)).withRel(
                "delete-plugin-ingress-path"));
        collection.add(linkTo(methodOn(EntandoPluginController.class).createOrReplace(null)).withRel(
                "create-or-replace-plugin"));
    }

    private CollectionModel<EntityModel<EntandoPlugin>> getPluginCollectionModel(List<EntandoPlugin> plugins) {
        return new CollectionModel<>(plugins.stream().map(resourceAssembler::toModel).collect(Collectors.toList()));
    }


}
