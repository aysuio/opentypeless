import { describe, expect, it, vi } from 'vitest'
import { requireUpstreamCloud, UPSTREAM_CLOUD_ENABLED } from '../privacy'
import { getSubscriptionStatus } from '../api'

describe('personal distribution network boundary', () => {
  it('rejects account requests before any fetch can run', () => {
    const fetch = vi.fn()
    expect(() => {
      requireUpstreamCloud()
      fetch()
    }).toThrow('disabled')
    expect(fetch).not.toHaveBeenCalled()
    expect(UPSTREAM_CLOUD_ENABLED).toBe(false)
  })
  it('blocks the real account API wrapper before opening a connection', async () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    try {
      await expect(getSubscriptionStatus()).rejects.toThrow('disabled')
      expect(fetch).not.toHaveBeenCalled()
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
