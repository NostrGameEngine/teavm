/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.buildutil;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jreleaser.gradle.plugin.JReleaserExtension;
import org.jreleaser.gradle.plugin.JReleaserPlugin;
import org.jreleaser.model.Active;
import org.jreleaser.model.api.deploy.maven.MavenCentralMavenDeployer;

public class ReleaseTeaVMPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        var publish = Boolean.parseBoolean(target.getProviders().gradleProperty("teavm.mavenCentral.publish")
                .getOrElse("false"));
        if (publish) {
            target.getPlugins().apply(JReleaserPlugin.class);
        }
        if (publish) {
            target.afterEvaluate(p -> {
                var jreleaser = target.getExtensions().getByType(JReleaserExtension.class);
                jreleaser.getGitRootSearch().set(true);
                jreleaser.signing(signing -> signing.getActive().set(Active.NEVER));
                jreleaser.deploy(deploy -> {
                    deploy.maven(maven -> {
                        maven.pomchecker(pomchecker -> {
                            pomchecker.getFailOnError().set(false);
                            pomchecker.getFailOnWarning().set(false);
                            pomchecker.getStrict().set(false);
                        });
                        maven.mavenCentral(mavenCentral -> {
                            var sonatype = maven.getMavenCentral().maybeCreate("sonatype");
                            sonatype.getActive().set(Active.ALWAYS);
                            sonatype.getUrl().set("https://central.sonatype.com/api/v1/publisher");
                            sonatype.getNamespace().set("org.ngengine");
                            sonatype.getStagingRepositories().add("build/staging-deploy");
                            sonatype.getUsername().set(target.getProviders()
                                    .environmentVariable("SONATYPE_USERNAME")
                                    .orElse(target.getProviders().gradleProperty("ossrhUsername")));
                            sonatype.getPassword().set(target.getProviders()
                                    .environmentVariable("SONATYPE_PASSWORD")
                                    .orElse(target.getProviders().gradleProperty("ossrhPassword")));
                            sonatype.getStage().set(MavenCentralMavenDeployer.Stage.FULL);
                            sonatype.getSign().set(false);
                            sonatype.getApplyMavenCentralRules().set(true);
                        });
                    });
                });
                jreleaser.release(release -> {
                    release.github(github -> {
                        github.getSkipRelease().set(true);
                        github.getToken().set("123");
                    });
                });
            });
        }
    }
}
