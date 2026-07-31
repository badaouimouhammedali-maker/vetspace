/**
 * Bytes as something a human reads before tapping download on a mobile plan.
 *
 * <p>Its own module rather than an export from the page that uses it: a file that
 * exports both components and plain functions loses React Fast Refresh.
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} o`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} Ko`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
}
