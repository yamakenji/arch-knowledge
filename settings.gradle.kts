rootProject.name = "arch-knowledge"

// `application` is the use-case layer; `applications` holds executable hosts (AGENTS.md).
include(
    "core",
    "application",
    "profiles:business-software",
    "examples:order-management",
    "applications:cli",
)
