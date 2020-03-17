package org.entando.kubernetes.controller;

import static org.entando.kubernetes.util.EntandoPluginTestHelper.BASE_PLUGIN_ENDPOINT;
import static org.entando.kubernetes.util.EntandoPluginTestHelper.TEST_PLUGIN_NAME;
import static org.entando.kubernetes.util.EntandoPluginTestHelper.TEST_PLUGIN_NAMESPACE;
import static org.entando.kubernetes.util.EntandoPluginTestHelper.getTestEntandoPlugin;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import org.entando.kubernetes.EntandoKubernetesJavaApplication;
import org.entando.kubernetes.config.TestJwtDecoderConfig;
import org.entando.kubernetes.config.TestKubernetesConfig;
import org.entando.kubernetes.config.TestSecurityConfiguration;
import org.entando.kubernetes.model.link.EntandoAppPluginLink;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import org.entando.kubernetes.service.EntandoLinkService;
import org.entando.kubernetes.service.EntandoPluginService;
import org.entando.kubernetes.util.EntandoLinkTestHelper;
import org.entando.kubernetes.util.EntandoPluginTestHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = {
                EntandoKubernetesJavaApplication.class,
                TestSecurityConfiguration.class,
                TestKubernetesConfig.class,
                TestJwtDecoderConfig.class
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("component")
public class EntandoPluginControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockBean
    private EntandoPluginService entandoPluginService;

    @MockBean
    private EntandoLinkService entandoLinkService;

    @Test
    public void shouldReturnEmptyListIfNotPluginIsDeployed() throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .build().toUri();

        mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().json("{}"));

        verify(entandoPluginService, times(1)).getAll();
    }

    @Test
    public void shouldReturnAListWithOnePlugin() throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .queryParam("namespace", TEST_PLUGIN_NAMESPACE)
                .build().toUri();


        EntandoPlugin tempPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        when(entandoPluginService.getAllInNamespace(any(String.class))).thenReturn(Collections.singletonList(tempPlugin));

        mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$._embedded.entandoPluginList").isNotEmpty())
                .andExpect(jsonPath("$._embedded.entandoPluginList[0].metadata.name" ).value(TEST_PLUGIN_NAME))
                .andExpect(jsonPath("$._embedded.entandoPluginList[0].metadata.namespace").value(TEST_PLUGIN_NAMESPACE));

        verify(entandoPluginService, times(1)).getAllInNamespace(TEST_PLUGIN_NAMESPACE);
    }

    @Test
    public void shouldReturnPluginByName() throws Exception {
        EntandoPlugin tempPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        String pluginName = tempPlugin.getMetadata().getName();
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .pathSegment(pluginName)
                .build().toUri();

        when(entandoPluginService.findByName(eq(pluginName))).thenReturn(Optional.of(tempPlugin));

        mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.metadata.name").value(pluginName));

    }

    @Test
    public void shouldReturnListOfLinksToThePlugin() throws Exception {
        EntandoPlugin tempPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();
        EntandoAppPluginLink tempLink = EntandoLinkTestHelper.getTestLink();

        String pluginName = tempPlugin.getMetadata().getName();
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .pathSegment(pluginName, "links")
                .build().toUri();

        when(entandoPluginService.findByName(eq(pluginName))).thenReturn(Optional.of(tempPlugin));
        when(entandoLinkService.getPluginLinks(eq(tempPlugin))).thenReturn(Collections.singletonList(tempLink));

        String jsonPathToCheck = "$._embedded.entandoAppPluginLinkList";

        mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath(jsonPathToCheck).isNotEmpty())
                .andExpect(jsonPath(jsonPathToCheck + "[0].spec.entandoPluginName" ).value(TEST_PLUGIN_NAME))
                .andExpect(jsonPath(jsonPathToCheck + "[0].spec.entandoPluginNamespace").value(TEST_PLUGIN_NAMESPACE));

        verify(entandoPluginService, times(1)).findByName(pluginName);
        verify(entandoLinkService, times(1)).getPluginLinks(any());

    }

    @Test
    public void shouldReturn404IfPluginNotFound() throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .pathSegment(TEST_PLUGIN_NAMESPACE, TEST_PLUGIN_NAME)
                .build().toUri();
        mvc.perform(get(uri)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

    }

    @Test
    public void shouldThrowBadRequestExceptionForAlreadyDeployedPlugin() throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .build().toUri();
        EntandoPlugin tempPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();

        when(entandoPluginService.findByName(eq(TEST_PLUGIN_NAME)))
                .thenReturn(Optional.of(tempPlugin));

        mvc.perform(post(uri)
                .content(mapper.writeValueAsString(tempPlugin))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

    }

    @Test
    public void shouldReturnCreatedForNewlyDeployedPlugin() throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .build().toUri();

        EntandoPlugin tempPlugin = EntandoPluginTestHelper.getTestEntandoPlugin();

        when(entandoPluginService.deploy(any(EntandoPlugin.class))).thenReturn(tempPlugin);

        mvc.perform(post(uri)
                .content(mapper.writeValueAsString(tempPlugin))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$._links.self").exists())
                .andExpect(jsonPath("$._links.self.href").value(endsWith(Paths.get("plugins", TEST_PLUGIN_NAME).toString())))
                .andExpect(jsonPath("$._links.plugins").exists());

    }

    @Test
    public void shouldReturnAcceptedWhenDeletingAPlugin() throws Exception {
        URI uri = UriComponentsBuilder
                .fromUriString(BASE_PLUGIN_ENDPOINT)
                .pathSegment(TEST_PLUGIN_NAME)
                .build().toUri();
        when(entandoPluginService.findByName(eq(TEST_PLUGIN_NAME)))
                .thenReturn(Optional.of(getTestEntandoPlugin()));

        mvc.perform(delete(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted());

    }
}
