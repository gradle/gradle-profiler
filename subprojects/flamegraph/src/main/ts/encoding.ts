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
 * Decodes a Base64 encoded string, then GZIP decompresses it.
 *
 * Uses the native DecompressionStream API so decompression is truly streaming
 * — the browser never allocates a buffer for the full decompressed output,
 * allowing files that expand to multiple GB without hitting ArrayBuffer limits.
 */
export const decodeAndDecompressData = (
    base64CompressedStr: string,
): ReadableStream<Uint8Array> => {
    const binary = atob(base64CompressedStr)
    const compressedBytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
        compressedBytes[i] = binary.charCodeAt(i)
    }
    return new ReadableStream<Uint8Array>({
        start(controller) {
            controller.enqueue(compressedBytes)
            controller.close()
        },
    // The DOM type for DecompressionStream.writable is WritableStream<BufferSource>
    // (it accepts any ArrayBuffer/ArrayBufferView), but pipeThrough requires a
    // narrower WritableStream<Uint8Array>. We always feed Uint8Array chunks, so
    // this cast is safe — it works around a TypeScript lib type imprecision.
    }).pipeThrough(new DecompressionStream("gzip") as unknown as TransformStream<Uint8Array, Uint8Array>)
}

/**
 * Converts a string into bytes using UTF-8 encoding.
 */
export const toBytes = (str: string): Uint8Array => {
    return new TextEncoder().encode(str)
}
