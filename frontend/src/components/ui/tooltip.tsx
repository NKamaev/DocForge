import * as React from "react"
import { Tooltip as TooltipPrimitive } from "radix-ui"

const tooltipStyle: React.CSSProperties = {
  zIndex: 50,
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  maxWidth: 320,
  borderRadius: 6,
  background: 'var(--ink-1)',
  color: 'white',
  padding: '5px 10px',
  fontSize: 11,
  lineHeight: 1.4,
  fontFamily: 'var(--font-ui)',
  boxShadow: 'var(--shadow-pop)',
  border: '1px solid oklch(from var(--ink-1) l c h / 0.5)',
}

function TooltipProvider({
  delayDuration = 0,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return (
    <TooltipPrimitive.Provider
      data-slot="tooltip-provider"
      delayDuration={delayDuration}
      {...props}
    />
  )
}

function Tooltip({
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Root>) {
  return <TooltipPrimitive.Root data-slot="tooltip" {...props} />
}

const TooltipTrigger = React.forwardRef<
  React.ElementRef<typeof TooltipPrimitive.Trigger>,
  React.ComponentPropsWithoutRef<typeof TooltipPrimitive.Trigger>
>((props, ref) => {
  return <TooltipPrimitive.Trigger ref={ref} data-slot="tooltip-trigger" {...props} />
})

function TooltipContent({
  className,
  sideOffset = 4,
  children,
  style,
  ...props
}: React.ComponentProps<typeof TooltipPrimitive.Content>) {
  return (
    <TooltipPrimitive.Portal>
      <TooltipPrimitive.Content
        data-slot="tooltip-content"
        sideOffset={sideOffset}
        style={{ ...tooltipStyle, ...style }}
        className={
          "data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2 data-[state=delayed-open]:animate-in data-[state=delayed-open]:fade-in-0 data-[state=delayed-open]:zoom-in-95 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95" +
          (className ? " " + className : "")
        }
        {...props}
      >
        {children}
        <TooltipPrimitive.Arrow
          style={{
            fill: 'var(--ink-1)',
            zIndex: 50,
          }}
          width={10}
          height={5}
        />
      </TooltipPrimitive.Content>
    </TooltipPrimitive.Portal>
  )
}

export { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger }
