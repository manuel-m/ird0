/**
 * Safely get the window origin, returning empty string if window is not available (SSR/tests).
 */
export function getWindowOrigin(): string {
  return typeof window !== 'undefined' ? window.location.origin : '';
}
