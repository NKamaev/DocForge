import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui'
import type { CollaborationUser } from '../../hooks/useCollaboration'

interface Props {
  isConnected: boolean
  connectedUsers: CollaborationUser[]
  currentUserName?: string
  currentUserColor?: string
}

/**
 * Shows a compact strip of avatar bubbles for all connected participants.
 * Green dot = online indicator on the current user's avatar.
 */
export default function ParticipantsPanel({
  isConnected,
  connectedUsers,
  currentUserName,
  currentUserColor,
}: Props) {
  return (
    <TooltipProvider>
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
      {/* Current user */}
      {currentUserName && (
        <Tooltip>
          <TooltipTrigger asChild>
            <div style={{ position: 'relative' }}>
              <Avatar name={currentUserName} color={currentUserColor ?? '#888'} />
              {/* Online indicator */}
              <span style={{
                position: 'absolute',
                bottom: 0,
                right: 0,
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: isConnected ? '#52c41a' : '#bfbfbf',
                border: '1.5px solid #fff',
              }} />
            </div>
          </TooltipTrigger>
          <TooltipContent>{currentUserName} (вы)</TooltipContent>
        </Tooltip>
      )}

      {/* Remote users — max 3 visible */}
      {connectedUsers.slice(0, 3).map(u => (
        <Tooltip key={u.clientId}>
          <TooltipTrigger asChild>
            <div style={{ position: 'relative' }}>
              <Avatar name={u.name} color={u.color} />
              <span style={{
                position: 'absolute',
                bottom: 0,
                right: 0,
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: '#52c41a',
                border: '1.5px solid #fff',
              }} />
            </div>
          </TooltipTrigger>
          <TooltipContent>{u.name}</TooltipContent>
        </Tooltip>
      ))}

      {/* Overflow badge: "+N" with tooltip listing hidden users */}
      {connectedUsers.length > 3 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <div style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              background: '#8c8c8c',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 11,
              fontWeight: 600,
              flexShrink: 0,
              border: '2px solid #fff',
              boxShadow: '0 1px 3px rgba(0,0,0,.2)',
              cursor: 'default',
              userSelect: 'none',
            }}>
              +{connectedUsers.length - 3}
            </div>
          </TooltipTrigger>
          <TooltipContent>{connectedUsers.slice(3).map(u => u.name).join(', ')}</TooltipContent>
        </Tooltip>
      )}

      {/* Connection status text when disconnected */}
      {!isConnected && connectedUsers.length === 0 && (
        <span style={{ fontSize: 11, color: '#8c8c8c' }}>офлайн</span>
      )}
    </div>
    </TooltipProvider>
  )
}

function Avatar({ name, color }: { name: string; color: string }) {
  const initials = name
    .split(/\s+/)
    .slice(0, 2)
    .map(w => w[0]?.toUpperCase() ?? '')
    .join('')

  return (
    <div style={{
      width: 28,
      height: 28,
      borderRadius: '50%',
      background: color,
      color: '#fff',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: 11,
      fontWeight: 600,
      flexShrink: 0,
      border: '2px solid #fff',
      boxShadow: '0 1px 3px rgba(0,0,0,.2)',
      cursor: 'default',
      userSelect: 'none',
    }}>
      {initials || '?'}
    </div>
  )
}
