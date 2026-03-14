import React from "react"
import type { ColorSettings } from "./color"
import { Row, Stack } from "./containers"
import { SingleSlider } from "./Sliders"

export const ColorPicker: React.FC<{
    colorSettings: ColorSettings
    onColorChange: (settings: ColorSettings) => void
}> = ({ colorSettings, onColorChange }) => {
    const { center, width, amount, distribution } = colorSettings
    const update = (patch: Partial<ColorSettings>) =>
        onColorChange({ ...colorSettings, ...patch })

    return (
        <Stack wide>
            <Row>
                Center ({Math.round(center)})
                <SingleSlider
                    min={0}
                    max={360}
                    value={center}
                    onChange={(v) => update({ center: v })}
                />
            </Row>
            <Row>
                Width ({Math.round(width)})
                <SingleSlider
                    min={0}
                    max={360}
                    value={width}
                    onChange={(v) => update({ width: v })}
                />
            </Row>
            <Row>
                Decay ({amount.toFixed(2)})
                <SingleSlider
                    min={1}
                    max={3}
                    value={amount}
                    onChange={(v) => update({ amount: v })}
                />
            </Row>
            <Row>
                Spread ({Math.round(distribution)})
                <SingleSlider
                    min={1}
                    max={5000}
                    value={distribution}
                    onChange={(v) => update({ distribution: v })}
                />
            </Row>
        </Stack>
    )
}
