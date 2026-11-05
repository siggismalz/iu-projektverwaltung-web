package de.iu.projektverwaltung.service;

import de.iu.projektverwaltung.controller.ApiModels.*;
import de.iu.projektverwaltung.model.*;
import de.iu.projektverwaltung.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProjectService {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final UserService userService;
    public ProjectService(ProjectRepository projects, TaskRepository tasks,
                          UserRepository users, UserService userService) {
        this.projects = projects; this.tasks = tasks; this.users = users; this.userService = userService;
    }

    public List<ProjectView> list(Authentication auth) {
        AppUser actor = userService.current(auth);
        return projects.findAllByTenantIdOrderByIdAsc(actor.getTenant().getId()).stream()
                .filter(project -> mayRead(actor, project)).map(this::view).toList();
    }

    @Transactional
    public ProjectView create(Authentication auth, ProjectInput input) {
        AppUser actor = userService.current(auth);
        requireLeader(actor);
        Set<AppUser> members = members(actor, input.memberIds());
        // Projektleitungen behalten Zugriff auf die von ihnen angelegten Projekte.
        if (actor.getRole() == Role.LEAD) { members.add(actor); }
        Project project = new Project(input.name().strip(), input.description().strip(), actor.getTenant(), members);
        return view(projects.save(project));
    }

    @Transactional
    public ProjectView update(Authentication auth, Long id, ProjectInput input) {
        AppUser actor = userService.current(auth);
        Project project = accessible(actor, id);
        requireLeader(actor);
        Set<AppUser> members = members(actor, input.memberIds());
        if (actor.getRole() == Role.LEAD) { members.add(actor); }
        Set<Long> memberIds = new HashSet<>();
        members.forEach(user -> memberIds.add(user.getId()));
        boolean assignedRemoved = tasks.findAllByProjectIdOrderByIdAsc(id).stream()
                .anyMatch(task -> task.getAssignee() != null && !memberIds.contains(task.getAssignee().getId()));
        if (assignedRemoved) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Mitglied hat noch zugewiesene Aufgaben. Bitte Aufgaben zuerst neu zuordnen.");
        }
        project.update(input.name().strip(), input.description().strip(), input.archived(), members);
        return view(project);
    }

    @Transactional
    public void delete(Authentication auth, Long id) {
        AppUser actor = userService.current(auth);
        Project project = accessible(actor, id);
        requireLeader(actor);
        tasks.deleteAllByProjectId(id);
        projects.delete(project);
    }

    public List<TaskView> listTasks(Authentication auth, Long projectId) {
        AppUser actor = userService.current(auth);
        Project project = accessible(actor, projectId);
        return tasks.findAllByProjectIdOrderByIdAsc(project.getId()).stream().map(this::taskView).toList();
    }

    @Transactional
    public TaskView createTask(Authentication auth, Long projectId, TaskInput input) {
        AppUser actor = userService.current(auth);
        Project project = accessible(actor, projectId);
        requireActive(project);
        AppUser assignee = assignee(actor, project, input.assigneeId());
        Task task = new Task(input.title().strip(), input.description().strip(), input.status(), project, assignee);
        return taskView(tasks.save(task));
    }

    @Transactional
    public TaskView updateTask(Authentication auth, Long id, TaskInput input) {
        AppUser actor = userService.current(auth);
        Task task = accessibleTask(actor, id);
        requireActive(task.getProject());
        task.update(input.title().strip(), input.description().strip(), input.status(),
                assignee(actor, task.getProject(), input.assigneeId()));
        return taskView(task);
    }

    @Transactional
    public void deleteTask(Authentication auth, Long id) {
        AppUser actor = userService.current(auth);
        Task task = accessibleTask(actor, id);
        requireActive(task.getProject());
        tasks.delete(task);
    }

    private Project accessible(AppUser actor, Long id) {
        Project project = projects.findByIdAndTenantId(id, actor.getTenant().getId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Projekt nicht gefunden."));
        if (!mayRead(actor, project)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Keine Berechtigung für dieses Projekt.");
        }
        return project;
    }

    private Task accessibleTask(AppUser actor, Long id) {
        Task task = tasks.findByIdAndProjectTenantId(id, actor.getTenant().getId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Aufgabe nicht gefunden."));
        if (!mayRead(actor, task.getProject())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Keine Berechtigung für dieses Projekt.");
        }
        return task;
    }

    private boolean mayRead(AppUser actor, Project project) {
        return actor.getRole() == Role.ADMIN || project.getMembers().stream()
                .anyMatch(member -> member.getId().equals(actor.getId()));
    }

    private void requireLeader(AppUser actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.LEAD) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nur Administration und Projektleitung dürfen Projekte verwalten.");
        }
    }

    private void requireActive(Project project) {
        if (project.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, "Projekt ist archiviert. Aufgaben können nur gelesen werden.");
        }
    }

    private Set<AppUser> members(AppUser actor, Set<Long> ids) {
        Set<AppUser> result = new LinkedHashSet<>();
        for (Long id : ids) {
            result.add(users.findByIdAndTenantId(id, actor.getTenant().getId()).orElseThrow(() ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Projektmitglied ist im eigenen Mandanten nicht verfügbar.")));
        }
        return result;
    }

    private AppUser assignee(AppUser actor, Project project, Long id) {
        if (id == null) { return null; }
        AppUser user = users.findByIdAndTenantId(id, actor.getTenant().getId()).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "Verantwortliche Person ist im eigenen Mandanten nicht verfügbar."));
        if (project.getMembers().stream().noneMatch(member -> member.getId().equals(id))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verantwortliche Person muss Projektmitglied sein.");
        }
        return user;
    }

    private ProjectView view(Project project) {
        List<AppUser> members = project.getMembers().stream().sorted(Comparator.comparing(AppUser::getId)).toList();
        long total = tasks.countByProjectId(project.getId());
        long done = tasks.countByProjectIdAndStatus(project.getId(), TaskStatus.DONE);
        int progress = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return new ProjectView(project.getId(), project.getName(), project.getDescription(), project.isArchived(),
                members.stream().map(AppUser::getId).toList(), members.stream().map(AppUser::getDisplayName).toList(),
                total, done, progress);
    }

    private TaskView taskView(Task task) {
        AppUser person = task.getAssignee();
        return new TaskView(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                person == null ? null : person.getId(), person == null ? null : person.getDisplayName(),
                task.getProject().getId());
    }
}
