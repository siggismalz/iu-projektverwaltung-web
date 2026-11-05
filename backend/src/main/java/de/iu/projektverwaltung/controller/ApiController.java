package de.iu.projektverwaltung.controller;

import de.iu.projektverwaltung.controller.ApiModels.*;
import de.iu.projektverwaltung.service.ProjectService;
import de.iu.projektverwaltung.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final UserService users;
    private final ProjectService projects;
    public ApiController(UserService users, ProjectService projects) {
        this.users = users; this.projects = projects;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @GetMapping("/session") public SessionView session(Authentication auth) { return users.session(auth); }
    @GetMapping("/users") public List<UserView> users(Authentication auth) { return users.list(auth); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(Authentication auth, @Valid @RequestBody NewUser input) {
        return users.create(auth, input);
    }
    @PutMapping("/users/{id}/role")
    public UserView role(Authentication auth, @PathVariable Long id, @Valid @RequestBody RoleChange input) {
        return users.changeRole(auth, id, input);
    }
    @GetMapping("/projects") public List<ProjectView> projects(Authentication auth) { return projects.list(auth); }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED)
    public ProjectView createProject(Authentication auth, @Valid @RequestBody ProjectInput input) {
        return projects.create(auth, input);
    }
    @PutMapping("/projects/{id}")
    public ProjectView updateProject(Authentication auth, @PathVariable Long id, @Valid @RequestBody ProjectInput input) {
        return projects.update(auth, id, input);
    }
    @DeleteMapping("/projects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(Authentication auth, @PathVariable Long id) { projects.delete(auth, id); }
    @GetMapping("/projects/{id}/tasks")
    public List<TaskView> tasks(Authentication auth, @PathVariable Long id) { return projects.listTasks(auth, id); }
    @PostMapping("/projects/{id}/tasks") @ResponseStatus(HttpStatus.CREATED)
    public TaskView createTask(Authentication auth, @PathVariable Long id, @Valid @RequestBody TaskInput input) {
        return projects.createTask(auth, id, input);
    }
    @PutMapping("/tasks/{id}")
    public TaskView updateTask(Authentication auth, @PathVariable Long id, @Valid @RequestBody TaskInput input) {
        return projects.updateTask(auth, id, input);
    }
    @DeleteMapping("/tasks/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(Authentication auth, @PathVariable Long id) { projects.deleteTask(auth, id); }
}
