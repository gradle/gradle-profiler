use wasm_bindgen::prelude::*;

use crate::{GraphBuilder, WasmStackGraph};

fn parse_line(line: &[u8], graph: &mut GraphBuilder) -> Result<(), String> {
    let last_space = memchr::memrchr(b' ', line).ok_or_else(|| {
        format!(
            "malformed input: no space in line: {}",
            std::str::from_utf8(line).unwrap_or("<invalid utf8>")
        )
    })?;

    let mut value: i64 = 0;
    for &b in &line[last_space + 1..] {
        value = value * 10 + (b - b'0') as i64;
    }

    let frames = &line[..last_space];
    let mut parent = 0usize;
    let mut prev = 0usize;

    for pos in memchr::memchr_iter(b';', frames) {
        if pos > prev {
            // SAFETY: input is valid UTF-8 (decompressed from UTF-8 encoded text)
            let name = unsafe { std::str::from_utf8_unchecked(&frames[prev..pos]) };
            (parent, _) = graph.get_or_create_child(parent, name, value);
        }
        prev = pos + 1;
    }
    // Last (or only) frame
    if prev < frames.len() {
        let name = unsafe { std::str::from_utf8_unchecked(&frames[prev..]) };
        graph.get_or_create_child(parent, name, value);
    }

    graph.values[0] += value;
    Ok(())
}

/// Streaming stack parser — processes decompressed chunks as they arrive.
///
/// JS workflow:
///   ```js
///   const parser = new StacksParser()
///   // for each decompressed chunk:
///   parser.feed(chunk)
///   // when done:
///   const graph = parser.finish()
///   ```
///
/// Partial lines that span chunk boundaries are buffered internally, so chunks of
/// any size (including single-byte chunks) are handled correctly.
#[wasm_bindgen]
pub struct StacksParser {
    graph: GraphBuilder,
    /// Bytes of an incomplete line carried over from the previous chunk.
    line_buf: Vec<u8>,
}

#[wasm_bindgen]
impl StacksParser {
    #[wasm_bindgen(constructor)]
    pub fn new(root_name: &str) -> Self {
        StacksParser {
            graph: GraphBuilder::new(root_name),
            line_buf: Vec::new(),
        }
    }

    /// Feed a decompressed chunk into the parser.
    /// May be called any number of times. Chunks may be any size.
    pub fn feed(&mut self, chunk: &[u8]) -> Result<(), JsError> {
        let mut start = 0;
        for pos in memchr::memchr_iter(b'\n', chunk) {
            if self.line_buf.is_empty() {
                // No pending partial data — process the slice directly.
                let line_end = if pos > start && chunk[pos - 1] == b'\r' { pos - 1 } else { pos };
                if line_end > start {
                    parse_line(&chunk[start..line_end], &mut self.graph).map_err(|e| JsError::new(&e))?;
                }
            } else {
                // Complete the pending partial line.
                self.line_buf.extend_from_slice(&chunk[start..pos]);
                // Strip trailing \r (handles \r\n split across chunks).
                if self.line_buf.last() == Some(&b'\r') {
                    self.line_buf.pop();
                }
                if !self.line_buf.is_empty() {
                    parse_line(&self.line_buf, &mut self.graph).map_err(|e| JsError::new(&e))?;
                }
                self.line_buf.clear();
            }
            start = pos + 1;
        }
        // Buffer any remaining bytes as a partial line.
        if start < chunk.len() {
            self.line_buf.extend_from_slice(&chunk[start..]);
        }
        Ok(())
    }

    /// Finish parsing and return the completed graph.
    /// Consumes the parser. Do not call `feed` after this.
    pub fn finish(mut self) -> Result<WasmStackGraph, JsError> {
        if !self.line_buf.is_empty() {
            // Strip trailing \r (in case the last line ended with \r without \n).
            if self.line_buf.last() == Some(&b'\r') {
                self.line_buf.pop();
            }
            parse_line(&self.line_buf, &mut self.graph).map_err(|e| JsError::new(&e))?;
        }
        Ok(self.graph.into_wasm_stack_graph())
    }
}

/// Test helper: parse stack collapse data in one shot via StacksParser.
#[cfg(test)]
pub fn parse_stacks_impl(data: &[u8]) -> WasmStackGraph {
    let mut parser = StacksParser::new("root");
    parser.feed(data).expect("parse error in feed");
    parser.finish().expect("parse error in finish")
}
