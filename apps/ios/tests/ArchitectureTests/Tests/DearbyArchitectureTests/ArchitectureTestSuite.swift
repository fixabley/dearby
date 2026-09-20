import Testing

// One serialization boundary includes every suite and parameterized case using Harmonize.
// Separate serialized suites still run concurrently with each other.
@Suite(.serialized)
struct ArchitectureTestSuite {}
