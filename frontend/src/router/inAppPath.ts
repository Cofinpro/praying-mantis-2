/**
 * True for a path inside this app, such as "/approvals" or "/absences?request=42". False for
 * anything that could leave it: a full URL, or "//evil.com" and "/\evil.com", which browsers treat
 * as another host (open redirect). Used for `?redirect=` on login and for notification links.
 */
export function isInAppPath(target: unknown): target is string {
  return typeof target === 'string' && target.startsWith('/') && !/^\/[/\\]/.test(target)
}
