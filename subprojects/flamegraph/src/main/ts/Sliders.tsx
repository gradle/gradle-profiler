import React, { useCallback, useRef } from "react"

const TRACK_HEIGHT = 6
const TRACK_RADIUS = 3
const TRACK_BG = "#555"
const TRACK_FILL = "#ffffff"
const TRACK_MARGIN = "0 10px"

const thumbStyle: React.CSSProperties = {
    position: "absolute",
    width: 9,
    height: 16,
    borderRadius: 4,
    background: TRACK_FILL,
    top: "50%",
    transform: "translate(-50%, -50%)",
    cursor: "grab",
    boxShadow: "0 2px 4px rgba(0,0,0,0.5)",
    zIndex: 2,
}

const trackStyle = (disabled?: boolean): React.CSSProperties => ({
    height: TRACK_HEIGHT,
    background: TRACK_BG,
    position: "relative",
    flexGrow: 1,
    alignSelf: "center",
    borderRadius: TRACK_RADIUS,
    margin: TRACK_MARGIN,
    cursor: disabled ? "default" : "pointer",
    pointerEvents: disabled ? "none" : undefined,
    opacity: disabled ? 0.4 : undefined,
})

export const SingleSlider: React.FC<{
    min: number
    max: number
    value: number
    onChange: (newValue: number) => void
}> = ({ min, max, value, onChange }) => {
    const trackRef = useRef<HTMLDivElement>(null)

    const valueFromClientX = (clientX: number): number => {
        if (!trackRef.current) return value
        const rect = trackRef.current.getBoundingClientRect()
        const percent = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width))
        return min + percent * (max - min)
    }

    const handleMouseDown = useCallback(
        (e: React.MouseEvent) => {
            e.preventDefault()
            onChange(valueFromClientX(e.clientX))

            const onMouseMove = (moveEvent: MouseEvent) => {
                onChange(valueFromClientX(moveEvent.clientX))
            }
            const onMouseUp = () => {
                document.removeEventListener("mousemove", onMouseMove)
                document.removeEventListener("mouseup", onMouseUp)
            }
            document.addEventListener("mousemove", onMouseMove)
            document.addEventListener("mouseup", onMouseUp)
        },
        // valueFromClientX closes over trackRef (stable ref) and min/max
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [min, max, onChange],
    )

    const percent = ((value - min) / (max - min)) * 100

    return (
        <div ref={trackRef} onMouseDown={handleMouseDown} style={trackStyle()}>
            <div
                style={{
                    position: "absolute",
                    left: 0,
                    width: `${percent}%`,
                    height: "100%",
                    background: TRACK_FILL,
                    borderRadius: TRACK_RADIUS,
                    pointerEvents: "none",
                }}
            />
            <div style={{ ...thumbStyle, left: `${percent}%`, pointerEvents: "none" }} />
        </div>
    )
}

/**
 * A custom two-thumb range slider.
 * The selected range is shown in white; the unselected track is #555.
 */
export const RangeSlider: React.FC<{
    min: number
    max: number
    valueLeft: number
    valueRight: number
    onChange: (left: number, right: number) => void
    disabled?: boolean
}> = ({ min, max, valueLeft, valueRight, onChange, disabled }) => {
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
                    if (newValue > valueLeft) onChange(valueLeft, newValue)
                } else {
                    if (newValue < valueRight) onChange(newValue, valueRight)
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

                if (newLeft < min) { newLeft = min; newRight = min + range }
                if (newRight > max) { newRight = max; newLeft = max - range }

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
        <div ref={trackRef} style={trackStyle(disabled)}>
            {/* Selected range fill */}
            <div
                onMouseDown={handleTrackMouseDown}
                style={{
                    position: "absolute",
                    left: `${leftPos}%`,
                    right: `${100 - rightPos}%`,
                    height: "100%",
                    background: TRACK_FILL,
                    borderRadius: TRACK_RADIUS,
                    cursor: "grab",
                }}
            />
            {/* Left thumb */}
            <div onMouseDown={handleMouseDown(false)} style={{ ...thumbStyle, left: `${leftPos}%` }} />
            {/* Right thumb */}
            <div onMouseDown={handleMouseDown(true)} style={{ ...thumbStyle, left: `${rightPos}%` }} />
        </div>
    )
}
