use wasm_bindgen::prelude::*;

use crate::builder::{format_jfr_name, parse_method_name};
use crate::{get_children, get_name, GraphBuilder, WasmStackGraph};

/// Produces a canonical simplified node name in JFR format, stripping line numbers
/// and async-profiler frame types. Unrecognized names are returned unchanged.
fn simplify_node_name(name: &str) -> String {
    match parse_method_name(name) {
        Some(p) => format_jfr_name(&p, true, true, false),
        None => name.to_string(),
    }
}

/// Converts a stack graph to a simplified form where:
/// - Both JFR-based and async-profiler-based method names are normalized to JFR format.
/// - Line numbers and async-profiler frame types (`_[x]`) are stripped.
/// - Nodes that become identical after simplification (e.g., same method at different
///   line numbers) are merged, with their sample counts summed.
#[wasm_bindgen]
pub fn wasm_simplify_graph(
    children_offsets: &[i32],
    children_data: &[i32],
    names_data: &[u8],
    names_offsets: &[i32],
    values: &[i64],
    node_id: u32,
) -> WasmStackGraph {
    let node_id = node_id as usize;
    let root_name = get_name(names_data, names_offsets, node_id);
    let simplified_root = simplify_node_name(root_name);
    let mut new_graph = GraphBuilder::new(&simplified_root);
    new_graph.values[0] = values[node_id];

    // (original_node_id, parent_id_in_new_graph)
    let mut stack: Vec<(usize, usize)> = Vec::new();
    for &child in get_children(children_offsets, children_data, node_id) {
        stack.push((child as usize, 0));
    }

    while let Some((current, new_parent)) = stack.pop() {
        let name = get_name(names_data, names_offsets, current);
        let simplified = simplify_node_name(name);
        let (new_current, _) = new_graph.get_or_create_child(new_parent, &simplified, values[current]);

        for &child in get_children(children_offsets, children_data, current) {
            stack.push((child as usize, new_current));
        }
    }

    new_graph.into_wasm_stack_graph()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parse_stacks_impl;

    fn simplify(data: &[u8]) -> WasmStackGraph {
        let g = parse_stacks_impl(data);
        wasm_simplify_graph(
            &g.children_offsets,
            &g.children_data,
            &g.names_data,
            &g.names_offsets,
            &g.values,
            0,
        )
    }

    fn node_names(g: &WasmStackGraph) -> Vec<String> {
        (0..g.names_offsets.len().saturating_sub(1))
            .map(|i| {
                let start = g.names_offsets[i] as usize;
                let end = g.names_offsets[i + 1] as usize;
                String::from_utf8(g.names_data[start..end].to_vec()).unwrap()
            })
            .collect()
    }

    #[test]
    fn test_jfr_strips_line_number() {
        let g = simplify(b"org.example.Foo.bar(int):10_[j] 5\n");
        let names = node_names(&g);
        assert!(names.contains(&"org.example.Foo.bar(int)".to_string()), "{names:?}");
        assert!(!names.iter().any(|n| n.contains(":10")), "{names:?}");
    }

    #[test]
    fn test_async_profiler_converts_to_jfr() {
        let g = simplify(b"org/example/Foo.bar_[j] 5\n");
        let names = node_names(&g);
        assert!(names.contains(&"org.example.Foo.bar()".to_string()), "{names:?}");
    }

    #[test]
    fn test_merges_nodes_with_different_line_numbers() {
        // Two stacks through the same method at different line numbers should merge.
        let g = simplify(
            b"org.example.Foo.bar(int):10_[j] 3\norg.example.Foo.bar(int):20_[j] 4\n",
        );
        let names = node_names(&g);
        let bar_count = names.iter().filter(|n| n.as_str() == "org.example.Foo.bar(int)").count();
        assert_eq!(bar_count, 1, "expected exactly one merged bar node, got {names:?}");

        let bar_idx = names.iter().position(|n| n == "org.example.Foo.bar(int)").unwrap();
        assert_eq!(g.values[bar_idx], 7);
    }

    #[test]
    fn test_non_method_names_unchanged() {
        let g = simplify(b"kernel 5\n");
        let names = node_names(&g);
        assert!(names.contains(&"kernel".to_string()), "{names:?}");
    }
}
