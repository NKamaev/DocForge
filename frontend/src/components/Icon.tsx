interface IconProps {
  name: string
  size?: number
  className?: string
  style?: React.CSSProperties
}

export function Icon({ name, size = 16, className, style }: IconProps) {
  const common = {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.6,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    className,
    style,
  }
  switch (name) {
    case 'home': return <svg {...common}><path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-7h-6v7H4a1 1 0 0 1-1-1z"/></svg>
    case 'doc': return <svg {...common}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></svg>
    case 'docs': return <svg {...common}><path d="M16 4h-7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h9a2 2 0 0 0 2-2V9z"/><path d="M16 4v5h5"/><path d="M5 8v12a2 2 0 0 0 2 2h8"/></svg>
    case 'templates': return <svg {...common}><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>
    case 'book': return <svg {...common}><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v16H6.5A2.5 2.5 0 0 0 4 20.5z"/><path d="M4 4.5v16A2.5 2.5 0 0 0 6.5 23H20v-3"/></svg>
    case 'settings': return <svg {...common}><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>
    case 'search': return <svg {...common}><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
    case 'plus': return <svg {...common}><path d="M12 5v14M5 12h14"/></svg>
    case 'chev-down': return <svg {...common}><path d="m6 9 6 6 6-6"/></svg>
    case 'chev-right': return <svg {...common}><path d="m9 6 6 6-6 6"/></svg>
    case 'chev-left': return <svg {...common}><path d="m15 6-6 6 6 6"/></svg>
    case 'x': return <svg {...common}><path d="M6 6l12 12M18 6 6 18"/></svg>
    case 'trash': return <svg {...common}><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M10 11v6M14 11v6"/></svg>
    case 'edit': return <svg {...common}><path d="M11 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-6"/><path d="M18.4 2.6a2 2 0 1 1 2.8 2.8L12 14.6l-4 1 1-4z"/></svg>
    case 'save': return <svg {...common}><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><path d="M17 21v-8H7v8M7 3v5h8"/></svg>
    case 'publish': return <svg {...common}><path d="M5 12h14M12 5l7 7-7 7"/></svg>
    case 'history': return <svg {...common}><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><path d="M12 7v5l3 2"/></svg>
    case 'download': return <svg {...common}><path d="M12 3v12M7 10l5 5 5-5"/><path d="M5 21h14"/></svg>
    case 'upload': return <svg {...common}><path d="M12 21V9M7 14l5-5 5 5"/><path d="M5 3h14"/></svg>
    case 'alert': return <svg {...common}><path d="M12 9v4"/><circle cx="12" cy="17" r=".5" fill="currentColor"/><path d="M10.3 3.9 2.6 17a2 2 0 0 0 1.7 3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/></svg>
    case 'info': return <svg {...common}><circle cx="12" cy="12" r="9"/><path d="M12 8v.5M12 11v5"/></svg>
    case 'check': return <svg {...common}><path d="m5 12 5 5L20 7"/></svg>
    case 'more': return <svg {...common}><circle cx="12" cy="5" r="1.2" fill="currentColor"/><circle cx="12" cy="12" r="1.2" fill="currentColor"/><circle cx="12" cy="19" r="1.2" fill="currentColor"/></svg>
    case 'more-h': return <svg {...common}><circle cx="5" cy="12" r="1.2" fill="currentColor"/><circle cx="12" cy="12" r="1.2" fill="currentColor"/><circle cx="19" cy="12" r="1.2" fill="currentColor"/></svg>
    case 'user': return <svg {...common}><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>
    case 'users': return <svg {...common}><circle cx="9" cy="8" r="4"/><path d="M2 21a7 7 0 0 1 14 0"/><path d="M16 4a4 4 0 0 1 0 8"/><path d="M22 21a7 7 0 0 0-5-6.7"/></svg>
    case 'lock': return <svg {...common}><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 1 1 8 0v4"/></svg>
    case 'logout': return <svg {...common}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/></svg>
    case 'bold': return <svg {...common}><path d="M7 4h6a4 4 0 0 1 0 8H7zM7 12h7a4 4 0 0 1 0 8H7z"/></svg>
    case 'italic': return <svg {...common}><path d="M19 4h-9M14 20H5M15 4l-6 16"/></svg>
    case 'under': return <svg {...common}><path d="M6 4v8a6 6 0 1 0 12 0V4M5 21h14"/></svg>
    case 'strike': return <svg {...common}><path d="M5 12h14"/><path d="M16 6a4 4 0 0 0-4-2c-3 0-5 2-5 4 0 3 4 4 6 4M8 18a4 4 0 0 0 4 2c3 0 5-2 5-4"/></svg>
    case 'align-l': return <svg {...common}><path d="M3 6h18M3 12h12M3 18h18"/></svg>
    case 'align-c': return <svg {...common}><path d="M3 6h18M6 12h12M3 18h18"/></svg>
    case 'align-r': return <svg {...common}><path d="M3 6h18M9 12h12M3 18h18"/></svg>
    case 'list-ul': return <svg {...common}><circle cx="4" cy="6" r="1.2" fill="currentColor"/><circle cx="4" cy="12" r="1.2" fill="currentColor"/><circle cx="4" cy="18" r="1.2" fill="currentColor"/><path d="M9 6h12M9 12h12M9 18h12"/></svg>
    case 'list-ol': return <svg {...common}><path d="M9 6h12M9 12h12M9 18h12"/><path d="M3 6h2M4 6V4M3 18h3M3 14h3l-3 4h3M3 12h2"/></svg>
    case 'table': return <svg {...common}><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M3 16h18M9 4v16M15 4v16"/></svg>
    case 'image': return <svg {...common}><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 16-5-5L8 19"/></svg>
    case 'link': return <svg {...common}><path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1"/><path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1"/></svg>
    case 'code': return <svg {...common}><path d="m8 8-5 4 5 4M16 8l5 4-5 4M14 4l-4 16"/></svg>
    case 'quote': return <svg {...common}><path d="M5 11h4v6H3v-3a4 4 0 0 1 2-3zM15 11h4v6h-6v-3a4 4 0 0 1 2-3z"/></svg>
    case 'param': return <svg {...common}><path d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"/></svg>
    case 'branch': return <svg {...common}><circle cx="6" cy="5" r="2"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="12" r="2"/><path d="M6 7v10M8 12h8"/></svg>
    case 'hash': return <svg {...common}><path d="M5 9h14M5 15h14M10 3 8 21M16 3l-2 18"/></svg>
    case 'highlight': return <svg {...common}><path d="m9 11 4 4M5 21h14M14 4l6 6-9 9-4 1 1-4z"/></svg>
    case 'fold': return <svg {...common}><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/><path d="M14 9l-2 3 2 3"/></svg>
    case 'folder': return <svg {...common}><path d="M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>
    case 'folder-plus': return <svg {...common}><path d="M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M12 12v5M9.5 14.5h5"/></svg>
    case 'filter': return <svg {...common}><path d="M3 5h18l-7 9v7l-4-2v-5z"/></svg>
    case 'calendar': return <svg {...common}><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>
    case 'sparkle': return <svg {...common}><path d="M12 3v6M12 15v6M3 12h6M15 12h6"/></svg>
    case 'indent-r': return <svg {...common}><path d="M3 6h18M11 12h10M11 18h10M3 12l4 4V8z"/></svg>
    case 'indent-l': return <svg {...common}><path d="M3 6h18M3 12h10M3 18h10M21 12l-4 4V8z"/></svg>
    case 'page-break': return <svg {...common}><path d="M5 4h14v6H5zM5 14h14v6H5z" strokeDasharray="3 3"/><path d="M3 12h2m4 0h2m4 0h2m4 0h2"/></svg>
    case 'spacing': return <svg {...common}><path d="M3 6h6m6 0h6M3 12h18M3 18h6m6 0h6"/></svg>
    default: return <svg {...common}><circle cx="12" cy="12" r="9"/></svg>
  }
}
