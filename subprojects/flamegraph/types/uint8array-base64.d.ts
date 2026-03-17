// Type declaration for Uint8Array.fromBase64
// TC39 proposal, available in Chrome 140+, Firefox 133+, Safari 18.2+
interface Uint8ArrayConstructor {
    fromBase64?: (
        base64: string,
        options?: {
            alphabet?: "base64" | "base64url"
            lastChunkHandling?: "loose" | "strict" | "stop-before-partial"
        },
    ) => Uint8Array
}
