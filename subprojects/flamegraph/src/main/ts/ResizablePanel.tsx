import React, { useCallback, useRef, useState } from "react"
import { Stack } from "./containers"

function useResizable(initialWidth: number, min = 150) {
    const [width, setWidth] = useState(initialWidth)
    const containerRef = useRef<HTMLDivElement | null>(null)

    const startDrag = useCallback(
        (e: React.MouseEvent, direction: "from-right" | "from-left") => {
            e.preventDefault()
            const rect = containerRef.current?.getBoundingClientRect()
            const fixedEdge =
                direction === "from-right"
                    ? (rect?.left ?? e.clientX)
                    : (rect?.right ?? e.clientX)

            const handleMouseMove = (moveEvent: MouseEvent) => {
                const newWidth =
                    direction === "from-right"
                        ? moveEvent.clientX - fixedEdge
                        : fixedEdge - moveEvent.clientX
                setWidth(Math.max(min, newWidth))
            }

            const handleMouseUp = () => {
                document.removeEventListener("mousemove", handleMouseMove)
                document.removeEventListener("mouseup", handleMouseUp)
            }

            document.addEventListener("mousemove", handleMouseMove)
            document.addEventListener("mouseup", handleMouseUp)
        },
        [min],
    )

    return { width, startDrag, containerRef }
}

/**
 * A panel with a draggable edge handle.
 *
 * Structure:
 *  - Outer invisible div: tracks the dynamic width; gets a definite height from
 *    the `style` prop (e.g. `alignSelf: stretch` or `flex: 1; minHeight: 0`).
 *  - Inner visible Stack: content-driven height, capped at `max-height: 100%` of
 *    the outer div when open. Acts as a flex column so children can use
 *    `flex: 1; minHeight: 0` for internal scrolling.
 *    Shows an inset box-shadow on the drag edge while hovered.
 *  - Drag handle: thin invisible strip inside the inner Stack, so the hover
 *    border and drag affordance span only the visible content area.
 *
 * @param edge       Which side the drag handle is on.
 * @param open       When false: no width constraint, no height cap, no handle.
 * @param style      Extra styles for the outer invisible container (maxWidth, alignSelf, flex…).
 * @param panelStyle Styles for the inner visible Stack (background, padding, gap, pointerEvents…).
 */
export const ResizablePanel: React.FC<{
    edge: "left" | "right"
    open: boolean
    initialWidth: number
    minWidth?: number
    style?: React.CSSProperties
    panelStyle?: React.CSSProperties
    children: React.ReactNode
}> = ({ edge, open, initialWidth, minWidth, style, panelStyle, children }) => {
    const { width, startDrag, containerRef } = useResizable(initialWidth, minWidth)
    const [hovered, setHovered] = useState(false)

    const boxShadow =
        hovered && open
            ? edge === "left"
                ? "inset 2px 0 0 rgba(128, 128, 128, 0.5)"
                : "inset -2px 0 0 rgba(128, 128, 128, 0.5)"
            : undefined

    return (
        <div
            ref={containerRef}
            style={{
                position: "relative",
                width: open ? width : undefined,
                ...style,
            }}
        >
            {/* Inner visible Stack: content-driven height, capped at the outer
                div's height. overflow: hidden + max-height: 100% together make
                this a definite-height flex container so children can use flex: 1. */}
            <Stack
                style={{
                    position: "relative",
                    maxHeight: open ? "100%" : undefined,
                    overflow: open ? "hidden" : undefined,
                    boxShadow,
                    ...panelStyle,
                }}
            >
                {children}
                {open && (
                    <div
                        style={{
                            position: "absolute",
                            [edge]: -4,
                            top: 0,
                            bottom: 0,
                            width: 8,
                            cursor: "ew-resize",
                            pointerEvents: "auto",
                            zIndex: 1,
                        }}
                        onMouseDown={(e) => {
                            e.preventDefault()
                            startDrag(e, edge === "right" ? "from-right" : "from-left")
                        }}
                        onMouseEnter={() => setHovered(true)}
                        onMouseLeave={() => setHovered(false)}
                    />
                )}
            </Stack>
        </div>
    )
}
