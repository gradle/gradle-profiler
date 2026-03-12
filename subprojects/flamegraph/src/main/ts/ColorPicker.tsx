import React from "react"
import type { ColorSettings } from "./color"
import { Row, Stack } from "./containers"

const Slider: React.FC<{
    min: number
    max: number
    value: number
    onChange: (newValue: number) => void
}> = ({ min, max, value, onChange }) => {
    const resolution = 10000
    const percent = (value - min) / (max - min)
    const scaledValue = percent * resolution

    return (
        <input
            style={{ flexGrow: 1 }}
            type="range"
            min={0}
            max={resolution}
            value={scaledValue}
            onChange={(e) => {
                const v = parseInt(e.target.value, 10)
                onChange(min + (v / resolution) * (max - min))
            }}
        />
    )
}

export const ColorPicker: React.FC<{
    colorSettings: ColorSettings
    onColorChange: (settings: ColorSettings) => void
}> = ({ colorSettings, onColorChange }) => {
    const { center, width, amount, distribution } = colorSettings
    const update = (patch: Partial<ColorSettings>) =>
        onColorChange({ ...colorSettings, ...patch })

    return (
        <Stack style={{ width: 450 }}>
            <Row>
                Center ({Math.round(center)})
                <Slider
                    min={0}
                    max={360}
                    value={center}
                    onChange={(v) => update({ center: v })}
                />
            </Row>
            <Row>
                Width ({Math.round(width)})
                <Slider
                    min={0}
                    max={360}
                    value={width}
                    onChange={(v) => update({ width: v })}
                />
            </Row>
            <Row>
                Decay ({amount.toFixed(2)})
                <Slider
                    min={1}
                    max={3}
                    value={amount}
                    onChange={(v) => update({ amount: v })}
                />
            </Row>
            <Row>
                Spread ({Math.round(distribution)})
                <Slider
                    min={1}
                    max={5000}
                    value={distribution}
                    onChange={(v) => update({ distribution: v })}
                />
            </Row>
        </Stack>
    )
}
