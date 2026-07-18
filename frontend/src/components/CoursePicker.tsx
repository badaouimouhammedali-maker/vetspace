import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Select } from './forms';
import { t } from '../i18n/fr';
import { fetchCourses, fetchModules } from '../lib/endpoints';

/** Sélecteur module → cours en cascade. Remonte l'id du cours choisi (ou null). */
export function CoursePicker({
  courseId,
  onCourse,
  includeAllOption = false,
}: {
  courseId: string;
  onCourse: (id: string) => void;
  includeAllOption?: boolean;
}) {
  const { user } = useAuth();
  const [moduleId, setModuleId] = useState('');
  const studyYear = user?.studyYear ?? 1;

  const modules = useQuery({
    queryKey: ['modules', studyYear],
    queryFn: () => fetchModules(studyYear),
  });
  const courses = useQuery({
    queryKey: ['courses', moduleId],
    queryFn: () => fetchCourses(moduleId),
    enabled: moduleId !== '',
  });

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <Select
        aria-label={t('builder.module')}
        value={moduleId}
        onChange={(e) => {
          setModuleId(e.target.value);
          onCourse('');
        }}
      >
        <option value="">{t('builder.modulePlaceholder')}</option>
        {(modules.data ?? []).map((m) => (
          <option key={m.id} value={m.id}>
            {m.name}
          </option>
        ))}
      </Select>
      <Select
        aria-label={t('builder.courses')}
        value={courseId}
        disabled={!moduleId}
        onChange={(e) => onCourse(e.target.value)}
      >
        <option value="">
          {moduleId ? t('mindmaps.selectCourse') : t('mindmaps.selectModuleFirst')}
        </option>
        {includeAllOption && moduleId ? <option value="__none__">—</option> : null}
        {(courses.data ?? []).map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </Select>
    </div>
  );
}
