package de.iu.projektverwaltung;

import com.fasterxml.jackson.databind.*;
import de.iu.projektverwaltung.model.*;
import de.iu.projektverwaltung.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Echte HTTP-Controller, Securityfilter, Services und H2/JPA gemeinsam. Keine Repository-Mocks. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired TaskRepository tasks;
    @Autowired PasswordEncoder passwords;
    private AppUser admin, lead, member, foreign;
    private Project intranet, portal, other;

    @BeforeEach
    void fixture() {
        admin = users.findByUsername("admin").orElseThrow();
        lead = users.findByUsername("leitung").orElseThrow();
        member = users.findByUsername("mitarbeiter").orElseThrow();
        foreign = users.findByUsername("fremd").orElseThrow();
        List<Project> own = projects.findAllByTenantIdOrderByIdAsc(admin.getTenant().getId());
        intranet = own.stream().filter(p -> p.getName().equals("Intranet-Relaunch")).findFirst().orElseThrow();
        portal = own.stream().filter(p -> p.getName().equals("Kundenportal")).findFirst().orElseThrow();
        other = projects.findAllByTenantIdOrderByIdAsc(foreign.getTenant().getId()).getFirst();
    }

    @Test
    void anonymousAccessAndIncorrectPasswordAreRejected() throws Exception {
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").isString());
        mvc.perform(post("/api/login").with(csrf()).param("username", "admin").param("password", "falsch"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void realCsrfHandshakeLoginRotationAndLogoutWork() throws Exception {
        MvcResult tokenResponse = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) tokenResponse.getRequest().getSession(false);
        JsonNode token = body(tokenResponse);
        MvcResult login = mvc.perform(post("/api/login").session(session)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .param("username", "admin").param("password", "Demo2026!"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/session").session(authenticated)).andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin")).andExpect(jsonPath("$.tenantName").value("Muster IT GmbH"));
        mvc.perform(post("/api/logout").session(authenticated)
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isForbidden());
        JsonNode fresh = body(mvc.perform(get("/api/csrf").session(authenticated)).andReturn());
        mvc.perform(post("/api/logout").session(authenticated)
                        .header(fresh.get("headerName").asText(), fresh.get("token").asText()))
                .andExpect(status().isOk());
        assertThat(authenticated.isInvalid()).isTrue();
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingCsrfTokenBlocksMutations() throws Exception {
        mvc.perform(post("/api/projects").with(user("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("Neues Projekt", Set.of(member.getId()), false)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").isString());
    }

    @Test
    void memberSeesOnlyAssignedProjectsAndTenantDirectory() throws Exception {
        mvc.perform(get("/api/projects").with(user("mitarbeiter"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("Intranet-Relaunch"));
        mvc.perform(get("/api/users").with(user("mitarbeiter"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3))).andExpect(jsonPath("$[*].username", not(hasItem("fremd"))))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist()).andExpect(jsonPath("$[0].tenant").doesNotExist());
    }

    @Test
    void adminScopeEndsAtTenantBoundary() throws Exception {
        mvc.perform(get("/api/projects").with(user("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/projects/" + other.getId() + "/tasks").with(user("admin"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects").with(user("fremd"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].name").value("Fremdes Projekt"));
    }

    @Test
    void directIdsDoNotBypassProjectOrTenantPermissions() throws Exception {
        Task portalTask = tasks.findAllByProjectIdOrderByIdAsc(portal.getId()).getFirst();
        Task otherTask = tasks.findAllByProjectIdOrderByIdAsc(other.getId()).getFirst();
        mvc.perform(get("/api/projects/" + portal.getId() + "/tasks").with(user("mitarbeiter"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/" + portalTask.getId()).with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Manipulation", "DONE", null)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/" + otherTask.getId()).with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Manipulation", "DONE", null)))
                .andExpect(status().isNotFound());
        assertThat(otherTask.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void memberCannotManageProjectsUsersOrRoles() throws Exception {
        mvc.perform(post("/api/projects").with(user("mitarbeiter")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("Verbotenes Projekt", Set.of(member.getId()), false)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/projects/" + intranet.getId()).with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody("Geändert", Set.of(member.getId()), false)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users").with(user("leitung")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("neukonto", "MEMBER"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/" + member.getId() + "/role").with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassignedLeadCannotEditAnotherProject() throws Exception {
        users.saveAndFlush(new AppUser("andereleitung", "Andere Leitung", passwords.encode("Demo2026!"), Role.LEAD, admin.getTenant()));
        mvc.perform(put("/api/projects/" + intranet.getId()).with(user("andereleitung")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody("Manipulation", Set.of(member.getId()), false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAccountWithHashedPasswordAndOwnTenant() throws Exception {
        MvcResult result = mvc.perform(post("/api/users").with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(userBody("NEUKONTO", "MEMBER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("neukonto"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        AppUser created = users.findById(body(result).get("id").asLong()).orElseThrow();
        assertThat(created.getTenant().getId()).isEqualTo(admin.getTenant().getId());
        assertThat(created.getPasswordHash()).isNotEqualTo("Sicher2026!");
        assertThat(passwords.matches("Sicher2026!", created.getPasswordHash())).isTrue();
        mvc.perform(put("/api/users/" + created.getId() + "/role").with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"LEAD\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("LEAD"));
    }

    @Test
    void duplicateUsernameAndRemovalOfLastAdminAreRejected() throws Exception {
        mvc.perform(post("/api/users").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("ADMIN", "MEMBER"))).andExpect(status().isConflict());
        mvc.perform(put("/api/users/" + admin.getId() + "/role").with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/users/" + foreign.getId() + "/role").with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void roleChangesTakeEffectWithoutTrustingStaleSessionAuthorities() throws Exception {
        mvc.perform(put("/api/users/" + lead.getId() + "/role").with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/projects").with(user("leitung").roles("LEAD")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody("Veraltete Rolle", Set.of(lead.getId()), false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadCreatesProjectWithMultipleMembersAndKeepsOwnAccess() throws Exception {
        MvcResult result = mvc.perform(post("/api/projects").with(user("leitung")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody("Testprojekt", Set.of(member.getId()), false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.memberIds", hasSize(2)))
                .andExpect(jsonPath("$.memberIds", hasItem(member.getId().intValue())))
                .andExpect(jsonPath("$.memberIds", hasItem(lead.getId().intValue())))
                .andExpect(jsonPath("$.progress").value(0)).andReturn();
        long id = body(result).get("id").asLong();
        assertThat(projects.findById(id).orElseThrow().getTenant().getId()).isEqualTo(lead.getTenant().getId());
        mvc.perform(put("/api/projects/" + id).with(user("leitung")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody("Umbenannt", Set.of(member.getId()), true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Umbenannt"))
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void projectMemberAssignmentCannotCrossTenantBoundary() throws Exception {
        mvc.perform(post("/api/projects").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("Falsche Zuordnung", Set.of(foreign.getId()), false)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberCreatesAndEditsTaskAndProgressFollowsDoneCount() throws Exception {
        long projectId = body(mvc.perform(post("/api/projects").with(user("leitung")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(projectBody("Fortschrittstest", Set.of(member.getId()), false)))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        long taskId = body(mvc.perform(post("/api/projects/" + projectId + "/tasks").with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Erste Aufgabe", "OPEN", member.getId())))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(put("/api/tasks/" + taskId).with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Überarbeitet", "IN_PROGRESS", lead.getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(put("/api/tasks/" + taskId).with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Erledigt", "DONE", member.getId())))
                .andExpect(status().isOk());
        JsonNode all = body(mvc.perform(get("/api/projects").with(user("leitung"))).andReturn());
        JsonNode project = findProject(all, projectId);
        assertThat(project.get("totalTasks").asInt()).isEqualTo(1);
        assertThat(project.get("doneTasks").asInt()).isEqualTo(1);
        assertThat(project.get("progress").asInt()).isEqualTo(100);
        mvc.perform(delete("/api/tasks/" + taskId).with(user("mitarbeiter")).with(csrf())).andExpect(status().isNoContent());
        assertThat(findProject(body(mvc.perform(get("/api/projects").with(user("leitung"))).andReturn()), projectId)
                .get("progress").asInt()).isZero();
    }

    @Test
    void taskAssigneeMustBelongToProjectAndTenant() throws Exception {
        for (Long id : List.of(foreign.getId(), admin.getId())) {
            mvc.perform(post("/api/projects/" + intranet.getId() + "/tasks").with(user("leitung")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(taskBody("Falsche Person", "OPEN", id)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void archivedProjectRemainsReadableButTaskMutationsAreBlocked() throws Exception {
        Task task = tasks.findAllByProjectIdOrderByIdAsc(intranet.getId()).getFirst();
        mvc.perform(put("/api/projects/" + intranet.getId()).with(user("leitung")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody(intranet.getName(),
                                Set.of(lead.getId(), member.getId()), true))).andExpect(status().isOk());
        mvc.perform(get("/api/projects/" + intranet.getId() + "/tasks").with(user("mitarbeiter"))).andExpect(status().isOk());
        mvc.perform(post("/api/projects/" + intranet.getId() + "/tasks").with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Zu spät", "OPEN", null)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/tasks/" + task.getId()).with(user("admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Zu spät", "DONE", null)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/tasks/" + task.getId()).with(user("mitarbeiter")).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void memberWithAssignedTaskCannotBeRemovedSilently() throws Exception {
        mvc.perform(put("/api/projects/" + intranet.getId()).with(user("leitung")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(projectBody(intranet.getName(), Set.of(lead.getId()), false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("neu zuordnen")));
    }

    @Test
    void blankOversizedUnknownAndInvalidEnumInputsAreRejected() throws Exception {
        mvc.perform(post("/api/projects").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("   ", Set.of(), false))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(projectBody("x".repeat(121), Set.of(), false))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects/" + intranet.getId() + "/tasks").with(user("mitarbeiter")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(taskBody("Aufgabe", "INVALID", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Manipulation\",\"description\":\"\",\"memberIds\":[],\"tenantId\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"neu\",\"displayName\":\"Neu\",\"password\":\"kurz\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void projectDeletionRemovesItsTasksWithoutTouchingOtherProjects() throws Exception {
        long taskCount = tasks.countByProjectId(portal.getId());
        mvc.perform(delete("/api/projects/" + intranet.getId()).with(user("leitung")).with(csrf()))
                .andExpect(status().isNoContent());
        projects.flush();
        assertThat(projects.findById(intranet.getId())).isEmpty();
        assertThat(tasks.countByProjectId(intranet.getId())).isZero();
        assertThat(tasks.countByProjectId(portal.getId())).isEqualTo(taskCount);
    }

    private JsonNode body(MvcResult response) throws Exception { return json.readTree(response.getResponse().getContentAsString()); }
    private JsonNode findProject(JsonNode array, long id) {
        for (JsonNode item : array) { if (item.get("id").asLong() == id) return item; }
        throw new AssertionError("Projekt fehlt: " + id);
    }
    private String projectBody(String name, Set<Long> members, boolean archived) throws Exception {
        return json.writeValueAsString(Map.of("name", name, "description", "Testbeschreibung", "memberIds", members, "archived", archived));
    }
    private String taskBody(String title, String status, Long assignee) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("title", title); input.put("description", "Testbeschreibung"); input.put("status", status); input.put("assigneeId", assignee);
        return json.writeValueAsString(input);
    }
    private String userBody(String username, String role) throws Exception {
        return json.writeValueAsString(Map.of("username", username, "displayName", "Neues Demokonto", "password", "Sicher2026!", "role", role));
    }
}
