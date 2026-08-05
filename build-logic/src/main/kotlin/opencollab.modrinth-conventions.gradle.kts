plugins {
    id("com.modrinth.minotaur")
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "") // Even though this is the default value, apparently this prevents GitHub Actions caching the token?
    debugMode.set(System.getenv("MODRINTH_TOKEN") == null)
    projectId.set("magic-metrics")
    versionName.set(project.version as String)
    versionNumber.set(project.version as String)
    versionType.set("beta")
    changelog.set(System.getenv("CHANGELOG") ?: "")
    gameVersions.addAll("26.1", "26.1.2", "26.2")
    failSilently.set(true)
}