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

package fbk.hlt.utility.archive;

import java.util.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;

import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.w3c.dom.*;


/**
 * Author: Christian Girardi (cgirardi@fbk.eu)
 * Date: 10-apr-2013
 */

public class CGTParser {
    static private String classLoader = "fbk.hlt.parsing.BaseParser";
    static private Map<String, Object> optionValues = null;

    static private Object webpageinterface;
    static private Method getBody;
    static private Method getHeader;

    static private Writer out = null;
    static private String xmlEncoding = "UTF-8";

    private static boolean keepC_on = false;


    private static void Usage (boolean showcmd) {
        if (showcmd)
            System.err.println("Usage: java -cp \"./lib/newsarchive1.0.jar:./lib/jdom.jar\" fbk.hlt.utility.archive.CGTParser [OPTION] <FILE.cgt>");

        System.err.println("\n" +
                " -h           show this help\n" +
                " -v           verbose\n" +
                " -l           show the list of URLs only\n" +
                " -la          show list of URLs with complete info\n"+
                " -p URL       print the content of the URL\n"+
                " -m           mirror all stored pages on filesystem (it forces the creation of the directories)\n" +
                " -mu URL      mirror the URL page on filesystem (it forces the creation of the directories)\n" +
                " -mf FILE     mirror stored pages from URLs of the FILE (a URL per line) on filesystem (it forces the creation of the directories)\n" +
                " -O DIR       write output file in the directory DIR\n" +
                " -a NAME      set the output filename to NAME (the defult is the input filename, with file extension .xml, is the default)\n" +
                " -e ENCODING  force to read the page with encoding ENCODING (i.e. -e \"ISO-8859-1\")\n" +
                " -I REGEXP    the parsing is applied only to the urls that match the regular expression REGEXP.\n              A list is accepted using &quot;|&quot; as delimiter (i.e. -s '.*.html$|.*news*.');\n" +
                " -X REGEXP    do not parse urls that match the regular expression REGEXP.\n              A list is accepted using &quot;|&quot; as delimiter (i.e. -s '.*.html$|.*news*.');\n" +
                " -cl CLASS    use a specify CLASS to parse the stored page. This class must have the methods of the\n              inferface class fbk.hlt.parsing.CGTDocumentInterface. The method signatures are:" +
                "\n                - public LinkedHashMap<String, String> getHeader (String url, String text, LinkedHashMap header)\n                - public String getBody (String url, String text, LinkedHashMap header)\n");
        System.err.flush();
        System.exit(0);
    }

