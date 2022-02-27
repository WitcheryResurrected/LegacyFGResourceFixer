plugins {
    `maven-publish`
    java
}

version = "1.2"
group = "net.msrandom.resourcefixer"

System.getenv("GITHUB_RUN_NUMBER")?.let { version = "$version-$it" }

base {
    archivesName.set("LegacyResourceFixer")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
