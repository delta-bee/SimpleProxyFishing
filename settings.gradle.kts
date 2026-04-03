rootProject.name = "AdvancedProxyChat"

include(
    "projects:common",

    "projects:proxy:shared",
    "projects:proxy:velocity",
    "projects:proxy:bungeecord",

    "projects:server:shared",
    "projects:server:spigot"
)

project(":projects:proxy:velocity").name = "AdvancedProxyChat-Velocity"
project(":projects:proxy:bungeecord").name = "AdvancedProxyChat-BungeeCord"
project(":projects:server:spigot").name = "AdvancedProxyChatHelper-Spigot"
