export interface ColorSettings {
    center: number
    width: number
    amount: number
    distribution: number
}

const generateColorFor = (hue: number): string => {
    const lightness = 50
    return `hsl(${hue}, 100%, ${lightness}%)`
}

const hashString = (str: string, distribution: number): number => {
    let hash = 0
    for (let i = 0; i < str.length; i++) {
        hash = (hash << 5) - hash + str.charCodeAt(i)
        hash |= 0
    }
    return (Math.abs(hash) % distribution) / distribution
}

const getParts = (name: string): string[] => {
    // Strip method call and line number if present
    let cleanName = name
    const parenIdx = name.indexOf("(")
    if (parenIdx !== -1) {
        cleanName = name.substring(0, parenIdx)
    } else if (name.endsWith("_[j]")) {
        cleanName = name.substring(0, name.length - 4)
    }

    return cleanName
        .split(/[./:]/)
        .map((x) =>
            x.indexOf("$") !== -1 ? x.substring(0, x.indexOf("$")) : x,
        )
        .filter((p) => p.length > 0)
}

/**
 * Given a string, deterministically generate a color.
 */
export const colorFor = (str: string, colorContext: ColorSettings): string => {
    const { center, width, amount, distribution } = colorContext
    const parts = getParts(str)

    let hueAccumulator = 0
    let currentDivisor = 1
    for (let i = 0; i < parts.length; i++) {
        // Shift hash from [0, 1] to [-0.5, 0.5] to center it around the 'center' hue.
        hueAccumulator +=
            (hashString(parts[i]!, distribution) - 0.5) *
            (width / currentDivisor)
        currentDivisor *= amount
    }

    // Ensure the hue is always positive before modulo.
    const hue = (center + hueAccumulator + 3600) % 360
    return generateColorFor(hue)
}
