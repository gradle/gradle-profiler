import { compressAndEncodeData, toBytes } from "./encoding.ts"

const DEMO_STACKS = `alpha;component_one;foo;auth 10
alpha;component_one;foo;process;db_read;connection_pool;check_idle_connections;check_feature_flag 65
alpha;component_one;foo;process;db_read 150
alpha;component_one;foo;process;transform;serialization_helper;object_mapper;write_buffer 20
alpha;component_one;foo;logging 5
alpha;component_two;foo;auth 12
alpha;component_two;foo;process;db_read 40
alpha;component_two;foo;process;transform;serialization_helper;object_mapper;instrumentation.increment_counter 45
alpha;component_two;foo;process;transform 130
alpha;component_two;foo;validate 50
alpha;component_two;foo;logging 5
asset_pipeline;process_image;decode_png 80
asset_pipeline;process_image;resize 120
asset_pipeline;process_image;compress_jpeg 150
beta;foo;auth 5
beta;foo;process;db_read;query_planner;optimizer;gc.safepoint 25
beta;foo;process;db_read 10
beta;foo;logging 3
gamma;middleware;foo;auth 9
gamma;middleware;foo;process;db_read 60
gamma;middleware;foo;process;transform;serialization_helper;object_mapper;check_feature_flag 35
gamma;middleware;foo;process;transform 55
gamma;middleware;foo;logging 4
alpha;component_one;foo;auth 14
alpha;component_one;foo;process;db_read 135
alpha;component_one;foo;process;transform;serialization_helper;object_mapper;instrumentation.increment_counter 30
alpha;component_one;foo;process;transform 35
alpha;component_one;foo;logging 6
init_subsystem;load_config;parse_yaml 90
init_subsystem;connect_downstream_api 110
delta;service;foo;auth 20
delta;service;foo;process;db_read 90
delta;service;foo;process;transform 80
delta;service;foo;validate 45
delta;service;foo;logging 8
beta;foo;auth 7
beta;foo;process;db_read;connection_pool;check_idle_connections;check_feature_flag 15
beta;foo;process;db_read 15
beta;foo;logging 4
gamma;middleware;foo;auth 11
gamma;middleware;foo;process;db_read 55
gamma;middleware;foo;process;transform 65
gamma;middleware;foo;process;db_read;query_planner;optimizer;gc.safepoint 45
gamma;middleware;foo;logging 5
`

/**
 * This application supports reading from base64-encoded and GZIP-compressed
 * stack data. This constant represents the sort of data this app can read.
 */
export const ENCODED_DEMO_STACKS = compressAndEncodeData(toBytes(DEMO_STACKS))
