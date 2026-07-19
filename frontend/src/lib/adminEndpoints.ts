import { z } from 'zod';
import { api, apiGet, apiPost } from './api';
import {
  adminCodeSchema,
  adminCourseSchema,
  adminMindmapSchema,
  adminModuleSchema,
  adminOverviewSchema,
  adminPackSchema,
  adminSchoolSchema,
  adminSourceExamSchema,
  adminUserSchema,
  generateCodesResponseSchema,
  notificationAdminSchema,
  pageSchema,
  questionAdminSchema,
  signalAdminSchema,
  subscriptionAuditSchema,
  supportMessageSchema,
  type AdminCourse,
  type AdminMindmap,
  type AdminModule,
  type AdminPack,
  type AdminSchool,
  type AdminSourceExam,
  type Difficulty,
  type ExamType,
  type QuestionAdmin,
  type SignalStatus,
  type UserStatus,
} from './schemas';

// A generous page size for admin lists (Paging caps at 50 server-side).
const ALL = 50;

// Overview -------------------------------------------------------------
export const fetchAdminOverview = () => apiGet('/api/admin/overview', adminOverviewSchema);

// Schools --------------------------------------------------------------
export const fetchAdminSchools = () =>
  apiGet(`/api/admin/schools?size=${ALL}`, pageSchema(adminSchoolSchema)).then((p) => p.content);
export const createSchool = (body: { name: string; slug: string }) =>
  apiPost('/api/admin/schools', body, adminSchoolSchema);
export const updateSchool = (id: string, body: { name: string; slug: string }) =>
  api.put(`/api/admin/schools/${id}`, body).then((r) => adminSchoolSchema.parse(r.data));
export const deleteSchool = (id: string) => api.delete(`/api/admin/schools/${id}`);

// Modules --------------------------------------------------------------
export const fetchAdminModules = () =>
  apiGet(`/api/admin/modules?size=${ALL}`, pageSchema(adminModuleSchema)).then((p) => p.content);
export const createModule = (body: {
  schoolId: string;
  studyYear: number;
  name: string;
  published: boolean;
}) => apiPost('/api/admin/modules', body, adminModuleSchema);
export const updateModule = (
  id: string,
  body: { schoolId: string; studyYear: number; name: string; published: boolean },
): Promise<AdminModule> =>
  api.put(`/api/admin/modules/${id}`, body).then((r) => adminModuleSchema.parse(r.data));
export const deleteModule = (id: string) => api.delete(`/api/admin/modules/${id}`);
export const publishModule = (id: string, published: boolean) =>
  api.patch(`/api/admin/modules/${id}/publish`, { published }).then((r) => adminModuleSchema.parse(r.data));
export const reorderModules = (orderedIds: string[]) =>
  api.patch('/api/admin/modules/reorder', { orderedIds });

// Courses --------------------------------------------------------------
export const fetchAdminCourses = () =>
  apiGet(`/api/admin/courses?size=${ALL}`, pageSchema(adminCourseSchema)).then((p) => p.content);
export const createCourse = (body: {
  moduleId: string;
  name: string;
  published: boolean;
  freePreview: boolean;
}) => apiPost('/api/admin/courses', body, adminCourseSchema);
export const updateCourse = (
  id: string,
  body: { moduleId: string; name: string; published: boolean; freePreview: boolean },
): Promise<AdminCourse> =>
  api.put(`/api/admin/courses/${id}`, body).then((r) => adminCourseSchema.parse(r.data));
export const deleteCourse = (id: string) => api.delete(`/api/admin/courses/${id}`);
export const publishCourse = (id: string, published: boolean) =>
  api.patch(`/api/admin/courses/${id}/publish`, { published }).then((r) => adminCourseSchema.parse(r.data));
export const reorderCourses = (orderedIds: string[]) =>
  api.patch('/api/admin/courses/reorder', { orderedIds });

