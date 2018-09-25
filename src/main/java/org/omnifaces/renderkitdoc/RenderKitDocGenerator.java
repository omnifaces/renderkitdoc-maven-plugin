/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.omnifaces.facesconfigparser.digester.beans.AttributeBean;
import org.omnifaces.facesconfigparser.digester.beans.DescriptionBean;
import org.omnifaces.facesconfigparser.digester.beans.FacesConfigBean;
import org.omnifaces.facesconfigparser.digester.beans.RenderKitBean;
import org.omnifaces.facesconfigparser.digester.beans.RendererBean;

/**
 * Generate javadoc style documenation about the render-kits defined in a faces-config.xml file.
 *
 */
public class RenderKitDocGenerator {

    public static String DOCTYPE = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\"\"http://www.w3.org/TR/REC-html40/loose.dtd\">";

    // -------------------------------------------------------- Static Variables

    // The directory into which the HTML will be generated
    private File baseDirectory;

    // The directory into which the individual Renderer HTML will be generated
    private File renderKitDirectory;
    private String renderKitId;

    private FacesConfigBean configBean;

    private List<File> filesTouched = new ArrayList<>();

    // ------------------------------------------------------------ Constructors

    public RenderKitDocGenerator(String outputDirectory, String renderKitId) {
        baseDirectory = new File(outputDirectory, "renderkitdoc");
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs();
            filesTouched.add(baseDirectory);
        }

        this.renderKitId = renderKitId;

