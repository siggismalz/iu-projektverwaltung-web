export type Role = 'ADMIN' | 'LEAD' | 'MEMBER';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE';
export interface User { id: number; username: string; displayName: string; role: Role }
export interface Session extends User { tenantName: string }
export interface Project {
  id: number; name: string; description: string; archived: boolean;
  memberIds: number[]; memberNames: string[];
  totalTasks: number; doneTasks: number; progress: number;
}
export interface Task {
  id: number; title: string; description: string; status: TaskStatus;
  assigneeId: number | null; assigneeName: string | null; projectId: number;
}
export const roleNames: Record<Role, string> = {
  ADMIN: 'Administration', LEAD: 'Projektleitung', MEMBER: 'Mitarbeitende',
};
export const statusNames: Record<TaskStatus, string> = {
  OPEN: 'Offen', IN_PROGRESS: 'In Bearbeitung', DONE: 'Erledigt',
};
