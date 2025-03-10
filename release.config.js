/*
 * Copyright (C) 2010-2022, Danilo Pianini and contributors
 * listed, for each module, in the respective subproject's build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

var publishCmd = `
git tag -a -f \${nextRelease.version} \${nextRelease.version} -F CHANGELOG.md
git push --force origin \${nextRelease.version} || exit 6
./gradlew injectVersionInWebsite || exit 7
git -C build/website/ add . || exit 1
git -C build/website/ commit -m "chore: update website to version \${nextRelease.version}" || exit 2
git -C build/website/ push || exit 3
./gradlew shadowJar --parallel || ./gradlew shadowJar --parallel || exit 4
./gradlew uploadKotlinOSSRH uploadKotlinMultiplatform release --parallel || ./gradlew uploadKotlinOSSRH uploadKotlinMultiplatform release --parallel || ./gradlew uploadKotlinOSSRH uploadKotlinMultiplatform release --parallel exit 5
./gradlew publishKotlinOSSRHPublicationToGithubRepository --continue || true
`
var config = require('semantic-release-preconfigured-conventional-commits');
config.plugins.push(
    ["@semantic-release/exec", {
        "publishCmd": publishCmd,
    }],
    ["@semantic-release/github", {
        "assets": [ 
            { "path": "build/shadow/*-all.jar" },
         ]
    }],
    "@semantic-release/git",
)
module.exports = config
