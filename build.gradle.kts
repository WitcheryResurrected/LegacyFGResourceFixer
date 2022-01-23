plugins {
    `maven-publish`
    java
}

group = "net.msrandom.resourcefixer"
version = "1.0"

base {
    archivesName.set("LegacyResourceFixer")
}

java {
    withSourcesJar()
}

repositories {
    maven(url = "https://files.minecraftforge.net/maven")
    maven(url = "https://libraries.minecraft.net/")
}

dependencies {
    implementation(group = "net.minecraft", name = "launchwrapper", version = "1.12")
}

tasks.jar {
    from("LICENSE")
}

publishing {
    System.getenv("MAVEN_USERNAME")?.let { mavenUsername ->
        System.getenv("MAVEN_PASSWORD")?.let { mavenPassword ->
            publications {
                create<MavenPublication>("maven") {
                    groupId = group.toString()
                    artifactId = project.name
                    version = project.version.toString()

                    from(components["java"])
                    artifact(tasks.named("sourcesJar"))
                }
            }

            repositories {
                maven {
                    url = uri("https://maven.msrandom.net/repository/root/")
                    credentials {
                        username = mavenUsername
                        password = mavenPassword
                    }
                }
            }
        }
    }
}