// Source exams ---------------------------------------------------------
export const fetchAdminSourceExams = () =>
  apiGet(`/api/admin/source-exams?size=${ALL}`, pageSchema(adminSourceExamSchema)).then((p) => p.content);
export const createSourceExam = (body: {
  schoolId: string;
  label: string;
  year: number;
  examType: ExamType;
}) => apiPost('/api/admin/source-exams', body, adminSourceExamSchema);
export const updateSourceExam = (
  id: string,
  body: { schoolId: string; label: string; year: number; examType: ExamType },
): Promise<AdminSourceExam> =>
  api.put(`/api/admin/source-exams/${id}`, body).then((r) => adminSourceExamSchema.parse(r.data));
export const deleteSourceExam = (id: string) => api.delete(`/api/admin/source-exams/${id}`);

// Mindmaps -------------------------------------------------------------
export const fetchAdminMindmaps = () =>
  apiGet(`/api/admin/mindmaps?size=${ALL}`, pageSchema(adminMindmapSchema)).then((p) => p.content);
export const createMindmap = (body: {
  courseId: string;
  title: string;
  imageUrl: string;
  published: boolean;
}) => apiPost('/api/admin/mindmaps', body, adminMindmapSchema);
export const updateMindmap = (
  id: string,
  body: { courseId: string; title: string; imageUrl: string; published: boolean },
): Promise<AdminMindmap> =>
  api.put(`/api/admin/mindmaps/${id}`, body).then((r) => adminMindmapSchema.parse(r.data));
export const deleteMindmap = (id: string) => api.delete(`/api/admin/mindmaps/${id}`);
export const publishMindmap = (id: string, published: boolean) =>
  api.patch(`/api/admin/mindmaps/${id}/publish`, { published }).then((r) => adminMindmapSchema.parse(r.data));

// Packs ----------------------------------------------------------------
export const fetchAdminPacks = () =>
  apiGet(`/api/admin/packs?size=${ALL}`, pageSchema(adminPackSchema)).then((p) => p.content);
export interface PackInput {
  schoolId: string;
  studyYear: number | null;
  name: string;
  academicYear: string;
  priceDa: number;
  active: boolean;
  expiresAt: string;
}
export const createPack = (body: PackInput) => apiPost('/api/admin/packs', body, adminPackSchema);
export const updatePack = (id: string, body: PackInput): Promise<AdminPack> =>
  api.put(`/api/admin/packs/${id}`, body).then((r) => adminPackSchema.parse(r.data));
export const deletePack = (id: string) => api.delete(`/api/admin/packs/${id}`);

// Codes ----------------------------------------------------------------
export const generateCodes = (packId: string, count: number, maxUses: number | null) =>
  apiPost('/api/admin/codes', { packId, count, maxUses }, generateCodesResponseSchema);
export const fetchCodes = (packId: string | null, page: number) => {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  if (packId) {
    params.set('packId', packId);
  }
  return apiGet(`/api/admin/codes?${params.toString()}`, pageSchema(adminCodeSchema));
};
export const revokeCode = (id: string) =>
  apiPost(`/api/admin/codes/${id}/revoke`, undefined, adminCodeSchema);
/** One-shot CSV download URL for a freshly generated batch. */
export const codesCsvUrl = (csvToken: string) => `/api/admin/codes/csv/${csvToken}`;

// Questions ------------------------------------------------------------
export interface QuestionFilters {
  courseId?: string;
  moduleId?: string;
  sourceExamId?: string;
  difficulty?: Difficulty;
  published?: boolean;
  q?: string;
}
export const fetchAdminQuestions = (filters: QuestionFilters, page: number) => {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  if (filters.courseId) params.set('courseId', filters.courseId);
  if (filters.moduleId) params.set('moduleId', filters.moduleId);
  if (filters.sourceExamId) params.set('sourceExamId', filters.sourceExamId);
  if (filters.difficulty) params.set('difficulty', filters.difficulty);
  if (filters.published != null) params.set('published', String(filters.published));
  if (filters.q) params.set('q', filters.q);
  return apiGet(`/api/admin/questions?${params.toString()}`, pageSchema(questionAdminSchema));
};
export const fetchAdminQuestion = (id: string) =>
  apiGet(`/api/admin/questions/${id}`, questionAdminSchema);

