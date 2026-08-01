import type { CourseCoverage } from '../../lib/schemas';

/**
 * Ordering rules for /app/suivi.
 *
 * <p>Its own module rather than an export from `SuiviPage.tsx`: that file is loaded
 * through `React.lazy` + the `named()` helper, which types every export as a component,
 * and exporting a plain function from it breaks both the typecheck and react-refresh's
 * component-only rule. Pure logic living apart from the component is also what makes it
 * unit-testable without rendering anything.
 */

/** The three ways a student wants to read the coverage screen. */
export type SortMode = 'default' | 'weakest' | 'leastCovered';

/** Courses with nothing answered have no meaningful précision; they sort last, not first. */
export function compare(mode: SortMode, a: CourseCoverage, b: CourseCoverage): number {
  if (mode === 'weakest') {
    if (a.answeredQuestions === 0 || b.answeredQuestions === 0) {
      return a.answeredQuestions === b.answeredQuestions ? 0 : a.answeredQuestions === 0 ? 1 : -1;
    }
    return a.precisionPercent - b.precisionPercent;
  }
  // Least covered: by the fraction seen, so a 5-question course is not permanently
  // "better covered" than a 200-question one just for being small. Empty courses have no
  // fraction at all and go last.
  const ratio = (c: CourseCoverage) =>
    c.totalQuestions === 0 ? Number.POSITIVE_INFINITY : c.seenQuestions / c.totalQuestions;
  return ratio(a) - ratio(b);
}
