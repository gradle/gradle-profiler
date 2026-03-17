use wasm_bindgen::prelude::*;

use crate::{build_parent_map, get_children, get_name, GraphBuilder, WasmStackGraph};

/// Discovers all "slices" in the subtree rooted at `target`, where a slice is a node with positive
/// self-time. Slices are sorted heaviest first. Each slice is `(node_id, self_weight)`. Note, a
/// "slice" is roughly equivalent to a line in the original "-stacks.txt" file, and there should
/// be as many slices as there are lines in that file, if the file has no duplicate stacks.
fn discover_slices(
    children_offsets: &[i32],
    children_data: &[i32],
    values: &[i64],
    target: usize,
) -> Vec<(usize, i64)> {
    let mut slices: Vec<(usize, i64)> = Vec::new();
    let mut stack = vec![target];
    while let Some(curr) = stack.pop() {
        let node_children = get_children(children_offsets, children_data, curr);
        let mut children_sum: i64 = 0;
        for &child in node_children {
            children_sum += values[child as usize];
            stack.push(child as usize);
        }
        let self_weight = values[curr] - children_sum;
        if self_weight > 0 {
            slices.push((curr, self_weight));
        }
    }
    slices.sort_unstable_by(|a, b| b.1.cmp(&a.1));
    slices
}

/// Writes the inverted call stack of `original_node` to `new_parent` of `new_graph`, stopping
/// when `budget` nodes have been written, or the entire inverted call stack is written.
///
/// Returns the number of nodes written. Never larger than `budget`.
fn write_caller_chain(
    new_graph: &mut GraphBuilder,
    mut new_parent: usize,
    mut original_node: usize,
    weight: i64,
    mut budget: usize,
    target: usize,
    parents: &[i32],
    names_data: &[u8],
    names_offsets: &[i32],
) -> usize {
    let initial_size = new_graph.node_names.len();

    loop {
        if budget == 0 || original_node == target {
            break;
        }

        // Add the new node if it is not already present, adding the weight of this stack.
        let name = get_name(names_data, names_offsets, original_node);
        let (new_node, created) = new_graph.get_or_create_child(new_parent, name, weight);
        budget = budget.saturating_sub(created as usize);

        // Walk the caller chain upward until we hit the target node.
        let original_parent = parents[original_node];
        if original_parent < 0 {
            break;
        }
        new_parent = new_node;
        original_node = original_parent as usize;
    }

    new_graph.node_names.len() - initial_size
}

