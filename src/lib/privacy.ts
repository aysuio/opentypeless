/** This distribution uses direct provider keys, without an upstream account. */
export const UPSTREAM_CLOUD_ENABLED = false
export function requireUpstreamCloud(): void {
  if (!UPSTREAM_CLOUD_ENABLED) throw new Error('Upstream account service is disabled in this build')
}
