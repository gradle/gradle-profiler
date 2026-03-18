use wasm_bindgen::prelude::*;

mod builder;
pub(crate) use builder::GraphBuilder;

mod delete;
pub use delete::wasm_delete_node;

mod icicle;
pub use icicle::wasm_icicle_graph;

mod merge;
pub use merge::wasm_merge_children;

mod parser;
pub use parser::StacksParser;
#[cfg(test)]
pub use parser::parse_stacks_impl;

mod simplify;
pub use simplify::wasm_simplify_graph;

#[wasm_bindgen(start)]
pub fn main_js() {
    #[cfg(debug_assertions)]
    console_error_panic_hook::set_once();
}

/// Get the name of node `i` as a &str directly from the CSR names data.
#[inline]
pub(crate) fn get_name<'a>(names_data: &'a [u8], names_offsets: &[i32], i: usize) -> &'a str {
    let start = names_offsets[i] as usize;
    let end = names_offsets[i + 1] as usize;
    // SAFETY: `names_data` must be valid UTF-8. All flamegraph inputs are UTF-8 encoded text.
    unsafe { std::str::from_utf8_unchecked(&names_data[start..end]) }
}

/// Get the children of node `i` as a slice of node IDs from the CSR children data.
#[inline]
pub(crate) fn get_children<'a>(children_offsets: &[i32], children_data: &'a [i32], i: usize) -> &'a [i32] {
    let start = children_offsets[i] as usize;
    let end = children_offsets[i + 1] as usize;
    &children_data[start..end]
}

/// Build a parent map from the CSR children data.
/// `parents[child] = parent_id`, or `-1` for the root.
pub(crate) fn build_parent_map(children_offsets: &[i32], children_data: &[i32]) -> Vec<i32> {
    let size = children_offsets.len().saturating_sub(1);
    let mut parents: Vec<i32> = vec![-1; size];
    for i in 0..size {
        for &child in get_children(children_offsets, children_data, i) {
            parents[child as usize] = i as i32;
        }
    }
    parents
}

/// A stack graph in flat Compressed Sparse Row (CSR) encoding for efficient transfer between Rust and JS.
///
/// Children are stored in CSR format:
///   children of node i = children_data[children_offsets[i] .. children_offsets[i+1]]
///   children are sorted alphabetically by node name.
///
/// Node names and display names are stored as UTF-8 byte buffers with offset arrays:
///   name of node i = names_data[names_offsets[i] .. names_offsets[i+1]]
///
/// TODO: `names_data` and `display_names_data` store the full bytes of every node's name,
/// including duplicates where the same method name appears under different parents. For large
/// profiles these buffers dominate memory (~44 MB and ~20 MB respectively for a 1 GB stacks
/// file). A string-interning scheme — storing each distinct name once and using an indirection
/// layer in the offset arrays — could significantly reduce this cost.
#[wasm_bindgen]
pub struct WasmStackGraph {
    pub(crate) children_offsets: Vec<i32>,
    pub(crate) children_data: Vec<i32>,
    pub(crate) names_data: Vec<u8>,
    pub(crate) names_offsets: Vec<i32>,
    pub(crate) display_names_data: Vec<u8>,
    pub(crate) display_names_offsets: Vec<i32>,
    pub(crate) values: Vec<i64>,
}

#[wasm_bindgen]
impl WasmStackGraph {
    pub fn node_count(&self) -> u32 {
        self.names_offsets.len().saturating_sub(1) as u32
    }

    pub fn children_offsets(&self) -> Vec<i32> {
        self.children_offsets.clone()
    }

    pub fn children_data(&self) -> Vec<i32> {
        self.children_data.clone()
    }

    pub fn names_data(&self) -> Vec<u8> {
        self.names_data.clone()
    }

    pub fn names_offsets(&self) -> Vec<i32> {
        self.names_offsets.clone()
    }

    pub fn display_names_data(&self) -> Vec<u8> {
        self.display_names_data.clone()
    }

    pub fn display_names_offsets(&self) -> Vec<i32> {
        self.display_names_offsets.clone()
    }

    pub fn values(&self) -> Vec<i64> {
        self.values.clone()
    }
}
