import { useEffect, useMemo, useState } from 'react'
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { useAuthStore } from '../store/authStore'

export interface CollaborationUser {
  clientId: number
  name: string
  color: string
}

export interface CollaborationState {
  ydoc: Y.Doc
  provider: WebsocketProvider | null
  isConnected: boolean
  connectedUsers: CollaborationUser[]
  yAnswers: Y.Map<unknown>
}

/**
 * Sets up a Yjs Doc and y-websocket provider for real-time collaboration.
 *
 * Auth: JWT is passed as ?token= query param (browsers can't set WS headers).
 * URL : ws://localhost:8080/ws/collab/{documentId}?token={JWT}
 *
 * Returns a stable { ydoc, provider, isConnected, connectedUsers } object.
 * Provider is created in useEffect (not useMemo) to avoid React StrictMode
 * double-invocation creating two WebSocket connections, one leaked and one
 * used — causing the editor ydoc and the WS provider to diverge.
 */
export function useCollaboration(documentId: string): CollaborationState {
  const token = useAuthStore(s => s.token)
  const user  = useAuthStore(s => s.user)

  // Stable ydoc per documentId. Y.Doc holds no system resources so we skip
  // destroy() to avoid StrictMode double-mount leaving the Collaboration
  // extension bound to a dead doc and silently dropping all user edits.
  const ydoc = useMemo(() => new Y.Doc(), [documentId])
  const yAnswers = useMemo(() => ydoc.getMap<unknown>('answers'), [ydoc])

  const [provider, setProvider] = useState<WebsocketProvider | null>(null)
  const [isConnected, setIsConnected]       = useState(false)
  const [connectedUsers, setConnectedUsers] = useState<CollaborationUser[]>([])

  // Create provider in useEffect so StrictMode cleanup → remount properly
  // destroys and recreates the WebSocket instead of reusing a destroyed one.
  useEffect(() => {
    const serverUrl = buildWsUrl()
    const room      = `ws/collab/${documentId}`
    const p = new WebsocketProvider(serverUrl, room, ydoc, {
      connect: true,
      params: token ? { token } : undefined,
    })
    setProvider(p)

    const onStatus = ({ status }: { status: string }) =>
      setIsConnected(status === 'connected')
    p.on('status', onStatus)
    // 'sync' fires after initial sync — reliable secondary signal that WS is up
    const onSync = (synced: boolean) => { if (synced) setIsConnected(true) }
    p.on('sync', onSync)
    setIsConnected(p.wsconnected)

    const updateUsers = () => {
      const states = p.awareness.getStates()
      const users: CollaborationUser[] = []
      states.forEach((state, clientId) => {
        if (clientId !== p.awareness.clientID && state.user) {
          users.push({ clientId, ...state.user as { name: string; color: string } })
        }
      })
      setConnectedUsers(deduplicateByName(users))
    }
    p.awareness.on('change', updateUsers)
    updateUsers()

    return () => {
      p.off('status', onStatus)
      p.off('sync', onSync)
      p.awareness.off('change', updateUsers)
      p.destroy()
      setProvider(null)
      setIsConnected(false)
      setConnectedUsers([])
    }
  }, [documentId, token, ydoc])

  // Set local awareness user info once provider is ready or user changes
  useEffect(() => {
    if (!user || !provider) return
    provider.awareness.setLocalStateField('user', {
      name:  user.displayName || user.login,
      color: loginToColor(user.login),
    })
  }, [user, provider])

  return { ydoc, provider, isConnected, connectedUsers, yAnswers }
}

/** Deduplicate users by name, keeping the entry with the highest clientId. */
export function deduplicateByName(users: CollaborationUser[]): CollaborationUser[] {
  const seen = new Map<string, CollaborationUser>()
  for (const u of users) {
    const existing = seen.get(u.name)
    if (!existing || u.clientId > existing.clientId) seen.set(u.name, u)
  }
  return Array.from(seen.values())
}

/** Resolve WebSocket server URL from env var or current window location. */
export function buildWsUrl(): string {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL as string
  // In dev mode connect directly to backend (port 8080) instead of proxying
  // through Vite (port 5173) — Vite's built-in WS can conflict with the proxy.
  if (import.meta.env.DEV) return 'ws://localhost:8080'
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${proto}://${window.location.host}`
}

/** Deterministic HSL color from a login string (hash → hue). */
function loginToColor(login: string): string {
  let hash = 0
  for (let i = 0; i < login.length; i++) {
    hash = login.charCodeAt(i) + ((hash << 5) - hash)
  }
  const hue = Math.abs(hash) % 360
  return `hsl(${hue}, 65%, 45%)`
}
