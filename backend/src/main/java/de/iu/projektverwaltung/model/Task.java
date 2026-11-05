package de.iu.projektverwaltung.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tasks", indexes = @Index(name = "idx_task_project", columnList = "project_id"))
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15)
    private TaskStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    protected Task() { }
    public Task(String title, String description, TaskStatus status, Project project, AppUser assignee) {
        this.title = title; this.description = description; this.status = status;
        this.project = project; this.assignee = assignee;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public Project getProject() { return project; }
    public AppUser getAssignee() { return assignee; }
    public void update(String title, String description, TaskStatus status, AppUser assignee) {
        this.title = title; this.description = description; this.status = status; this.assignee = assignee;
    }
}
