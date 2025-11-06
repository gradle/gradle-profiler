import { decompressSync, gzipSync } from "fflate"

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
 * Decodes a Base64 encoded string into a Uint8Array.
 * This is the reverse of `uint8ArrayToBase64`.
 */
const base64ToUint8Array = (base64: string): Uint8Array => {
    const binaryString = atob(base64)
    const len = binaryString.length
    const bytes = new Uint8Array(len)
    for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i)
    }
    return bytes
}

/**
 * GZIP Compresses data, then encodes it into a Base64 string.
 */
export const compressAndEncodeData = (dataToCompress: Uint8Array): string => {
    const compressedData = gzipSync(dataToCompress, { level: 6 })
    return uint8ArrayToBase64(compressedData)
}

/**
 * Decodes a Base64 encoded string, then GZIP decompresses it.
 */
export const decodeAndDecompressData = (
    base64CompressedStr: string,
): Uint8Array => {
    const compressedData = base64ToUint8Array(base64CompressedStr)
    return decompressSync(compressedData)
}

/**
 * Converts a string into bytes using UTF-8 encoding.
 */
export const toBytes = (str: string): Uint8Array => {
    return new TextEncoder().encode(str)
}

/**
 * Creates a ReadableStream from a Uint8Array.
 */
export const toStream = (data2: Uint8Array): ReadableStream<Uint8Array> => {
    return new Response(data2).body!
}
