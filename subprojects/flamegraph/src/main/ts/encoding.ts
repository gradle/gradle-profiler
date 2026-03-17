import { gzipSync } from "fflate"

/**
 * Converts a Uint8Array into a Base64 encoded string.
 * This is the reverse of `base64ToUint8Array`.
 */
const uint8ArrayToBase64 = (bytes: Uint8Array): string => {
    let binary = ""
    const len = bytes.byteLength
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]!)
    }
    return btoa(binary)
}

/**
 * GZIP Compresses data, then encodes it into a Base64 string.
 */
export const compressAndEncodeData = (dataToCompress: Uint8Array): string => {
    const compressedData = gzipSync(dataToCompress, { level: 6 })
    return uint8ArrayToBase64(compressedData)
}

/**
 * Decodes a Base64 string to a Uint8Array.
 *
 * Uses Uint8Array.fromBase64 when available, as it is much more performant.
 * Falls back to atob for older browsers.
 */
export const decodeBase64 = (base64: string): Uint8Array => {
    if (Uint8Array.fromBase64 != null) {
        return Uint8Array.fromBase64(base64)
    }
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i)
    }
    return bytes
}

/**
 * Converts a string into bytes using UTF-8 encoding.
 */
export const toBytes = (str: string): Uint8Array => {
    return new TextEncoder().encode(str)
}
