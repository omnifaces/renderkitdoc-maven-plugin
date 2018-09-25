/*
 * Copyright (c) 2018 OmniFaces. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */
package org.omnifaces.renderkitdoc;

import static org.omnifaces.facesconfigparser.FacesConfigParser.parseFacesConfig;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.omnifaces.facesconfigparser.digester.beans.FacesConfigBean;
import org.sonatype.plexus.build.incremental.BuildContext;

@Mojo(name = "generate")
public class RenderkitMojo extends AbstractMojo {

    @Component
    private BuildContext buildContext;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "renderKitId", defaultValue = "HTML_BASIC")
    private String renderKitId;

    @Parameter(property = "facesConfig", required = true)
    private String facesConfig;

    @Parameter(property = "schemaDirectory")
    private String schemaDirectory;

    @Parameter(property = "project.build.directory")
    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info(
            "Generating RenderKitDoc for faces config file: " + new File(facesConfig).toString());

        getLog().info(
            "Output directory: " + outputDirectory.toString() + "/renderkitdoc");

        // Parses a faces-config.xml file into a set of tree of Java classes
        FacesConfigBean facesConfigRoot = parseFacesConfig(facesConfig, schemaDirectory);

        // Generates HTML docs from the parsed faces config file
        RenderKitDocGenerator renderKitDocGenerator = new RenderKitDocGenerator(outputDirectory.toString(), renderKitId);

        renderKitDocGenerator.generateHtmlDocs(facesConfigRoot);

        for (File file : renderKitDocGenerator.getFilesTouched()) {
            getLog().info("Refreshing: " + file.toString());
            buildContext.refresh(file);
        }
    }

}