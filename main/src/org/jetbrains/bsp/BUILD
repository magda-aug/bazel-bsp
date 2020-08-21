java_library(
    name = "bsp",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_code_gson_gson",
        "@maven//:com_google_guava_guava",
        "@maven//:ch_epfl_scala_bsp4j",
        "@maven//:org_eclipse_lsp4j_org_eclipse_lsp4j_jsonrpc",
        "@maven//:org_eclipse_xtext_org_eclipse_xtext_xbase_lib",
        "@io_bazel//third_party/grpc:grpc-jar",
        "@com_google_protobuf//:protobuf_java",
	    "@io_bazel_rules_scala//src/protobuf/io/bazel/rules_scala:diagnostics_java_proto",
        "@io_bazel//src/main/java/com/google/devtools/build/lib/buildeventstream/proto:build_event_stream_java_proto",
        "@googleapis//:google_devtools_build_v1_publish_build_event_java_grpc",
        "@googleapis//:google_devtools_build_v1_build_events_java_proto",
        "@googleapis//:google_devtools_build_v1_publish_build_event_proto",
        "@googleapis//:google_devtools_build_v1_publish_build_event_java_proto",
        "@io_bazel//src/main/protobuf:analysis_java_proto",
        "@io_bazel//src/main/protobuf:build_java_proto",
    ]
)