    public static void main(String[] args) {
        args = getCommandLineOptions(args);

        if (args.length > 0) {
            Runtime.getRuntime().addShutdownHook(new RunWhenShuttingDown());

            Format format = Format.getPrettyFormat();
            format.setEncoding(xmlEncoding);
            XMLOutputter xml = new XMLOutputter(format);

            Class cl = null;

            if ((optionValues.get("PARSER_CLASS")) != null)
                classLoader = (String) optionValues.get("PARSER_CLASS");

            try {
                CatalogOfGzippedTexts cgt = new CatalogOfGzippedTexts(args[0],"r");
                if (cgt == null) {
                    System.err.println("# Failed loading " + args[0]);
                    System.exit(0);
                }
                System.err.println("# Loading " + cgt.getFilepath() + " ..." + cgt.size() + " found pages");
                //System.err.println("Loaded " + this.size() + " (unique " + setfile.size() + ") text items ("+filepath+").");

                if (optionValues.get("LISTOFURLS") == Boolean.TRUE ||
                        optionValues.get("LISTALLOFURLS") == Boolean.TRUE) {
                    int docnum=0;
                    String key = cgt.firstKey();
                    if (optionValues.get("LISTOFURLS") == Boolean.TRUE) {
                        while(key != null) {
                            docnum++;
                            System.out.println(key);
                            key = cgt.nextKey();
                        }

                    } else if (optionValues.get("LISTALLOFURLS") == Boolean.TRUE) {
                        Map head;
                        String s;
                        while(key != null) {
                            head = cgt.getHeader(key);
                            Set<String> set = head.keySet();

                            Iterator<String> itr = set.iterator();
                            while (itr.hasNext()) {
                                s = itr.next();
                                key += "#:"+s + "=" + head.get(s);
                            }


                            docnum++;
                            System.out.println(key.replaceAll("\r*\n"," "));
                            key = cgt.nextKey();

                        }
                    }
                    if (docnum!=cgt.size())
                        System.err.println("WARNING! Archive contains " + (cgt.size() -  docnum)+ " unreacheable documents.");
                } else if (optionValues.get("MIRROR") == Boolean.TRUE ||
                        optionValues.get("MIRROR_FILE") != null||
                        optionValues.get("MIRROR_URL") != null) {
                    //creo un mirror dei file sul file system (uno per ogni pagina html dell'archivio
                    String key;
                    int savedfiles = 0;
                    if (optionValues.get("MIRROR_URL") != null) {
                        key = (String) optionValues.get("MIRROR_URL");
                        if (saveURL(key.trim(), cgt) == 1) {
                            savedfiles++;
                            // if ((optionValues.get("VERBOSE")) == Boolean.TRUE)
                            //   System.err.println("# Writing...");
                        }
                    } else if (optionValues.get("MIRROR_FILE") != null) {
                        File mfile = new File((String) optionValues.get("MIRROR_FILE"));
                        if (mfile.exists()) {
                            BufferedReader inbuff = new BufferedReader(
                                    new InputStreamReader(new FileInputStream(mfile)));

                            String line;
                            while ((line = inbuff.readLine()) != null) {
                                String[] urls = line.split("\\s+");
                                for (int u=0; u<urls.length; u++) {
                                    if (urls[u].trim().length() > 0) {

                                        if (saveURL(urls[u].trim(), cgt) == 1) {
                                            savedfiles++;
                                        }

                                    }
                                }
                            }
                            inbuff.close();
                        } else {
                            System.err.println("# ERROR! The file " + optionValues.get("MIRROR_FILE") +" has not been found.");
                            return;
                        }


                    } else {
                        key = cgt.firstKey();

                        while(key != null) {
                            //System.err.println("# " +key);
                            try {
                                savedfiles += saveURL(key, cgt);
                                key = cgt.nextKey();

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                    }
                    System.err.println("# Saved "+ savedfiles + " URL files from "+ cgt.getFilepath() +".");
                } else if ((optionValues.get("PRINT")) != null) {
                    String url = (String) optionValues.get("PRINT");
                    if (optionValues.get("ENCODING") != null) {
                        System.out.println(cgt.getEncodedText(url,(String) optionValues.get("ENCODING")));
                    } else
                        System.out.println(cgt.getEncodedText(url));

                } else {
                    //to create the output xml file
                    String filename;

                    //set output filename
                    if (optionValues.get("OUTPUT_FILE") != null)
                        filename = (String) optionValues.get("OUTPUT_FILE");
                    else
                        filename = args[0].replaceFirst("\\.cgt$",".xml");


                    File outfile = new File (filename);

                    if (optionValues.get("OUTPUT_DIRECTORY") != null)
                        outfile = new File((String) optionValues.get("OUTPUT_DIRECTORY"), outfile.getName());


                    //WebPageInteface instruction
                    cl = Class.forName(classLoader);
                    webpageinterface = cl.newInstance();

                    getBody = cl.getMethod("getBody", new Class[] {String.class, String.class, LinkedHashMap.class} );
                    getHeader = cl.getMethod("getHeader", new Class[] {String.class, String.class, LinkedHashMap.class} );

                    int num = 0;
                    String key = null;
                    String header = "<?xml version=\"1.0\" encoding=\"" + xmlEncoding +"\"?>\n<xml archive=\""+ cgt.getFilepath()+"\">\n";

                    String fileline = null;
                    String lasturl = null;

                    if (outfile.exists() && outfile.isFile()) {
                        /* controllo che il file XML finisca per </xml> (altrimenti mantengo il puntatore all'ultimo
                           </file>) e tengo traccia dell'ultimo url
                        */
                        //ReverseFileReader rfr = new ReverseFileReader(outfile.getCanonicalPath(),"UTF8");
                        ReverseFileReader rfr = new ReverseFileReader(outfile.getCanonicalPath());

                        //System.err.println(raf.readLine());
                        long lastfile = 0;
                        while((key = rfr.readLine()) != null) {
                            //System.err.println("# " + key);
                            if (lastfile != 0 && key.matches("\\s*<file id=.*")) {
                                fileline = key;
                                break;
                            } else if (lastfile != 0 && key.matches("\\s*<url>.+</url>\\s*")) {
                                key = key.replaceFirst("^\\s*<url>","").replaceFirst("</url>\\s*","");
                                key = key.replaceAll("&amp;","&");
                                //key = new String(key.getBytes("UTF8"));

                                System.err.println("# LAST SAVED PAGE: " + key);
                                lasturl = key;
                            } else if (key.matches("\\s*</file>")) {
                                lastfile = rfr.getFilePointer() + key.length()+3;
                                //System.err.println("LAST SAVED PAGE: " +  key);

                                //--num;
                            } else if (key.matches("\\s*</xml>")) {
                                lastfile = rfr.getFilePointer() +2;
                            }
                        }

                        if (lastfile > header.length())
                            header = "";

                        rfr.setLength(lastfile);
                        rfr.close();

                        //estraggo l'ultimo numero di file
                        if (fileline != null) {
                            fileline = fileline.replaceFirst("\\s*<file id=\\\"","");
                            fileline = fileline.replaceFirst("\".*","");
                            if (fileline.matches("[0-9]+"))
                                num += Integer.parseInt(fileline);
                        }

                        if (optionValues.get("VERBOSE") == Boolean.TRUE)
                            System.err.println("# Updating file " + outfile.getName() +" (it already contains "+num+" pages) ...");

                    } else {
                        if (outfile.getParentFile() != null)
                            if (!outfile.getParentFile().exists() && !outfile.getParentFile().mkdirs()) {
                                System.err.println("ERROR! Unable to create directory " + outfile.getParent());
                                return;
                            }

                        if (!outfile.exists() && !outfile.createNewFile()) {
                            System.err.println("ERROR! The file " + outfile + " hasn't been created.");
                            return;
                        }
                        if (optionValues.get("VERBOSE") == Boolean.TRUE)
                            System.err.println("# " +outfile + " ...WRITING");

                    }

                    out = new BufferedWriter(new OutputStreamWriter
                            (new FileOutputStream(outfile,true),xmlEncoding));
                    out.write(header);

                    int saved = 0;
                    if (lasturl == null) {
                        key = cgt.firstKey();
                    } else {
                        key = cgt.nextKey(lasturl);
                    }

                    String[] regexpNOT = (String[]) optionValues.get("XREGEXP");
                    String[] regexpYES = (String[]) optionValues.get("IREGEXP");
                    boolean xmatched, imatched;
                    while(key != null) {
                        xmatched = false;
                        imatched = true;
                        if (regexpYES != null ) {
                            imatched = false;
                            for (int i=0; i<regexpYES.length; i++) {
                                if (regexpYES[i].length() > 0) {
                                    if (key.toLowerCase().matches(regexpYES[i])) {
                                        if (optionValues.get("VERBOSE") == Boolean.TRUE)
                                            System.out.print("\n> " + key +" ...MATCHED IREGEXP!");
                                        imatched = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (regexpNOT != null ) {
                            for (int i=0; i<regexpNOT.length; i++) {
                                if (regexpNOT[i].length() > 0) {
                                    if (key.toLowerCase().matches(regexpNOT[i])) {
                                        if (optionValues.get("VERBOSE") == Boolean.TRUE)
                                            System.out.print("\n> " + key +" ...MATCHED XREGEXP!");
                                        xmatched = true;
                                        break;
                                    }
                                }
                            }

                        }

                        //System.err.println("W:" +  key);
                        if (imatched && !xmatched) {
                            if (optionValues.get("VERBOSE") == Boolean.TRUE)
                                System.out.print("\n> " + key +" ...");

                            //creo l'elemento <file
                            //todo webpage = cgt.getDocument(key);
                            org.jdom.Element file = getFileElement(cgt,key,++num);

                            if (file != null) {
                                if (optionValues.get("VERBOSE") == Boolean.TRUE)
                                    System.out.print(" OK!");
                                //write to the outfile
                                try {
                                    out.write((xml.outputString(file)).replaceAll("\r+","")+"\n");
                                } catch (Exception e) {
                                    //no message
                                }

                                saved++;
                                //downurls.put(key,"0");
                            } else {
                                --num;
                            }
                        }

                        key = cgt.nextKey();

                    }
                    out.write("\n</xml>\n");
                    out.close();


                    //report
                    System.out.print("\n# The file " + outfile.getCanonicalPath());
                    if (fileline == null) {
                        System.out.println(" has been saved correctly (" +num+ " page/s).");
                    } else {
                        //System.err.println(" is increased with " + saved + " page/s (and "+wordnum+ " words).");
                        System.out.println(" contains " + num + " page/s (" + saved +" more).");
                    }
                }
                cgt.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                keepC_on = true;
            }
        } else {
            System.err.println("ERROR! The input archive file (*.cgt) has not been found.");
        }
    }

    private static int saveURL (String key, CatalogOfGzippedTexts cgt ) throws IOException {
        //System.err.println(":: " + key + " ..." +cgt.contains(key));
        if (!cgt.contains(key)) {
            return 0;
        }
        URI url = URI.create(key);

        String filepath = "";
        if (url.getPath().matches("^/*$")) {
            filepath = "_index.html";
        } else {
            filepath = url.getPath();

        }

        if (url.getQuery() != null) {
            filepath += "?"+url.getQuery();
            //filepath += "++" + url.getQuery().replaceAll("&","-") + ".htm";
        }
        if (filepath.length() > 230)
            filepath = url.getRawPath();

        File dirfile, outfile;
        if (url.getHost() != null) {
            outfile = new File(url.getHost(),filepath);
        } else {
            outfile = new File(filepath);
        }
//set output directory
        if (optionValues.get("OUTPUT_DIRECTORY") != null) {
            if (outfile.getParent() == null)
                dirfile = new File((String) optionValues.get("OUTPUT_DIRECTORY"));
            else
                dirfile = new File((String) optionValues.get("OUTPUT_DIRECTORY"),outfile.getParent());


        } else {
            if (outfile.getParent() == null) {
                dirfile = new File(System.getProperty("user.dir"));
            } else {
                dirfile = new File(outfile.getParent());
            }
        }

        if (!dirfile.exists()) {
            if (!dirfile.mkdirs()) {
                System.err.println("ERROR! Unable to create directory " + dirfile.getCanonicalPath());
                return 0;
            }
        }

        outfile = new File(dirfile, outfile.getName());
        if (optionValues.get("VERBOSE") == Boolean.TRUE)
            System.out.print("# " + outfile.getPath() +" ...");
        if (outfile.exists()) {
            if (optionValues.get("VERBOSE") == Boolean.TRUE)
                System.err.print(" ALREADY EXISTS\n");

            return 0;
        }



        String contentType = cgt.getHeader(key, "content-type");
        if (contentType == null || contentType.equals("") ||
                contentType.equals("null") || contentType.contains("text/")) {

            String encoding = (String) optionValues.get("ENCODING");
            if (encoding == null) {
                encoding= cgt.getEncoding(key);
            }

            if (encoding == null || encoding.equals("")) {
                OutputStream out = new FileOutputStream(outfile);

                out.write(cgt.getBytes(key));
                out.close();

            } else {
                out = new OutputStreamWriter(new FileOutputStream(outfile), "UTF8");
                out.write(cgt.getEncodedText(key,encoding));
                out.close();
            }

        } else {
            //e' un file tipo media (image, video, audio, ...) quindi lo scrivo così com'è (senza info di header!)
            OutputStream out = new FileOutputStream(outfile);
            out.write(cgt.getBytes(key));
            out.close();

        }
        if (optionValues.get("VERBOSE") == Boolean.TRUE)
            System.err.print(" OK!\n");

        return 1;
    }


    private static Element getFileElement (CatalogOfGzippedTexts cgt, String key, int num) {
//HEAD
        Element head = new org.jdom.Element("head");
        Element content = new org.jdom.Element("content");

        try {
            byte[] binaryText = cgt.getBytes(key);
            if (binaryText == null || binaryText.length < 2) {
                if (optionValues.get("VERBOSE") == Boolean.TRUE)
                    System.out.print(" ERROR!");
                return null;
            }

            LinkedHashMap header = cgt.getHeader(key);
            String content_type = (String) header.get("content-type");
            String sb2;

            String encoding = (String) optionValues.get("ENCODING");
            if (encoding == null) {
                encoding= cgt.getEncoding(key);
            }

            //URL
            org.jdom.Element url = new org.jdom.Element("url");
            url.setText(key);
            head.addContent(url);

            String text = null;
            org.jdom.Element el = null;

            if (content_type != null && !content_type.contains("text")) {
                if ((optionValues.get("VERBOSE")) == Boolean.TRUE) {
                    System.out.print(" WARNING! This isn't a text file (content-type: " + content_type + ").");
                }
            } else {
                //get encoding
                try {
                    if (encoding != null && !encoding.equals("")) {
                        sb2 = new String(binaryText, encoding);
                    } else {
                        sb2 = new String(binaryText);
                    }
                } catch (UnsupportedEncodingException e) {
                    sb2 = new String(binaryText);
                }

                //GET OTHER HEADER INFO
                String field;
                String value;

                LinkedHashMap userheader = (LinkedHashMap) getHeader.invoke(webpageinterface,key,sb2,header);

                if (userheader != null && userheader.size() > 0) {
                    header.putAll(userheader);
                }


                if (encoding != null && !encoding.equals("")) {
                    header.put("encoding",encoding);
                }
                //System.err.println("ENCODING " + encoding);
                if (header.size() > 0) {
                    Hashtable<String,Element> xmlElsname = new Hashtable<String,Element>();
                    Iterator entryIter = header.entrySet().iterator();

                    while(entryIter.hasNext()) {
                        field = ((Map.Entry) entryIter.next()).getKey().toString();
                        value = (String) header.get(field);

                        // create xml element
                        if (value != null) {
                            try {
                                Element parent = head;
                                if (field.contains("/")) {
                                    String[] els = field.split("/");
                                    for (int i=0; i<els.length; i++) {
                                        if (xmlElsname.containsKey(els[i])) {
                                            el = xmlElsname.get(els[i]);
                                            parent = el;
                                        } else {
                                            el = new org.jdom.Element(els[i].replaceFirst(":.*",""));
                                            xmlElsname.put(els[i], el);

                                            if (i<els.length-1) {
                                                parent.addContent(el);
                                                parent = el;
                                            }
                                        }

                                    }
                                } else {
                                    el = new org.jdom.Element(field);
                                }
                                if (el != null) {
                                    if (field.contains(":")) {
                                        String[] attrs = field.replaceFirst("[^:]+:","").split(":");
                                        for (String attr : attrs) {
                                            el.setAttribute(attr.replaceFirst("=.*",""), attr.replaceFirst(".*=",""));
                                        }
                                    }

                                    byte[] byteval = value.getBytes(); //"Latin1"
                                    value = new String(byteval,"UTF8");

                                    el.setText(value.replaceAll("\r*\n"," "));
                                    parent.addContent(el);
                                }

                            } catch (Exception e) {
                                //e.printStackTrace();
                                if ((optionValues.get("VERBOSE2")) == Boolean.TRUE)
                                    System.err.print(" WARNING! Found illegal XML characters (" + field +") ...");
                            }
                        }
                    }

                }


                //get content by the custom class 
                text=(String) getBody.invoke(webpageinterface, key, sb2, header);

            }


            //System.err.println(sb2.length() + " TEXT " + text);
            if (text == null) {
                if (optionValues.get("VERBOSE") == Boolean.TRUE)
                    System.out.print(" WARNING! No relevant text found.");

                return null;
            }

            if (optionValues.get("VERBOSE") == Boolean.TRUE)
                System.out.print("[" + text.length() + " chars] ");

            if (optionValues.get("MIN_CONTENT_CHARS") != null && text.trim().length() < ((Integer) optionValues.get("MIN_CONTENT_CHARS")).intValue()) {
                if ((optionValues.get("VERBOSE")) == Boolean.TRUE)
                    System.out.print(" Content too short ("+text.length()+" bytes). ");
                return null;
            }

            content.addContent(text);

            if (!header.containsKey("charnum")) {
                org.jdom.Element size = new org.jdom.Element("charnum");
                size.setText(String.valueOf(text.length()));
                head.addContent(size);
            }
        } catch (IllegalDataException e ) {
            if ((optionValues.get("VERBOSE")) == Boolean.TRUE)
                System.out.print("ERROR!");
            System.err.println("\n# ERROR! Found illegal XML characters in " + key + " ("+e.getMessage() +") "+e.getStackTrace());
            return null;
        } catch (IllegalAccessException e) {
            if ((optionValues.get("VERBOSE")) == Boolean.TRUE)
                System.out.print("ERROR!");
            System.err.println("\n# ERROR! "+ e.getMessage() + " in " +key+" ("+e.getCause()+")");
            //e.printStackTrace();
            return null;
        } catch (InvocationTargetException e) {
            if ((optionValues.get("VERBOSE")) == Boolean.TRUE)
                System.out.print("ERROR!");
            System.err.println("\n# ERROR! "+ e.getMessage() + " in " +key+" ("+e.getCause()+")");
            //e.printStackTrace();
            return null;
        }

        org.jdom.Element file = new org.jdom.Element("file");
        file.setAttribute("id", String.valueOf(num));
        file.addContent(head);
        file.addContent(content);

        return file;
    }


    /**
     * Initialize for JavaCC
     */
    public static String[] getCommandLineOptions(String[] args) {
        if (args.length <= 0) {
            Usage(true);
        } else {
            optionValues = new HashMap<String, Object>();

            optionValues.put("VERBOSE", Boolean.FALSE);
            optionValues.put("VERBOSE2", Boolean.FALSE);
            optionValues.put("LISTOFURLS", Boolean.FALSE);
            optionValues.put("LISTALLOFURLS", Boolean.FALSE);
            optionValues.put("MIRROR", Boolean.FALSE);
            optionValues.put("MIRROR_URL", null);
            optionValues.put("MIRROR_FILE", null);
            optionValues.put("XREGEXP", null);
            optionValues.put("IREGEXP", null);
            optionValues.put("PARSER_CLASS", null);
            optionValues.put("OUTPUT_DIRECTORY", null);
            optionValues.put("OUTPUT_FILE", null);
            //optionValues.put("SOURCE_LANGUAGE",null);
            optionValues.put("ENCODING",null);
            optionValues.put("MIN_CONTENT_CHARS", null);
            optionValues.put("PRINT", null);

            Vector arg = new Vector();
            try {
                for (int i=0; i<args.length; i++) {
                    if (isOption(args[i])) {
                        if (args[i].equals("-h"))
                            Usage(true);
                        if (args[i].equals("-ho"))
                            Usage(false);
                        else if (args[i].equals("-v"))
                            optionValues.put("VERBOSE",Boolean.TRUE);
                        else if (args[i].equals("-v2")) {
                            optionValues.put("VERBOSE",Boolean.TRUE);
                            optionValues.put("VERBOSE2",Boolean.TRUE);
                        } else if (args[i].equals("-l"))
                            optionValues.put("LISTOFURLS",Boolean.TRUE);
                        else if (args[i].equals("-la"))
                            optionValues.put("LISTALLOFURLS",Boolean.TRUE);
                        else if (args[i].equals("-m"))
                            optionValues.put("MIRROR",Boolean.TRUE);
                        else if (args[i].equals("-mu"))
                            optionValues.put("MIRROR_URL", args[++i]);
                        else if (args[i].equals("-mf"))
                            optionValues.put("MIRROR_FILE", args[++i]);
                        else if (args[i].equals("-X")) {
                            String[] regexp = args[++i].toLowerCase().split("\\|");
                            optionValues.put("XREGEXP",regexp);
                        } else if (args[i].equals("-I")) {
                            String[] regexp = args[++i].toLowerCase().split("\\|");
                            optionValues.put("IREGEXP",regexp);
                        } else if (args[i].equals("-cl"))
                            optionValues.put("PARSER_CLASS",args[++i]);
                        else if (args[i].equals("-O"))
                            optionValues.put("OUTPUT_DIRECTORY",args[++i]);
                        else if (args[i].equals("-a"))
                            optionValues.put("OUTPUT_FILE",args[++i]);
                        else if (args[i].equals("-c"))
                            optionValues.put("MIN_CONTENT_CHARS",new Integer(args[++i]));
                        else if (args[i].equals("-p"))
                            optionValues.put("PRINT", args[++i]);
                        else if (args[i].equals("-e")) {
                            String myencname = args[++i].trim();

                            SortedMap charsets = Charset.availableCharsets();
                            Set names = charsets.keySet();
                            String name;
                            ArrayList encs = new ArrayList();
                            for (Iterator e = names.iterator(); e.hasNext();) {
                                name = (String) e.next();
                                if (!encs.contains(name))
                                    encs.add(name);
                                if (myencname.equalsIgnoreCase(name) || myencname.equalsIgnoreCase(name.replaceAll("-",""))) {
                                    optionValues.put("ENCODING", name);
                                    break;
                                }
                            }

                            if (optionValues.get("ENCODING") == null) {
                                System.err.println("ERROR! Unrecognized encoding. Please, use one of these labels:\n" + encs);
                                System.exit(0);
                            }
                        }
                        else {
                            System.err.println(" illegal option: " + args[i]);
                            Usage(false);
                        }

                    } else {
                        arg.add(args[i]);
//System.err.println(args[i]);
                    }

                }
                args = (String[]) arg.toArray(new String[0]);
            } catch (Exception e) {
                System.err.println("illegal option: (" + e.getMessage() + ")\nusage:");
                Usage(false);
            }
        }

        return args;
    }

    /**
     * Determine if a given command line argument might be an option flag.
     * Command line options start with a dash (-).
     *
     * @param opt
     *            The command line argument to examine.
     * @return True when the argument looks like an option flag.
     */
    private static boolean isOption(final String opt) {
        return opt != null && opt.length() > 1 && opt.charAt(0) == '-';
    }


//~ Inner Classes -----------------------------------------------------------------------------
    public static class RunWhenShuttingDown extends Thread {
        public void run() {
            System.out.flush();
            System.err.flush();
            if (!keepC_on) {
                try {
                    System.err.println("\n\n## Ctrl-C caught by user! Shutting down...\n");

                    Thread.sleep(1000);
                    if (out != null) {
                        if (out instanceof BufferedWriter)
                            out.write("\n</xml>\n");
                        out.flush();
                        out.close();
                        Thread.sleep(1000);
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