export interface PropositionInput {
  letter: string;
  text: string;
  isTrue: boolean;
  explanationHtml?: string;
  explanationImages?: string[];
}
export interface QuestionInput {
  courseId: string;
  statement: string;
  statementImages?: string[];
  sourceExamId?: string;
  difficulty?: Difficulty;
  published: boolean;
  propositions: PropositionInput[];
}
export const createQuestion = (body: QuestionInput) =>
  apiPost('/api/admin/questions', body, questionAdminSchema);
export const updateQuestion = (id: string, body: QuestionInput): Promise<QuestionAdmin> =>
  api.put(`/api/admin/questions/${id}`, body).then((r) => questionAdminSchema.parse(r.data));
export const deleteQuestion = (id: string) => api.delete(`/api/admin/questions/${id}`);
export const publishQuestion = (id: string, published: boolean) =>
  api.patch(`/api/admin/questions/${id}/publish`, { published }).then((r) => questionAdminSchema.parse(r.data));
/** Bulk import; on any row error the backend returns 400 with a body of ImportRowError[]. */
export const importQuestions = (rows: unknown[]) =>
  api
    .post('/api/admin/questions/import', rows)
    .then((r) => z.object({ imported: z.number(), questionIds: z.array(z.string()) }).parse(r.data));

// Media ----------------------------------------------------------------
export const uploadContentImage = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api.post('/api/admin/media', form).then((r) => z.object({ url: z.string() }).parse(r.data));
};

// Signals (admin) ------------------------------------------------------
export const fetchAdminSignals = (status: SignalStatus | null, page: number) => {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  if (status) params.set('status', status);
  return apiGet(`/api/admin/signals?${params.toString()}`, pageSchema(signalAdminSchema));
};
export const resolveSignal = (id: string, reply: string) =>
  apiPost(`/api/admin/signals/${id}/resolve`, { reply }, signalAdminSchema);
export const rejectSignal = (id: string, reply: string) =>
  apiPost(`/api/admin/signals/${id}/reject`, { reply }, signalAdminSchema);

// Notifications --------------------------------------------------------
export interface BroadcastInput {
  kind: 'UPDATE' | 'QUESTIONS' | 'INFO';
  title: string;
  body: string;
  schoolId?: string;
  studyYear?: number;
}
export const broadcastNotification = (body: BroadcastInput) =>
  api.post('/api/admin/notifications', body);
export const fetchNotificationHistory = () =>
  apiGet('/api/admin/notifications', z.array(notificationAdminSchema));

// Support inbox --------------------------------------------------------
export const fetchSupportInbox = (page: number) =>
  apiGet(`/api/admin/support?page=${page}&size=20`, pageSchema(supportMessageSchema));

// Abonnés (users) ------------------------------------------------------
export const searchAdminUsers = (query: string, page: number) => {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  if (query) params.set('query', query);
  return apiGet(`/api/admin/users?${params.toString()}`, pageSchema(adminUserSchema));
};
export const updateUserStatus = (id: string, status: UserStatus) =>
  api.patch(`/api/admin/users/${id}/status`, { status }).then((r) => adminUserSchema.parse(r.data));
export const fetchSubscriptionAudit = (email: string) =>
  apiGet(`/api/admin/subscriptions?email=${encodeURIComponent(email)}`, z.array(subscriptionAuditSchema));

// Re-export catalog picker types for pages that compose across these.
export type { AdminSchool, AdminModule, AdminCourse, AdminSourceExam, AdminPack };