/// Build icicle graph based off the subgraph of some original graph, rooted at `node_id`.
///
/// Simply inverting all call stacks and inserting them into a new graph will likely produce
/// a new graph that is massively larger than the original. This is since stacks in a flamegraph
/// often start with the same stack frames, allowing a tree-like structure to deduplicate them.
/// However, the same structure cannot take advantage of inverted call stacks the same way.
///
/// For this reason, this method generates an incomplete icicle graph containing up to `max_nodes`
/// nodes. It proportionally distributes a "node budget" to icicles as they are inserted, allocating
/// more nodes to wider icicles. Icicles which reach their budget are truncated.
///
/// In practice, this produces graphs where the node budget is optimally distributed. Wide icicles
/// which contribute the most to the graph can be rendered completely with no or little loss of
/// detail, while extremely narrow icicles are inserted with a limited node budget.
#[wasm_bindgen]
pub fn wasm_icicle_graph(
    children_offsets: &[i32],
    children_data: &[i32],
    names_data: &[u8],
    names_offsets: &[i32],
    values: &[i64],
    node_id: u32,
    max_nodes: u32,
) -> WasmStackGraph {
    let target = node_id as usize;
    let parents = build_parent_map(children_offsets, children_data);
    let slices = discover_slices(children_offsets, children_data, values, target);

    let total_weight = values[target];
    let mut new_graph = GraphBuilder::new(get_name(names_data, names_offsets, target));
    new_graph.values[0] = total_weight;

    let mut weight = total_weight;
    let mut budget = (max_nodes as usize).saturating_sub(1);
    for &(slice_id, slice_weight) in &slices {
        if budget == 0 {
            break;
        }

        let alloc = ((slice_weight as f64 * budget as f64) / weight as f64).round() as usize;
        weight -= slice_weight;
        if alloc == 0 {
            continue;
        }

        budget -= write_caller_chain(
            &mut new_graph,
            0,
            slice_id,
            slice_weight,
            alloc,
            target,
            &parents,
            names_data,
            names_offsets
        );
    }

    new_graph.into_wasm_stack_graph()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parse_stacks_impl;

    fn first_node_with_name(g: &WasmStackGraph, name: &str) -> usize {
        let n = g.node_count() as usize;
        for i in 0..n {
            let s = g.names_offsets[i] as usize;
            let e = g.names_offsets[i + 1] as usize;
            if &g.names_data[s..e] == name.as_bytes() {
                return i;
            }
        }
        panic!("node '{}' not found", name);
    }

    #[track_caller]
    fn assert_no_node(g: &WasmStackGraph, name: &str) {
        let n = g.node_count() as usize;
        for i in 0..n {
            let s = g.names_offsets[i] as usize;
            let e = g.names_offsets[i + 1] as usize;
            if &g.names_data[s..e] == name.as_bytes() {
                panic!("expected no node '{}' but found one", name);
            }
        }
    }

    fn node_value(g: &WasmStackGraph, name: &str) -> i64 {
        g.values[first_node_with_name(g, name)]
    }

    fn icicle(stacks: &str, root_name: &str, max_nodes: u32) -> WasmStackGraph {
        let base = parse_stacks_impl(stacks.as_bytes());
        let root_id = first_node_with_name(&base, root_name) as u32;
        wasm_icicle_graph(
            &base.children_offsets, &base.children_data,
            &base.names_data, &base.names_offsets,
            &base.values, root_id, max_nodes,
        )
    }

    #[test]
    fn test_redistributes_unused_budget() {
        // x gets alloc=3 but its chain is only 2 deep (x, a); surplus flows to y.
        // y gets alloc=1: writes y but not its caller b.
        let g = icicle("a;x 100\nb;y 1\n", "root", 4);
        assert_eq!(node_value(&g, "y"), 1);
        assert_no_node(&g, "b");
    }

    #[test]
    fn test_heavy_path_wins() {
        let g = icicle("a;x 10\nb;y 5\n", "root", 4);
        assert_eq!(node_value(&g, "x"), 10);
    }

    #[test]
    fn test_root_name_matches_target() {
        let g = icicle("a;b;x 10\na;c;y 5\n", "a", 10);
        let s = g.names_offsets[0] as usize;
        let e = g.names_offsets[1] as usize;
        assert_eq!(&g.names_data[s..e], b"a", "root node should be named after the target");
    }

    #[test]
    fn test_skipped_slice_weight_is_still_decremented() {
        // Slices sorted: x=6, y=4, z=3, w=2. Budget=1, total=15.
        // x: round(6/15)=0, skip. y: round(4/9)=0, skip. z: round(3/5)=1, written.
        let g = icicle("a;x 6\nb;y 4\nc;z 3\nd;w 2\n", "root", 2);
        assert_no_node(&g, "x");
        assert_no_node(&g, "y");
        assert_no_node(&g, "w");
        assert_eq!(node_value(&g, "z"), 3, "z");
    }

    #[test]
    fn test_same_function_slices_merge() {
        // x appears in two call paths; both slices write to the same icicle node, accumulating values.
        let g = icicle("a;b;x 10\nc;x 5\n", "root", 10);
        assert_eq!(node_value(&g, "x"), 15);
    }

    #[test]
    fn test_max_nodes_one_only_root() {
        let g = icicle("a;b;c 100\n", "root", 1);
        assert_eq!(g.node_count(), 1);
    }

    #[test]
    fn test_target_self_time_does_not_walk_above() {
        // "b" has self-time (5) and a child c (10). Ensure, the self-time slice
        // does not walk above the target into b's callers in the original graph.
        let g = icicle("b;c 10\nb 5\n", "b", 10);
        assert_eq!(node_value(&g, "c"), 10);
        assert_no_node(&g, "root");
    }

    #[test]
    fn test_stack_truncated_by_rightful_budget() {
        // x gets alloc=round(60/100 * 4)=2 but its chain is 4 deep (x, c, b, a).
        // Writes x and c (its immediate caller), but not b or a.
        // y gets alloc=2 and its chain is 2 deep (y, d), fully written.
        let g = icicle("a;b;c;x 60\nd;y 40\n", "root", 5);
        assert_eq!(node_value(&g, "x"), 60);
        assert_eq!(node_value(&g, "c"), 60);
        assert_no_node(&g, "b");
        assert_no_node(&g, "a");
        assert_eq!(node_value(&g, "y"), 40);
        assert_eq!(node_value(&g, "d"), 40);
    }

    #[test]
    fn test_max_nodes_respected() {
        let g = icicle("a;b;c;d;e;f;g;h;i;j 100\n", "root", 5);
        assert_eq!(g.node_count(), 5, "node count should equal max_nodes");
        assert_eq!(node_value(&g, "g"), 100);
        assert_no_node(&g, "f");
    }
}
