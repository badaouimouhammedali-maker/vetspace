import { z } from 'zod';
import { api, apiGet, apiPost } from './api';
import {
  correctionSchema,
  countSchema,
  courseSchema,
  courseStatsSchema,
  moduleCoverageSchema,
  dailyStatsSchema,
  labelSchema,
  meSchema,
  mindmapSchema,
  moduleResourcesSchema,
  moduleSchema,
  noteSchema,
  notificationSchema,
  overviewSchema,
  pageSchema,
  publicPackSchema,
  publicStatsSchema,
  questionPlaySchema,
  redeemResponseSchema,
  schoolSchema,
  sessionPlaySchema,
  sessionStatsSchema,
  sessionSummarySchema,
  signalSchema,
  sourceExamSchema,
  type SessionSummary,
  type SessionType,
} from './schemas';

/** Filtres du constructeur de session — même forme que le jsonb côté serveur. */
export interface SessionFilters {
  moduleIds?: string[];
  courseIds?: string[];
  sourceExamIds?: string[];
  difficulty?: 'EASY' | 'MEDIUM' | 'HARD';
  onlyUnseen?: boolean;
  labelIds?: string[];
}

// Catalogue ------------------------------------------------------------

export const fetchSchools = () => apiGet('/api/schools', z.array(schoolSchema));

export const fetchModules = (studyYear: number) =>
  apiGet(`/api/modules?studyYear=${studyYear}`, z.array(moduleSchema));

export const fetchCourses = (moduleId: string) =>
  apiGet(`/api/modules/${moduleId}/courses`, z.array(courseSchema));

export const fetchSourceExams = () => apiGet('/api/source-exams', z.array(sourceExamSchema));

export const fetchLabels = () => apiGet('/api/labels', z.array(labelSchema));

export function fetchQuestionCount(filters: SessionFilters) {
  const params = new URLSearchParams();
  filters.courseIds?.forEach((id) => params.append('courseIds', id));
  if (filters.moduleIds?.[0]) {
    params.set('moduleId', filters.moduleIds[0]);
  }
  if (filters.sourceExamIds?.[0]) {
    params.set('sourceExamId', filters.sourceExamIds[0]);
  }
  if (filters.difficulty) {
    params.set('difficulty', filters.difficulty);
  }
  filters.labelIds?.forEach((id) => params.append('labelIds', id));
  return apiGet(`/api/questions/count?${params.toString()}`, countSchema);
}

// Sessions -------------------------------------------------------------

export const fetchSessions = () =>
  apiGet('/api/sessions?size=50', pageSchema(sessionSummarySchema));

export const createSession = (input: {
  title?: string;
  sessionType: SessionType;
  filters: SessionFilters;
  questionCount: number;
}) => apiPost('/api/sessions', input, sessionSummarySchema);

export const fetchPlay = (sessionId: string) =>
  apiGet(`/api/sessions/${sessionId}/play`, sessionPlaySchema);

export const answerQuestion = (
  sessionId: string,
  questionId: string,
  selectedPropositionIds: string[],
  secondsSpent: number,
) =>
  api
    .put(`/api/sessions/${sessionId}/questions/${questionId}/answer`, {
      selectedPropositionIds,
      secondsSpent,
    })
    .then((r) => correctionSchema.parse(r.data));

export const consultQuestion = (sessionId: string, questionId: string) =>
  apiPost(`/api/sessions/${sessionId}/questions/${questionId}/consult`, undefined, correctionSchema);

export const submitSession = (sessionId: string) =>
  apiPost(`/api/sessions/${sessionId}/submit`, undefined, sessionSummarySchema);

export const patchSession = (
  sessionId: string,
  patch: { title?: string; favorite?: boolean; rating?: number },
): Promise<SessionSummary> =>
  api.patch(`/api/sessions/${sessionId}`, patch).then((r) => sessionSummarySchema.parse(r.data));

export type RepeatMode = 'ALL' | 'WRONG_ONLY' | 'UNANSWERED_ONLY' | 'SAME_FILTERS';

export const repeatSession = (sessionId: string, mode: RepeatMode) =>
  apiPost(`/api/sessions/${sessionId}/repeat`, { mode }, sessionSummarySchema);

export const resetSession = (sessionId: string) =>
  apiPost(`/api/sessions/${sessionId}/reset`, undefined, sessionPlaySchema);

export const deleteSession = (sessionId: string) => api.delete(`/api/sessions/${sessionId}`);

// Stats ----------------------------------------------------------------

export const fetchOverview = () => apiGet('/api/stats/overview', overviewSchema);

export const fetchWeekly = () => apiGet('/api/stats/weekly', z.array(dailyStatsSchema));

// Public marketing -----------------------------------------------------

