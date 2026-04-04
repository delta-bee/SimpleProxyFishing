tasks.named("test") {
    mustRunAfter(":projects:proxy:shared:shadowJar")
}

