/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main.java.org.fbk.hlt.newsreader;

import fbk.hlt.utility.archive.CatalogOfGzippedTexts;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Author: Christian Girardi (cgirardi@fbk.eu)
 * Date: 27-mag-2013
 */

public class ZipManager {
    private DefaultHandler handler;
    private SAXParser saxParser;
    private String newsUrl = "";
    private String zipPath;
    static private CatalogOfGzippedTexts cgtarchive;
    
    public int getCGTSize () {
        if (cgtarchive != null)
            return cgtarchive.size();
        return 0;
    }


    public ZipManager(String zipPath) {
        this.zipPath = zipPath;

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);


        try {
            //factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            saxParser = factory.newSAXParser();

            handler = new DefaultHandler() {

                boolean relevantBlock = false;
                boolean relevantElement = false;
                StringBuilder body = new StringBuilder();
                LinkedHashMap header = new LinkedHashMap();
                private int counter = 0;

                public void startElement(String uri, String localName,String qName,
                                         Attributes attributes) throws SAXException {
                    if (qName.equalsIgnoreCase("block") || qName.equalsIgnoreCase("hedline")) {
                        relevantBlock = true;
                    } else if (qName.equalsIgnoreCase("p") || qName.equalsIgnoreCase("hl1")) {
                        relevantElement = true;
                    } else if (qName.equalsIgnoreCase("date.issue")) {
                        String date = attributes.getValue("norm");
                        if (date != null)
                            date = date.substring(0,8);
                        header.put("date", date);
                    }

                }

                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if (qName.equalsIgnoreCase("block")) {
                        relevantBlock = false;
                        body.append("\n");
                    } else if (qName.equalsIgnoreCase("hedline")) {
                        relevantBlock = false;
                        body.append("\n\n");
                    } else if (qName.equalsIgnoreCase("p") || qName.equalsIgnoreCase("hl1")) {
                        relevantElement = false;
                        body.append("\n");
                        if (qName.equalsIgnoreCase("hl1")) {
                            header.put("title", body.toString());
                        }
                    } else if (qName.equalsIgnoreCase("br")) {
                        body.append("\n");
                    } else if (qName.equalsIgnoreCase("body")) {
                        if (body.length() > 0) {
                            System.err.print("> " + newsUrl);
                            header.put("encoding", "UTF8");

                            // store the metadata as Map and the body into the cgt archive
                            if (ZipManager.storeNews(newsUrl, body.toString(), header)) {
                                counter++;
                                System.err.println(" ...STORED ["+ counter +"]");
                            } else {
                                System.err.println(" ...SKIPPED!");
                            }
                            body.setLength(0);
                            header.clear();
                        }

                    }
                }

                public void characters(char ch[], int start, int length) throws SAXException {
                    if (relevantBlock && relevantElement) {
                        //append the interested text to the content
                        body.append(new String(ch, start, length));
                    }

                }

            };

        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }


    }

    private static boolean storeNews (String url, String news, Map header) {
        if (cgtarchive != null && !cgtarchive.contains(url)) {
            return cgtarchive.add(url, news.getBytes(), header);
        }
        return false;
    }

    public void zip2cgt (String cgtPath) throws IOException {
        try {
            File archiveFile = new File(cgtPath);
            if (!archiveFile.getParentFile().exists())
                archiveFile.getParentFile().mkdirs();
            cgtarchive = new CatalogOfGzippedTexts(cgtPath, "rw");
        } catch (Exception e) {
            e.printStackTrace();
        }

        ZipFile zf = new ZipFile(zipPath);
        try {
            for (Enumeration<? extends ZipEntry> e = zf.entries();
                 e.hasMoreElements();) {
                ZipEntry ze = e.nextElement();
                newsUrl = ze.getName();
                if (newsUrl.endsWith(".xml")) {
                    try {
                        saxParser.parse(zf.getInputStream(ze), handler);

                    } catch (SAXException e2) {
                        System.err.println("ERROR on " + newsUrl + " " +e2.getMessage());
                    }
                }
            }

        } finally {
            zf.close();
        }
    }



    public static void main (String args[]) throws Exception {
        ZipManager zip = new ZipManager(args[0]);
        zip.zip2cgt(args[1]);

        System.err.println("The archive " + args[1] +" contains " +zip.getCGTSize() + " documents.");

    }

}