export const fetchPublicStats = () => apiGet('/api/public/stats', publicStatsSchema);

// Abonnement -----------------------------------------------------------

/** Packs are national: study year is the only filter left. */
export const fetchPacks = (studyYear: number | null) => {
  const params = new URLSearchParams();
  if (studyYear != null) {
    params.set('studyYear', String(studyYear));
  }
  return apiGet(`/api/packs?${params.toString()}`, z.array(publicPackSchema));
};

export const redeemCode = (code: string) =>
  apiPost('/api/codes/redeem', { code }, redeemResponseSchema);

// Stats ----------------------------------------------------------------

export const fetchSessionStats = (type: SessionType | null) =>
  apiGet(`/api/stats/sessions${type ? `?type=${type}` : ''}`, z.array(sessionStatsSchema));

export const fetchCourseStats = (sessionId: string) =>
  apiGet(`/api/stats/sessions/${sessionId}/by-course`, z.array(courseStatsSchema));

/** Lifetime coverage of the caller's own study year, grouped by module. */
export const fetchCourseCoverage = () =>
  apiGet('/api/stats/courses', z.array(moduleCoverageSchema));

// MindMaps -------------------------------------------------------------

export const fetchMindmaps = (courseId: string) =>
  apiGet(`/api/mindmaps?courseId=${courseId}`, z.array(mindmapSchema));

// Labels ---------------------------------------------------------------

export const createLabel = (name: string, color: string) =>
  apiPost('/api/labels', { name, color }, labelSchema);

export const updateLabel = (id: string, name: string, color: string) =>
  api.put(`/api/labels/${id}`, { name, color }).then((r) => labelSchema.parse(r.data));

export const deleteLabel = (id: string) => api.delete(`/api/labels/${id}`);

export const attachLabel = (labelId: string, questionId: string) =>
  api.post(`/api/labels/${labelId}/questions/${questionId}`);

export const detachLabel = (labelId: string, questionId: string) =>
  api.delete(`/api/labels/${labelId}/questions/${questionId}`);

export const fetchLabelQuestions = (labelId: string) =>
  apiGet(`/api/labels/${labelId}/questions`, z.array(questionPlaySchema));

// Notes ----------------------------------------------------------------

export interface NoteInput {
  title: string;
  contentHtml: string;
  questionId?: string;
  courseId?: string;
}

export const fetchNotes = () => apiGet('/api/notes', z.array(noteSchema));

export const createNote = (input: NoteInput) => apiPost('/api/notes', input, noteSchema);

export const updateNote = (id: string, input: NoteInput) =>
  api.put(`/api/notes/${id}`, input).then((r) => noteSchema.parse(r.data));

export const deleteNote = (id: string) => api.delete(`/api/notes/${id}`);

// Signalements ---------------------------------------------------------

export const fetchSignals = () => apiGet('/api/signals', z.array(signalSchema));

export const createSignal = (questionId: string, message: string) =>
  apiPost('/api/signals', { questionId, message }, signalSchema);

// Notifications --------------------------------------------------------

export const fetchUnreadCount = () => apiGet('/api/notifications/unread-count', countSchema);

export const fetchNotifications = () => apiGet('/api/notifications', z.array(notificationSchema));

export const markNotificationRead = (id: string) => api.post(`/api/notifications/${id}/read`);

export const markAllNotificationsRead = () => api.post('/api/notifications/read-all');

export const deleteNotification = (id: string) => api.delete(`/api/notifications/${id}`);

// Support --------------------------------------------------------------

export const submitSupport = (subject: string, body: string) =>
  api.post('/api/support', { subject, body });

// Profil ---------------------------------------------------------------

export interface ProfileUpdate {
  lastName?: string;
  firstName?: string;
  username?: string;
  schoolId?: string;
  studyYear?: number;
  themePrimary?: string;
  themeSecondary?: string;
  themeTertiary?: string;
}

export const updateProfile = (patch: ProfileUpdate) =>
  api.patch('/api/users/me', patch).then((r) => meSchema.parse(r.data));

export const uploadPhoto = (file: File) => {
  const form = new FormData();
  form.append('file', file);
  return api
    .post('/api/users/me/photo', form)
    .then((r) => z.object({ photoUrl: z.string() }).parse(r.data));
};

export const changePassword = (currentPassword: string, newPassword: string) =>
  api.post('/api/users/me/password', { currentPassword, newPassword });

export const deleteAccount = (password: string) =>
  api.delete('/api/users/me', { data: { password } });

// --- Free study library (authenticated, no subscription required) ------
export const fetchResourceYears = () => apiGet('/api/resources/years', z.array(z.number()));

export const fetchResources = (studyYear: number) =>
  apiGet(`/api/resources?studyYear=${studyYear}`, z.array(moduleResourcesSchema));
