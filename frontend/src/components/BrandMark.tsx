interface BrandMarkProps {
  size?: number
}

export function BrandMark({ size = 24 }: BrandMarkProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" style={{ display: 'block' }}>
      <rect x="1" y="1" width="30" height="30" rx="7" fill="var(--brand)" />
      <path d="M9 8 H15.5 A8 8 0 0 1 15.5 24 H9 Z" fill="none" stroke="white" strokeWidth="2.4" strokeLinejoin="round" />
      <path d="M19 11 L23 16 L19 21" fill="none" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
