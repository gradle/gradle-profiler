use ahash::AHashSet;
use wasm_bindgen::prelude::*;

use crate::{get_children, get_name, GraphBuilder, WasmStackGraph};

/// Merges all occurrences of `node_name` into a single aggregate root.
#[wasm_bindgen]
pub fn wasm_merge_children(
    children_offsets: &[i32],
    children_data: &[i32],
    names_data: &[u8],
    names_offsets: &[i32],
    values: &[i64],
    node_name: &str,
) -> Result<WasmStackGraph, JsError> {
    let size = values.len();
    let node_name_bytes = node_name.as_bytes();

    let root_nodes: AHashSet<usize> = (0..size)
        .filter(|&i| {
            let start = names_offsets[i] as usize;
            let end = names_offsets[i + 1] as usize;
            &names_data[start..end] == node_name_bytes
        })
        .collect();

    if root_nodes.is_empty() {
        return Err(JsError::new(&format!(
            "No nodes with name: {}",
            node_name
        )));
    }

    let mut new_graph = GraphBuilder::new(node_name);

    // Tracks the parent of the ith node in new_graph so we can walk ancestors
    // when subtracting double-counted samples (see below). Index 0 is the root.
    let mut new_parents: Vec<i32> = vec![-1i32];

    for &root_node in &root_nodes {
        let mut stack: Vec<(usize, usize)> = Vec::new();

        for &child in get_children(children_offsets, children_data, root_node) {
            stack.push((child as usize, 0));
        }

        while let Some((current, new_parent)) = stack.pop() {
            if root_nodes.contains(&current) {
                // This is a recursive call back into the merged node. Its subtree
                // will be attributed when we process it as a root_node in the outer
                // loop. Subtract its inclusive value from all ancestors in new_graph
                // now to avoid double-counting those samples.
                let mut ancestor = new_parent as i32;
                while ancestor >= 0 {
                    new_graph.values[ancestor as usize] -= values[current];
                    ancestor = new_parents[ancestor as usize];
                }
                continue;
            }

            let name = get_name(names_data, names_offsets, current);
            let (new_current, created) = new_graph.get_or_create_child(new_parent, name, values[current]);
            if created {
                new_parents.push(new_parent as i32);
            }

            for &child in get_children(children_offsets, children_data, current) {
                stack.push((child as usize, new_current));
            }
        }
    }

    for &root_node in &root_nodes {
        new_graph.values[0] += values[root_node];
    }

    Ok(new_graph.into_wasm_stack_graph())
}
