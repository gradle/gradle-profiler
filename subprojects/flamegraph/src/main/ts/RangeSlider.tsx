import React, { useCallback, useRef } from "react"

/**
 * A custom multi-range slider with two handles for 'min' and 'max' selection.
 */
export const RangeSlider: React.FC<{
    min: number
    max: number
    valueLeft: number
    valueRight: number
    onChange: (left: number, right: number) => void
}> = ({ min, max, valueLeft, valueRight, onChange }) => {
    const trackRef = useRef<HTMLDivElement>(null)

    const handleMouseDown = useCallback(
        (isRight: boolean) => (e: React.MouseEvent) => {
            e.preventDefault()

            const onMouseMove = (moveEvent: MouseEvent) => {
                if (!trackRef.current) return
                const rect = trackRef.current.getBoundingClientRect()
                const percent = (moveEvent.clientX - rect.left) / rect.width
                const newValue = Math.round(
                    min + Math.max(0, Math.min(1, percent)) * (max - min),
                )

                if (isRight) {
                    if (newValue > valueLeft) {
                        onChange(valueLeft, newValue)
                    }
                } else {
                    if (newValue < valueRight) {
                        onChange(newValue, valueRight)
                    }
                }
            }

            const onMouseUp = () => {
                document.removeEventListener("mousemove", onMouseMove)
                document.removeEventListener("mouseup", onMouseUp)
            }

            document.addEventListener("mousemove", onMouseMove)
            document.addEventListener("mouseup", onMouseUp)
        },
        [min, max, valueLeft, valueRight, onChange],
    )

    const handleTrackMouseDown = useCallback(
        (e: React.MouseEvent) => {
            e.preventDefault()
            if (!trackRef.current) return

            const startX = e.clientX
            const startLeft = valueLeft
            const startRight = valueRight
            const range = startRight - startLeft

            const onMouseMove = (moveEvent: MouseEvent) => {
                if (!trackRef.current) return
                const rect = trackRef.current.getBoundingClientRect()
                const deltaPercent = (moveEvent.clientX - startX) / rect.width
                const deltaValue = Math.round(deltaPercent * (max - min))

                let newLeft = startLeft + deltaValue
                let newRight = startRight + deltaValue

                if (newLeft < min) {
                    newLeft = min
                    newRight = min + range
                }
                if (newRight > max) {
                    newRight = max
                    newLeft = max - range
                }

                onChange(newLeft, newRight)
            }

            const onMouseUp = () => {
                document.removeEventListener("mousemove", onMouseMove)
                document.removeEventListener("mouseup", onMouseUp)
            }

            document.addEventListener("mousemove", onMouseMove)
            document.addEventListener("mouseup", onMouseUp)
        },
        [min, max, valueLeft, valueRight, onChange],
    )

    const leftPos = ((valueLeft - min) / (max - min)) * 100
    const rightPos = ((valueRight - min) / (max - min)) * 100

    return (
        <div
            ref={trackRef}
            style={{
                height: "6px",
                background: "#444",
                position: "relative",
                flexGrow: 1,
                borderRadius: "3px",
                margin: "0 10px",
                cursor: "pointer",
            }}
        >
            <div
                onMouseDown={handleTrackMouseDown}
                style={{
                    position: "absolute",
                    left: `${leftPos}%`,
                    right: `${100 - rightPos}%`,
                    height: "100%",
                    background: "#007bff",
                    borderRadius: "3px",
                    cursor: "grab",
                }}
            />
            <div
                onMouseDown={handleMouseDown(false)}
                style={{
                    position: "absolute",
                    left: `${leftPos}%`,
                    width: "16px",
                    height: "16px",
                    borderRadius: "50%",
                    background: "#fff",
                    top: "50%",
                    transform: "translate(-50%, -50%)",
                    cursor: "grab",
                    boxShadow: "0 2px 4px rgba(0,0,0,0.5)",
                    zIndex: 2,
                }}
            />
            <div
                onMouseDown={handleMouseDown(true)}
                style={{
                    position: "absolute",
                    left: `${rightPos}%`,
                    width: "16px",
                    height: "16px",
                    borderRadius: "50%",
                    background: "#fff",
                    top: "50%",
                    transform: "translate(-50%, -50%)",
                    cursor: "grab",
                    boxShadow: "0 2px 4px rgba(0,0,0,0.5)",
                    zIndex: 2,
                }}
            />
        </div>
    )
}
