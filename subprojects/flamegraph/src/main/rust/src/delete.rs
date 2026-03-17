use wasm_bindgen::prelude::*;

use crate::{build_parent_map, get_children, WasmStackGraph};

/// Deletes a node from the graph by severing it from its parent's children list
/// and subtracting its weight from all ancestors.
///
/// The deleted node and its entire subtree are left as unreachable orphans in the
/// names, display names, and values arrays — they are never visited during rendering,
/// so this is harmless and avoids a full graph compaction.
#[wasm_bindgen]
pub fn wasm_delete_node(
    children_offsets: &[i32],
    children_data: &[i32],
    names_data: &[u8],
    names_offsets: &[i32],
    values: &[i64],
    display_names_data: &[u8],
    display_names_offsets: &[i32],
    to_delete: u32,
) -> Result<WasmStackGraph, JsError> {
    let to_delete = to_delete as usize;
    if to_delete == 0 {
        return Err(JsError::new("cannot delete the root node"));
    }

    let size = names_offsets.len().saturating_sub(1);
    let parents = build_parent_map(children_offsets, children_data);

    let parent_id = parents[to_delete];
    if parent_id < 0 {
        return Err(JsError::new(&format!("node {} has no parent in a non-empty graph", to_delete)));
    }

    // Subtract deleted node's weight from all ancestors
    let deleted_value = values[to_delete];
    let mut new_values = values.to_vec();
    let mut curr = parent_id;
    while curr >= 0 {
        new_values[curr as usize] -= deleted_value;
        curr = parents[curr as usize];
    }

    // Rebuild the children arrays with node_id removed from its parent's child list.
    // Node count is unchanged so new_children_offsets stays the same length.
    // new_children_data loses exactly one entry — the deleted node's id.
    let mut new_children_offsets: Vec<i32> = Vec::with_capacity(children_offsets.len());
    let mut new_children_data: Vec<i32> = Vec::with_capacity(children_data.len() - 1);

    for i in 0..size {
        new_children_offsets.push(new_children_data.len() as i32);
        let children = get_children(children_offsets, children_data, i);
        if i == parent_id as usize {
            for &child in children {
                if child as usize != to_delete {
                    new_children_data.push(child);
                }
            }
        } else {
            new_children_data.extend_from_slice(children);
        }
    }
    new_children_offsets.push(new_children_data.len() as i32);

    Ok(WasmStackGraph {
        children_offsets: new_children_offsets,
        children_data: new_children_data,
        names_data: names_data.to_vec(),
        names_offsets: names_offsets.to_vec(),
        display_names_data: display_names_data.to_vec(),
        display_names_offsets: display_names_offsets.to_vec(),
        values: new_values,
    })
}