        renderKitDirectory = new File(baseDirectory, renderKitId);
        if (!renderKitDirectory.exists()) {
            renderKitDirectory.mkdirs();
            filesTouched.add(renderKitDirectory);
        }
    }


    // ---------------------------------------------------------- Public Methods

    public void generateHtmlDocs(FacesConfigBean configBean) {
        this.configBean = configBean;

        try {
            // Copy the static files to the output area
            File indexFile = new File(baseDirectory, "index.html");
            File stylesheet = new File(baseDirectory, "stylesheet.css");

            copyResourceToFile("com/sun/faces/generate/facesdoc/index.html", indexFile);
            copyResourceToFile("com/sun/faces/generate/facesdoc/stylesheet.css", stylesheet);

            filesTouched.add(indexFile);
            filesTouched.add(stylesheet);

            generateAllRenderersFrame();
            generateRenderKitSummary();
            generateRenderersDocs();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<File> getFilesTouched() {
        return filesTouched;
    }


    // --------------------------------------------------------- Private Methods

    private static String getFirstSentance(String parameter) throws Exception {
        return parameter.substring(0, parameter.indexOf('.') + 1);
    }

    private static void copyResourceToFile(String resourceName, File file) throws Exception {
        byte[] bytes = new byte[1024];

        FileOutputStream fos = new FileOutputStream(file);
        URL url = getCurrentLoader(fos).getResource(resourceName);
        URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
        for (int len = bis.read(bytes, 0, 1024); len != -1; len = bis.read(bytes, 0, 1024)) {
            fos.write(bytes, 0, len);
        }
        fos.close();
        bis.close();
    }

    private static void writeStringToFile(String toWrite, File file) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(toWrite.getBytes());
        }
    }

    private static void appendResourceToStringBuffer(String resourceName, StringBuffer sb) throws Exception {

        char[] chars = new char[1024];

        URL url = getCurrentLoader(sb).getResource(resourceName);
        URLConnection conn = url.openConnection();
        conn.setUseCaches(false);
        InputStreamReader isr = new InputStreamReader(conn.getInputStream());
        for (int len = isr.read(chars, 0, 1024); len != -1; len = isr.read(chars, 0, 1024)) {
            sb.append(chars, 0, len);
        }
        isr.close();
    }

    private void generateAllRenderersFrame() throws Exception {

        // generate the allrenderers-frame.html
        StringBuffer sb = new StringBuffer(2048);
        appendResourceToStringBuffer("com/sun/faces/generate/facesdoc/allrenderers-frame.top", sb);
        sb.append("<FONT size=\"+1\" CLASS=\"FrameHeadingFont\">\n");

        sb.append("<B>" + renderKitId + " RenderKit ");
        String implVersionNumber = System.getProperty("impl.version.number");
        if (null != implVersionNumber) {
            sb.append("(" + implVersionNumber + ")");
        }
        sb.append("</B></FONT>\n");

        sb.append("<BR>\n\n");
        sb.append("<DL CLASS=\"FrameItemFont\">\n\n");

        Map<String, ArrayList<RendererBean>> renderersByComponentFamily = getComponentFamilyRendererMap(configBean, renderKitId);

        for (Map.Entry<String, ArrayList<RendererBean>> entry : renderersByComponentFamily.entrySet()) {

            String curFamily = entry.getKey();
            sb.append("  <DT>" + curFamily + "</DT>\n");
            List<RendererBean> renderers = entry.getValue();

            for (Iterator<RendererBean> rendererIter = renderers.iterator(); rendererIter.hasNext();) {

                RendererBean renderer = rendererIter.next();
                String curType = renderer.getRendererType();
                DescriptionBean[] descriptions = renderer.getDescriptions();
                String enclosingDiv = null, enclosingSpan = null;
                int[] divStart = new int[1];
                int[] spanStart = new int[1];
                if (descriptions != null) {
                    // Get the current operating locale
                    String localeStr = Locale.getDefault().getCountry().toLowerCase();

                    // Iterate over the descriptions and try to find one that matches
                    // the country of the current locale
                    for (DescriptionBean description : descriptions) {
                        if (description.getLang() != null && (-1 != localeStr.indexOf(description.getLang().toLowerCase()))) {
                            enclosingDiv = getFirstDivFromString(renderer.getDescription(description.getLang()).getDescription(), divStart);
                            enclosingSpan = getFirstSpanFromString(renderer.getDescription(description.getLang()).getDescription(), spanStart);

                            break;
                        }
                    }
                }

                if (enclosingDiv != null || enclosingSpan != null) {
                    String divOrSpan = (null != enclosingDiv ? enclosingDiv : enclosingSpan);
                    // If there is a div and a span, take which ever comes first
                    if (enclosingDiv != null && enclosingSpan != null) {
                        divOrSpan = (spanStart[0] < divStart[0] ? enclosingSpan : enclosingDiv);
                    }
                    sb.append("  <DD>" + divOrSpan);
                    sb.append("<A HREF=\"" + renderKitId + "/" + curFamily + curType + ".html\" TARGET=\"rendererFrame\">" + curType + "</A>");
                    sb.append((null != enclosingDiv ? "</div>" : "</span>") + "</DD>\n");
                } else {
                    sb.append("  <DD><A HREF=\"" + renderKitId + "/" + curFamily + curType + ".html\" TARGET=\"rendererFrame\">" + curType + "</A></DD>\n");
                }
            }
        }

        sb.append("</dl>\n");

        appendResourceToStringBuffer("com/sun/faces/generate/facesdoc/allrenderers-frame.bottom", sb);

        File allrenderersFile = new File(baseDirectory, "allrenderers-frame.html");
        writeStringToFile(sb.toString(), allrenderersFile);
        filesTouched.add(allrenderersFile);
    }

    private void generateRenderKitSummary() throws Exception {

        // generate the renderkit-summary.html
        StringBuffer sb = new StringBuffer(2048);
        appendResourceToStringBuffer("com/sun/faces/generate/facesdoc/renderkit-summary.top", sb);
        sb.append("<H2>" + renderKitId + " RenderKit ");

        String implVersionNumber = System.getProperty("impl.version.number");
        if (null != implVersionNumber) {
            sb.append("(" + implVersionNumber + ")");
        }
        sb.append("</H2>");
        sb.append("<BR>\n\n");

        RenderKitBean renderKit = configBean.getRenderKit(renderKitId);
        if (renderKit == null) {
            RenderKitBean[] kits = configBean.getRenderKits();
            if (kits == null) {
                throw new IllegalStateException("no RenderKits");
            }

            renderKit = kits[0];
            if (renderKit == null) {
                throw new IllegalStateException("no RenderKits");
            }
        }

        DescriptionBean descBean = renderKit.getDescription("");
        String description = (null == descBean) ? "" : descBean.getDescription();
        sb.append("<P>" + description + "</P>\n");
        sb.append("<P />");
        sb.append("<TABLE BORDER=\"1\" CELLPADDING=\"3\" CELLSPACING=\"0\" WIDTH=\"100%\">");
        sb.append("<TR BGCOLOR=\"#CCCCFF\" CLASS=\"TableHeadingColor\">\n");
        sb.append("<TD COLSPAN=\"3\"><FONT SIZE=\"+2\">\n");
        sb.append("<B>Renderer Summary</B></FONT></TD>\n");
        sb.append("\n");
        sb.append("<TR>\n");
        sb.append("<TH>component-family</TH>\n");
        sb.append("<TH>renderer-type</TH>\n");
        sb.append("<TH>description</TH>\n");
        sb.append("</TR>\n");

        Map<String, ArrayList<RendererBean>> renderersByComponentFamily = getComponentFamilyRendererMap(configBean, renderKitId);

        for (Map.Entry<String, ArrayList<RendererBean>> entry : renderersByComponentFamily.entrySet()) {
            String curFamily = entry.getKey();
            List<RendererBean> renderers = entry.getValue();

            sb.append("  <TR>\n");
            sb.append("    <TD rowspan=\"" + renderers.size() + "\">" + curFamily + "</TD>\n");
            for (Iterator<RendererBean> rendererIter = renderers.iterator(); rendererIter.hasNext();) {

                RendererBean renderer = rendererIter.next();
                String curType = renderer.getRendererType();
                sb.append("    <TD><A HREF=\"" + curFamily + curType + ".html\" TARGET=\"rendererFrame\">" + curType + "</A></TD>\n");
                descBean = renderer.getDescription("");
                description = (null == descBean) ? "" : descBean.getDescription();
                sb.append("    <TD>" + getFirstSentance(description) + "</TD>");
                if (rendererIter.hasNext()) {
                    sb.append("  </TR>\n");
                    sb.append("  <TR>\n");
                }
            }
            sb.append("  </TR>\n");
        }

        sb.append("</TABLE>\n\n");

        appendResourceToStringBuffer("com/sun/faces/generate/facesdoc/renderkit-summary.bottom", sb);

        File renderkitFile = new File(renderKitDirectory, "renderkit-summary.html");
        writeStringToFile(sb.toString(), renderkitFile);
        filesTouched.add(renderkitFile);
    }

    private void generateRenderersDocs() throws Exception {
        StringBuffer sb;
        RenderKitBean renderKit;
        DescriptionBean descBean;

        String description;
        String rendererType;
        String componentFamily;
        String defaultValue;
        String title;

        // generate the docus for each renderer

        if ((renderKit = configBean.getRenderKit(renderKitId)) == null) {
            RenderKitBean[] kits = configBean.getRenderKits();
            if (kits == null) {
                throw new IllegalStateException("no RenderKits");
            }

            renderKit = kits[0];
            if (renderKit == null) {
                throw new IllegalStateException("no RenderKits");
            }
        }

        RendererBean[] renderers = renderKit.getRenderers();
        AttributeBean[] attributes;
        sb = new StringBuffer(2048);

        for (int i = 0, len = renderers.length; i < len; i++) {
            if (renderers[i] == null) {
                throw new IllegalStateException("null Renderer at index: " + i);
            }
            
            attributes = renderers[i].getAttributes();

            sb.append(DOCTYPE + "\n");
            sb.append("<html>\n");
            sb.append("<head>\n");
            // PENDING timestamp
            sb.append("<title>\n");
            title = "<font size=\"-1\">component-family:</font> " + (componentFamily = renderers[i].getComponentFamily())
                    + " <font size=\"-1\">renderer-type:</font> " + (rendererType = renderers[i].getRendererType());
            sb.append(title + "\n");
            sb.append("</title>\n");
            // PENDING META tag
            sb.append("<link REL =\"stylesheet\" TYPE=\"text/css\" HREF=\"../stylesheet.css\" TITLE=\"Style\">\n");
            sb.append("</head>\n");
            sb.append("<script>\n");
            sb.append("function asd()\n");
            sb.append("{\n");
            sb.append("  parent.document.title=" + title + "\n");
            sb.append("}\n");
            sb.append("</SCRIPT>\n");
            sb.append("<body BGCOLOR=\"white\" onload=\"asd();\">\n");
            sb.append("\n");
            sb.append("<H2><font size=\"-1\">" + renderKitId + " render-kit</font>\n");
            sb.append("<br />\n");
            sb.append(title + "\n");
            sb.append("</H2>\n");
            sb.append("<HR />\n");
            descBean = renderers[i].getDescription("");
            description = (null == descBean) ? "" : descBean.getDescription();
            sb.append("<P>" + description + "</P>\n");

            // Render our renders children status

            if (renderers[i].isRendersChildren()) {
                sb.append("<P>This renderer is responsible for rendering its children.</P>");
            } else {
                sb.append("<P>This renderer is not responsible for rendering its children.</P>");
            }

            // If we have attributes
            if ((null == attributes) || (0 < attributes.length)) {
                sb.append("<HR />\n");
                sb.append("<a NAME=\"attributes\"><!-- --></a>\n");
                sb.append("\n");
                sb.append("<h3>Note:</h3>\n");
                sb.append("\n");
                sb.append("<p>Attributes with a <code class=\"changed_modified_2_2\">ignored-by-renderer</code> value of\n");
                sb.append("<code>true</code> are not interpreted by the renderer and are conveyed\n");
                sb.append("straight to the rendered markup, without checking for validity.  Attributes with a\n");
                sb.append("<code class=\"changed_modified_2_2\">ignored-by-renderer</code> value of <code>false</code> are interpreted\n");
                sb.append("by the renderer, and may or may not be checked for validity by the renderer.</p>\n");
                sb.append("\n");
                sb.append("<table BORDER=\"1\" CELLPADDING=\"3\" CELLSPACING=\"0\" WIDTH=\"100%\">\n");
                sb.append("<tr BGCOLOR=\"#CCCCFF\" CLASS=\"TableHeadingColor\">\n");
                sb.append("<td COLSPAN=\"5\"><font SIZE=\"+2\">\n");
                sb.append("<b>Attributes</b></font></td>\n");
                sb.append("</tr>\n");
                sb.append("<tr BGCOLOR=\"#CCCCFF\" CLASS=\"TableHeadingColor\">\n");
                sb.append("<th><b>attribute-name</b></th>\n");
                sb.append("<th><b class=\"changed_modified_2_2\">ignored-by-renderer</b></th>\n");
                sb.append("<th><b>attribute-class</b></th>\n");
                sb.append("<th><b>description</b></th>\n");
                sb.append("<th><b>default-value</b></th>\n");
                sb.append("</tr>\n");
                sb.append("	    \n");

                // Output each attribute
                if (attributes != null) {
                    for (int j = 0, attrLen = attributes.length; j < attrLen; j++) {
                        if (attributes[j].isAttributeIgnoredForRenderer()) {
                            continue;
                        }
                        sb.append("<tr BGCOLOR=\"white\" CLASS=\"TableRowColor\">\n");
                        sb.append("<td ALIGN=\"right\" VALIGN=\"top\" WIDTH=\"1%\"><code>\n");
                        sb.append("&nbsp;" + attributes[j].getAttributeName() + "\n");
                        sb.append("</td>\n");
                        sb.append("<td ALIGN=\"right\" VALIGN=\"top\">" + attributes[j].isPassThrough() + "</td>\n");
                        sb.append("<td><code>" + attributes[j].getAttributeClass() + "</code></td>\n");
                        descBean = attributes[j].getDescription("");
                        description = (null == descBean) ? "" : descBean.getDescription();
                        sb.append("<td>" + description + "</td>\n");
                        if (null == (defaultValue = attributes[j].getDefaultValue())) {
                            defaultValue = "undefined";
                        }
                        sb.append("<td>" + defaultValue + "<td>\n");
                        sb.append("</tr>\n");
                    }
                }
                sb.append("</table>\n");
            } else {
                sb.append("<p>This renderer-type has no attributes</p>\n");
            }
            sb.append("<hr>\n");
            sb.append("Copyright (c) 2003-2017 Oracle America, Inc. All Rights Reserved.\n");
            sb.append("</body>\n");
            sb.append("</html>\n");

            File file = new File(renderKitDirectory, componentFamily + rendererType + ".html");
            writeStringToFile(sb.toString(), file);
            filesTouched.add(file);

            sb.delete(0, sb.length());
        }
    }

    private static ClassLoader getCurrentLoader(Object fallbackClass) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = fallbackClass.getClass().getClassLoader();
        }

        return loader;
    }

    public static Map<String, ArrayList<RendererBean>> getComponentFamilyRendererMap(FacesConfigBean configBean, String renderKitId) {

        RenderKitBean renderKit = configBean.getRenderKit(renderKitId);
        if (renderKit == null) {
            throw new IllegalArgumentException("No RenderKit for id '" + renderKitId + '\'');
        }

        RendererBean[] renderers = renderKit.getRenderers();
        if (renderers == null) {
            throw new IllegalStateException("No Renderers for RenderKit id" + '"' + renderKitId + '"');
        }

        TreeMap<String, ArrayList<RendererBean>> result = new TreeMap<String, ArrayList<RendererBean>>();

        for (int i = 0, len = renderers.length; i < len; i++) {
            RendererBean renderer = renderers[i];

            if (renderer == null) {
                throw new IllegalStateException("no Renderer");
            }

            // If this is the first time we've encountered this
            // componentFamily
            String componentFamily = renderer.getComponentFamily();
            ArrayList<RendererBean> list = result.get(componentFamily);
            if (list == null) {
                // create a list for it
                list = new ArrayList<RendererBean>();
                list.add(renderer);
                result.put(componentFamily, list);
            } else {
                list.add(renderer);
            }
        }

        return result;
    }

    private static String getFirstDivFromString(String toParse, int[] out) {
        String result = null;

        if (toParse == null) {
            return result;
        }

        int divStart, divEnd;
        if (-1 != (divStart = toParse.indexOf("<div"))) {
            if (-1 != (divEnd = toParse.indexOf(">", divStart))) {
                result = toParse.substring(divStart, divEnd + 1);
            }
        }
        if (out != null && 0 < out.length) {
            out[0] = divStart;
        }

        return result;
    }

    private static String getFirstSpanFromString(String toParse, int[] out) {
        String result = null;

        if (null == toParse) {
            return result;
        }

        int spanStart, spanEnd;
        if (-1 != (spanStart = toParse.indexOf("<span"))) {
            if (-1 != (spanEnd = toParse.indexOf(">", spanStart))) {
                result = toParse.substring(spanStart, spanEnd + 1);
            }
        }
        if (null != out && 0 < out.length) {
            out[0] = spanStart;
        }

        return result;
    }


}
